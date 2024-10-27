package com.peerapp;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigInteger;
import java.net.BindException;
import java.net.ServerSocket;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Random;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

public class DatabaseUtil {
    //Port range to choose from
    private static final int MIN_PORT = 1024;
    private static final int MAX_PORT = 65535;

    //Login in the app
    public int authenticateUser(String username, String password) {
        SSLSocket socket = null;
        ObjectOutputStream oos = null;
        ObjectInputStream ois = null;

        try {
            //Create socket to communicate with databse server
            socket = createClientSocket(username, password);

            if (socket == null) {
                return -1;
            }

            //Create the communication stream
            oos = new ObjectOutputStream(socket.getOutputStream());

            oos.writeObject("LOGIN");
            oos.flush();

            oos.writeObject(username);
            oos.flush();
            
            //Hashed password
            oos.writeObject(password);
            oos.flush();

            //Password verification is server side, using saved parameters in the database
            ois = new ObjectInputStream(socket.getInputStream());
            String res = (String) ois.readObject();

            if(res.equals("OK")) {
                //Correct user
                int port = (int) ois.readObject();
                return port;
            }
            else if(res.equals("WRONG")) {
                //Wrong password or non-existent user
                return -1;
            }
            else 
            {
                //Error
                return -2;
            }   
        } catch (Exception e) {
            e.printStackTrace();
            return -3;
        } finally {
            try {
                if (oos != null) oos.close();
                if (ois != null) ois.close();
                if (socket != null) socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    //User registration
    public String registerUser(String username, String password) {
        //Create salt to hash password
        byte[] salt = EncryptionUtil.generateSalt();

        //Create the keystores and truststores for SSL/TLS (and store the server certificate as trusted by default)
        X509Certificate myCert = createKeystoreAndTruststore(username, password);

        SSLSocket socket = null;
        ObjectOutputStream oos = null;
        ObjectInputStream ois = null;

        try {
            //Hash password
            String hashedPassword = EncryptionUtil.hashPassword(password, salt);

            //Choose a random port within range
            int port = findAvailableRandomPort();

            //Encode user certificate to be saved as BLOB in database
            byte[] certEnc = myCert.getEncoded();

            //Create socket to communicate with server
            socket = createClientSocket(username, password);

            //Send user parameters to be saved in the database
            oos = new ObjectOutputStream(socket.getOutputStream());
            ois = new ObjectInputStream(socket.getInputStream());

            oos.writeObject("REGISTER");
            oos.flush();

            oos.writeObject(username);
            oos.flush();

            oos.writeObject(hashedPassword);
            oos.flush();

            oos.writeObject(salt);
            oos.flush();

            oos.writeObject("127.0.0.1");
            oos.flush();

            oos.writeObject(port);
            oos.flush();

            oos.writeObject(certEnc);
            oos.flush();

            String res = (String) ois.readObject();

            return res;
        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR";
        } finally {
            try {
                if (oos != null) oos.close();
                if (ois != null) ois.close();
                if (socket != null) socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    //Create a scoket to communicate with the server (fixed port)
    private SSLSocket createClientSocket(String username, String password) throws Exception {
        KeyStore trustStore = KeyStore.getInstance("JKS");
        try (FileInputStream trustStoreInput = new FileInputStream("truststores/" + username + "_truststore.jks")) {
            trustStore.load(trustStoreInput, password.toCharArray());
        } catch (Exception e) {
            return null;
        }

        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustManagerFactory.getTrustManagers(), null);

        SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
    
        return (SSLSocket) sslSocketFactory.createSocket("127.0.0.1", 8080);
    }

    //Choose a random available port in the network
    private int findAvailableRandomPort() {
        Random random = new Random();
        int port;

        while (true) {
            port = random.nextInt((MAX_PORT - MIN_PORT) + 1) + MIN_PORT;

            try (ServerSocket serverSocket = new ServerSocket(port)) {
                break;
            } catch (BindException e) {
                // Port is already in use, try another
                return findAvailableRandomPort();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return port;
    }

    //Create keystore and truststore for user and get their certificate (public key)
    private X509Certificate createKeystoreAndTruststore(String username, String password) {
        try {
            //Create private/public keys for user
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            KeyPair keyPair = keyGen.generateKeyPair();
            
            //Create user keystore
            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(null, null);
            
            //Generate certificate from the user's public key
            X509Certificate cert = generateSelfSignedCertificate(keyPair);
            
            //Add user private key to keystore
            keyStore.setKeyEntry(username, keyPair.getPrivate(), password.toCharArray(), new Certificate[]{cert});
            
            //Save the keystore file in the folder
            try (FileOutputStream fos = new FileOutputStream("keystores/" + username + "_keystore.jks")) {
                keyStore.store(fos, password.toCharArray());
            }

            //Create user truststore
            KeyStore trustStore = KeyStore.getInstance("JKS");
            trustStore.load(null, null);

            //Get the server certificate
            X509Certificate server_cert = loadCertificate("certs/server_cert.cer");

            //Store the server's certificate as trusted by default in the user's truststore
            trustStore.setCertificateEntry("keyServer", server_cert);

            //Save the truststore file in the folder
            try (FileOutputStream fos = new FileOutputStream("truststores/" + username + "_truststore.jks")) {
                trustStore.store(fos, password.toCharArray());
            }

            System.out.println("Keystore and truststore created");
            return cert;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    //Load a certificate from a file
    private static X509Certificate loadCertificate(String certFilePath) throws Exception {
        try (FileInputStream certInput = new FileInputStream(certFilePath)) {
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            return (X509Certificate) certFactory.generateCertificate(certInput);
        } catch (CertificateException e) {
            throw new Exception("Failed to load certificate: " + e.getMessage(), e);
        }
    }

    //Create a certificate for a user from their keypair
    private static X509Certificate generateSelfSignedCertificate(KeyPair keyPair) throws Exception {
        //Using BouncyCastle because it is easier
        Security.addProvider(new BouncyCastleProvider());

        //Certificate details (mostly just placeholders to make it work)
        X500Name issuer = new X500Name("CN=CA");
        X500Name subject = new X500Name("CN=Test User");
        BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
        Date notBefore = new Date(System.currentTimeMillis() - 1000L * 60 * 60 * 24);
        Date notAfter = new Date(System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 365);

        //Use BouncyCastle's certificate builder to create the certificate from public key
        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                issuer, serial, notBefore, notAfter, subject, keyPair.getPublic());

        //Sign the certificate with the user's private key
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());

        //Generate the certificate
        X509Certificate certificate = new JcaX509CertificateConverter()
                .setProvider("BC")
                .getCertificate(certBuilder.build(signer));

        return certificate;
    }
}
