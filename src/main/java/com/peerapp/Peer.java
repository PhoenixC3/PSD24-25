package com.peerapp;

import javax.net.ssl.*;
import java.io.*;
import java.security.KeyStore;
import java.security.PrivateKey;
import javax.crypto.SecretKey;

public class Peer {
    private String userId;
    private SSLServerSocket serverSocket;
    private final int PORT;
    private final SecretKey predefinedKey;

    public Peer(String userId, int port, SecretKey key) throws Exception {
        this.userId = userId;
        this.PORT = port;
        this.predefinedKey = key;
        this.serverSocket = createServerSocket();
        new Thread(this::listenForMessages).start();
    }

    public String getUserId() {
        return userId;
    }

    private SSLServerSocket createServerSocket() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("JKS");
        try (FileInputStream keyStoreInput = new FileInputStream("keystores/" + userId + "_keystore.jks")) {
            keyStore.load(keyStoreInput, "password".toCharArray());
        }

        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(keyStore, "password".toCharArray());

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagerFactory.getKeyManagers(), null, null);

        SSLServerSocketFactory sslServerSocketFactory = sslContext.getServerSocketFactory();
        return (SSLServerSocket) sslServerSocketFactory.createServerSocket(PORT);
    }

    private void listenForMessages() {
        while (true) {
            try {
                SSLSocket socket = (SSLSocket) serverSocket.accept();
                new Thread(new MessageHandler(socket)).start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void sendMessage(String recipient, String content) {
        try {
            String encryptedContent = EncryptionUtil.encrypt(content, predefinedKey);
            String hmac = EncryptionUtil.generateHMAC(content, predefinedKey);
            Message message = new Message(userId, recipient, encryptedContent, hmac);

            try (SSLSocket socket = createClientSocket(recipient);
                 ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream())) {
                oos.writeObject(message);
                oos.flush();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private SSLSocket createClientSocket(String recipient) throws Exception {
        // Load the truststore from a file.
        KeyStore trustStore = KeyStore.getInstance("JKS");
        try (FileInputStream trustStoreInput = new FileInputStream("truststores/" + userId + "_truststore.jks")) {
            trustStore.load(trustStoreInput, "password".toCharArray()); // Replace "password" with the actual password.
        }
    
        // Initialize the TrustManagerFactory using the loaded truststore.
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);
    
        // Create SSLContext with the TrustManagers from the TrustManagerFactory.
        SSLContext sslContext = SSLContext.getInstance("TLS"); // Or "TLSv1.2" if you want to specify a version.
        sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
    
        // Get the SSLSocketFactory from the SSLContext.
        SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
    
        // Fetch IP and port of the peer from the database.
        String ip = DatabaseUtil.getPeerIp(recipient);
        int port = DatabaseUtil.getPeerPort(recipient);
    
        // Create and return the SSLSocket, connecting to the peer.
        return (SSLSocket) sslSocketFactory.createSocket(ip, port);
    }

    private class MessageHandler implements Runnable {
        private SSLSocket socket;

        public MessageHandler(SSLSocket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (ObjectInputStream ois = new ObjectInputStream(socket.getInputStream())) {
                Message message = (Message) ois.readObject();
                if (EncryptionUtil.verifyHMAC(message.getEncryptedContent(), message.getHmac(), predefinedKey)) {
                    String decryptedContent = EncryptionUtil.decrypt(message.getEncryptedContent(), predefinedKey);
                    System.out.println("Received message from " + message.getSender() + ": " + decryptedContent);
                } else {
                    System.out.println("HMAC verification failed for message from " + message.getSender());
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    
}
