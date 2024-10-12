package com.peerapp;

import javax.net.ssl.*;
import java.io.*;
import java.net.SocketException;
import java.security.KeyStore;
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
        try (FileInputStream keyStoreInput = new FileInputStream("keystores/" + userId + ".jks")) {
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

    public void sendMessage(String recipient, String content, String host) {
        try {
            String encryptedContent = EncryptionUtil.encrypt(content, predefinedKey);
            String hmac = EncryptionUtil.generateHMAC(content, predefinedKey);
            Message message = new Message(userId, recipient, encryptedContent, hmac, content);

            try (SSLSocket socket = createClientSocket(host);
                 ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream())) {
                oos.writeObject(message);
                oos.flush();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private SSLSocket createClientSocket(String host) throws Exception {
        KeyStore trustStore = KeyStore.getInstance("JKS");
        try (FileInputStream trustStoreInput = new FileInputStream("truststores/" + userId + "_truststore.jks")) {
            trustStore.load(trustStoreInput, "password".toCharArray());
        }

        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustManagerFactory.getTrustManagers(), null);

        SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
        return (SSLSocket) sslSocketFactory.createSocket(host, PORT);
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
            if (EncryptionUtil.verifyHMAC(message.getOriginalContent(), message.getHmac(), predefinedKey)) {
                String decryptedContent = EncryptionUtil.decrypt(message.getEncryptedContent(), predefinedKey);
                System.out.println("Received message from " + message.getSender() + ": " + decryptedContent);
            } else {
                System.out.println("HMAC verification failed for message from " + message.getSender());
            }
        } catch (SocketException se) {
            System.err.println("Socket exception: " + se.getMessage());
            se.printStackTrace(); // This will give more context about the error
        } catch (EOFException eof) {
            System.err.println("Connection closed by the client: " + eof.getMessage());
        } catch (Exception e) {
            System.err.println("Error while reading message: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                socket.close(); // Ensure the socket is closed
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

}
