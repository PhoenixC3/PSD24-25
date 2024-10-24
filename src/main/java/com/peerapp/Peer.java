package com.peerapp;

import javax.net.ssl.*;

import javafx.application.Platform;

import java.io.*;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.crypto.SecretKey;

public class Peer {
    private String userId;
    String password;
    
    private SSLServerSocket serverSocket;
    private final int PORT;
    private PeerController peerController;

    public Peer(String userId, String password, int port, PeerController peerController) throws Exception {
        this.userId = userId;
        this.password = password;
        this.PORT = port;
        this.peerController = peerController;
        this.serverSocket = createServerSocket();
        new Thread(this::listenForMessages).start();
    }

    public String getUserId() {
        return userId;
    }

    private SSLServerSocket createServerSocket() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("JKS");
        try (FileInputStream keyStoreInput = new FileInputStream("keystores/" + userId + "_keystore.jks")) {
            keyStore.load(keyStoreInput, password.toCharArray());
        }

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, password.toCharArray());

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagerFactory.getKeyManagers(), null, null);

        SSLServerSocketFactory sslServerSocketFactory = sslContext.getServerSocketFactory();
        return (SSLServerSocket) sslServerSocketFactory.createServerSocket(PORT);
    }

    private void listenForMessages() {
        while (true) {
            try {
                SSLSocket socket = (SSLSocket) serverSocket.accept();
                new Thread(new MessageHandler(socket, peerController)).start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void sendMessage(String recipient, String content) {
        SSLSocket socket = null;
        ObjectOutputStream oos = null;

        try {
            // Send the message using SSL socket
            socket = createClientSocket(recipient);

            // Generate secret key and encrypt with recipient's public key
            SecretKey key = EncryptionUtil.generateSecretKey();
            PublicKey pubKey = EncryptionUtil.getPublicKeyFromTrustStore("truststores/" + userId + "_truststore.jks", password, recipient);
            byte[] encryptedKey = EncryptionUtil.encryptAESKey(key, pubKey);
    
            // Encrypt the message content
            String encryptedContent = EncryptionUtil.encrypt(content, key);
    
            // Sign the message
            PrivateKey privKey = EncryptionUtil.getPrivateKeyFromKeystore("keystores/" + userId + "_keystore.jks", password, userId, password);
            String signedMessage = EncryptionUtil.signMessage(encryptedContent, privKey);
    
            // Create message object
            Message message = new Message(userId, recipient, encryptedKey, encryptedContent, signedMessage);
            oos = new ObjectOutputStream(socket.getOutputStream());
            oos.writeObject(message);
            oos.flush();
    
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (oos != null) oos.close();
                if (socket != null) socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    

    private SSLSocket createClientSocket(String recipient) throws Exception {
        SSLSocket svSock = null;
        ObjectOutputStream oos = null;
        ObjectInputStream in = null;
        String ip = null;
        int port = -1;
        byte[] cert = null;
    
        try {
            svSock = contactServer();

            oos = new ObjectOutputStream(svSock.getOutputStream());
            in = new ObjectInputStream(svSock.getInputStream());

            oos.writeObject("GETPEER");
            oos.flush();
            oos.writeObject(recipient);
            oos.flush();

            ip = (String) in.readObject();
            port = (int) in.readObject();
            cert = (byte[]) in.readObject();

            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            ByteArrayInputStream certInputStream = new ByteArrayInputStream(cert);
            X509Certificate certificate = (X509Certificate) certFactory.generateCertificate(certInputStream);

            KeyStore trustStore = KeyStore.getInstance("JKS");

            try (FileInputStream trustStoreInput = new FileInputStream("truststores/" + userId + "_truststore.jks")) {
                trustStore.load(trustStoreInput, password.toCharArray());
            }

            trustStore.setCertificateEntry(recipient, certificate);

            try (FileOutputStream fos = new FileOutputStream("truststores/" + userId + "_truststore.jks")) {
                trustStore.store(fos, password.toCharArray());
            }
    
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);
    
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
    
            SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            return (SSLSocket) sslSocketFactory.createSocket(ip, port);

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (oos != null) oos.close();
                if (svSock != null) svSock.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return null;
    }

    private SSLSocket contactServer() throws Exception {
        KeyStore trustStore = KeyStore.getInstance("JKS");
        try (FileInputStream trustStoreInput = new FileInputStream("truststores/" + userId + "_truststore.jks")) {
            trustStore.load(trustStoreInput, password.toCharArray());
        }

        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustManagerFactory.getTrustManagers(), null);

        SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
    
        return (SSLSocket) sslSocketFactory.createSocket("127.0.0.1", 8080);
    }
    
    public String decryptMessage(Message message) {
        try {
            // Step 1: Get the sender's public key from the truststore
            PublicKey pubKey = EncryptionUtil.getPublicKeyFromTrustStore("truststores/" + userId + "_truststore.jks", password, message.getSender());

            // Step 2: Verify the signature of the encrypted content
            if (!EncryptionUtil.verifySignature(message.getEncryptedContent(), message.getSignedMessage(), pubKey)) {
                // Signature verification failed
                System.out.println("Signature verification failed for message from " + message.getSender());
                return "Invalid message signature.";
            }

            // Step 3: Decrypt the AES key using this peer's private key
            PrivateKey privKey = EncryptionUtil.getPrivateKeyFromKeystore("keystores/" + userId + "_keystore.jks", password, userId, password);
            SecretKey secretKey = EncryptionUtil.decryptAESKey(message.getEncKey(), privKey);

            // Step 4: Decrypt the content using the decrypted AES key
            String decryptedContent = EncryptionUtil.decrypt(message.getEncryptedContent(), secretKey);

            // Return the decrypted content
            return decryptedContent;

        } catch (Exception e) {
            e.printStackTrace();
            return "Error decrypting message: " + e.getMessage();
        }
    }

    
    public void getPeerInfo(String username) {
        // Run the query in a separate thread to avoid blocking the UI
        new Thread(() -> {
            SSLSocket socket = null;
            ObjectOutputStream oos = null;
            ObjectInputStream ois = null;

            try {
                // Connect to the SSL server
                socket = contactServer();
                
                // Setup input/output streams
                oos = new ObjectOutputStream(socket.getOutputStream());
                ois = new ObjectInputStream(socket.getInputStream());

                // Send query command and username
                oos.writeObject("QUERYUSER");
                oos.flush();
                oos.writeObject(username);
                oos.flush();

                // Read response from server
                String status = (String) ois.readObject();
                
                if ("FOUND".equals(status)) {
                    // Read peer information
                    String peerId = (String) ois.readObject();
                    String ip = (String) ois.readObject();
                    int port = (int) ois.readObject();
                    byte[] cert = (byte[]) ois.readObject();

                    // Store the certificate in truststore
                    storeCertificate(peerId, cert);

                    // Update the UI on JavaFX Application Thread
                    Platform.runLater(() -> {
                        peerController.updatePeerList(peerId);
                    });
                }

            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    peerController.appendError("Error querying peer information: " + e.getMessage());
                });
            } finally {
                try {
                    if (ois != null) ois.close();
                    if (oos != null) oos.close();
                    if (socket != null) socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private void storeCertificate(String peerId, byte[] cert) throws Exception {
        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        ByteArrayInputStream certInputStream = new ByteArrayInputStream(cert);
        X509Certificate certificate = (X509Certificate) certFactory.generateCertificate(certInputStream);

        KeyStore trustStore = KeyStore.getInstance("JKS");
        
        // Load existing truststore
        try (FileInputStream trustStoreInput = new FileInputStream("truststores/" + userId + "_truststore.jks")) {
            trustStore.load(trustStoreInput, password.toCharArray());
        }

        // Add new certificate
        trustStore.setCertificateEntry(peerId, certificate);

        // Save updated truststore
        try (FileOutputStream fos = new FileOutputStream("truststores/" + userId + "_truststore.jks")) {
            trustStore.store(fos, password.toCharArray());
        }
    }

    private class MessageHandler implements Runnable {
        private SSLSocket socket;
        private PeerController peerController;

        public MessageHandler(SSLSocket socket, PeerController peerController) {
            this.socket = socket;
            this.peerController = peerController;
        }

        @Override
        public void run() {
            ObjectInputStream ois = null;
            ObjectInputStream oisServer = null;
            SSLSocket svSock = null;
            ObjectOutputStream oos = null;
            String ip = null;
            int port = -1;
            byte[] cert = null;

            try {
                ois = new ObjectInputStream(socket.getInputStream());
                Message message = (Message) ois.readObject();

                // Add trusted
                svSock = contactServer();

                oos = new ObjectOutputStream(svSock.getOutputStream());
                oisServer = new ObjectInputStream(svSock.getInputStream());

                oos.writeObject("GETPEER");
                oos.flush();
                oos.writeObject(message.getSender());
                oos.flush();

                ip = (String) oisServer.readObject();
                port = (int) oisServer.readObject();
                cert = (byte[]) oisServer.readObject();

                CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
                ByteArrayInputStream certInputStream = new ByteArrayInputStream(cert);
                X509Certificate certificate = (X509Certificate) certFactory.generateCertificate(certInputStream);

                KeyStore trustStore = KeyStore.getInstance("JKS");

                try (FileInputStream trustStoreInput = new FileInputStream("truststores/" + userId + "_truststore.jks")) {
                    trustStore.load(trustStoreInput, password.toCharArray());
                }

                trustStore.setCertificateEntry(message.getSender(), certificate);

                try (FileOutputStream fos = new FileOutputStream("truststores/" + userId + "_truststore.jks")) {
                    trustStore.store(fos, password.toCharArray());
                }
                
                PublicKey pubKey = EncryptionUtil.getPublicKeyFromTrustStore("truststores/" + userId + "_truststore.jks", password, message.getSender());
                
                // Verify the signature of the message
                if (EncryptionUtil.verifySignature(message.getEncryptedContent(), message.getSignedMessage(), pubKey)) {
                    PrivateKey privKey = EncryptionUtil.getPrivateKeyFromKeystore("keystores/" + userId + "_keystore.jks", password, userId, password);
                    SecretKey skey = EncryptionUtil.decryptAESKey(message.getEncKey(), privKey);

                    String decryptedContent = EncryptionUtil.decrypt(message.getEncryptedContent(), skey);

                    // Update the UI with the received message
                    javafx.application.Platform.runLater(() -> peerController.appendReceivedMessage(message.getSender(), decryptedContent));
                } else {
                    javafx.application.Platform.runLater(() -> peerController.appendError("HMAC verification failed for message from " + message.getSender()));
                    System.out.println("HMAC verification failed for message from " + message.getSender());
                }

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    if (ois != null) ois.close();
                    if (socket != null) socket.close();

                    if (oisServer != null) oisServer.close();
                    if (oos != null) oos.close();
                    if (svSock != null) svSock.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }   
}
