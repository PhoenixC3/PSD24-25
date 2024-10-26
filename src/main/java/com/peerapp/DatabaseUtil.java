package com.peerapp;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
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
    private static final int MIN_PORT = 1024;
    private static final int MAX_PORT = 65535;

    public int authenticateUser(String username, String password) {
        SSLSocket socket = null;
        ObjectOutputStream oos = null;
        ObjectInputStream ois = null;

        try {
            socket = createClientSocket(username, password);

            if (socket == null) {
                return -1;
            }

            oos = new ObjectOutputStream(socket.getOutputStream());

            oos.writeObject("LOGIN");
            oos.flush();
            oos.writeObject(username);
            oos.flush();
            oos.writeObject(password);
            oos.flush();

            ois = new ObjectInputStream(socket.getInputStream());
            String res = (String) ois.readObject();

            if(res.equals("OK")) {
                int port = (int) ois.readObject();
                return port;
            }
            else if(res.equals("WRONG")) {
                return -1;
            }
            else 
            {
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

    public String registerUser(String username, String password) {
        byte[] salt = EncryptionUtil.generateSalt();
        X509Certificate myCert = createKeystoreAndTruststore(username, password);
        SSLSocket socket = null;
        ObjectOutputStream oos = null;
        ObjectInputStream ois = null;

        try {
            String hashedPassword = EncryptionUtil.hashPassword(password, salt);
            int port = findAvailableRandomPort();
            byte[] certEnc = myCert.getEncoded();

            socket = createClientSocket(username, password);

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

    /* Create an SSLSocket for a client if the username and password is correct */
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

    /* Returns a random available port between MIN_PORT and MAX_PORT */
    private int findAvailableRandomPort() {
        Random random = new Random();
        int port;

        while (true) {
            port = random.nextInt((MAX_PORT - MIN_PORT) + 1) + MIN_PORT;

            try (ServerSocket serverSocket = new ServerSocket(port)) {
                break;
            } catch (BindException e) {
                // Port is already in use, try the next random port
                findAvailableRandomPort();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return port;
    }

    /* Creates a keystore and truststore for a client using it's username and password */
    private X509Certificate createKeystoreAndTruststore(String username, String password) {
        try {
            //Create keypair
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
            keyGen.initialize(2048);
            KeyPair keyPair = keyGen.generateKeyPair();
            
            //Create keystore
            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(null, null);
    
            X509Certificate cert = generateSelfSignedCertificate(keyPair);
    
            keyStore.setKeyEntry(username, keyPair.getPrivate(), password.toCharArray(), new Certificate[]{cert});
    
            try (FileOutputStream fos = new FileOutputStream("keystores/" + username + "_keystore.jks")) {
                keyStore.store(fos, password.toCharArray());
            }

            //Create truststore
            KeyStore trustStore = KeyStore.getInstance("JKS");
            trustStore.load(null, null);

            X509Certificate server_cert = loadCertificate("certs/server_cert.cer");

            // Store server certificate
            trustStore.setCertificateEntry("keyServer", server_cert);

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

    private static X509Certificate loadCertificate(String certFilePath) throws Exception {
        try (FileInputStream certInput = new FileInputStream(certFilePath)) {
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            return (X509Certificate) certFactory.generateCertificate(certInput);
        } catch (CertificateException e) {
            throw new Exception("Failed to load certificate: " + e.getMessage(), e);
        }
    }

    private static X509Certificate generateSelfSignedCertificate(KeyPair keyPair) throws Exception {
        Security.addProvider(new BouncyCastleProvider());

        // Certificate details
        X500Name issuer = new X500Name("CN=CA");
        X500Name subject = new X500Name("CN=Test User");
        BigInteger serial = BigInteger.valueOf(System.currentTimeMillis());
        Date notBefore = new Date(System.currentTimeMillis() - 1000L * 60 * 60 * 24);
        Date notAfter = new Date(System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 365);

        // Create X509v3CertificateBuilder
        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                issuer, serial, notBefore, notAfter, subject, keyPair.getPublic());

        // Sign certificate with private key
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());

        // Generate X509Certificate
        X509Certificate certificate = new JcaX509CertificateConverter()
                .setProvider("BC")
                .getCertificate(certBuilder.build(signer));

        return certificate;
    }
}
