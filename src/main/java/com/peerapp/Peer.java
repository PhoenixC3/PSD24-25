package com.peerapp;

import javax.net.ssl.*;
import java.io.*;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;

import javax.crypto.SecretKey;

public class Peer {
    private String userId;
    private SSLServerSocket serverSocket;
    private final int PORT;
    private PeerController peerController;

    public Peer(String userId, int port, PeerController peerController) throws Exception {
        this.userId = userId;
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
                new Thread(new MessageHandler(socket, peerController)).start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void sendMessage(String recipient, String content) {
        try {
            //Gerar a secret key da mensagem e encriptar com a public key do recipient
            SecretKey key = EncryptionUtil.generateSecretKey();
            PublicKey pubKey = EncryptionUtil.getPublicKeyFromTrustStore("truststores/" + userId + "_truststore.jks","password", recipient);
            byte[] encryptedKey = EncryptionUtil.encryptAESKey(key, pubKey);

            //Encriptar o conteudo da mensagem com a secret key
            String encryptedContent = EncryptionUtil.encrypt(content, key);

            //Assinar a mensagem para verificar integridade
            PrivateKey privKey = EncryptionUtil.getPrivateKeyFromKeystore("keystores/" + userId + "_keystore.jks", "password", userId, "password");
            String signedMessage = EncryptionUtil.signMessage(encryptedContent, privKey);

            //Construir objeto
            Message message = new Message(userId, recipient, encryptedKey, encryptedContent, signedMessage);

            //Enviar pelo socket
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
        KeyStore trustStore = KeyStore.getInstance("JKS");
        try (FileInputStream trustStoreInput = new FileInputStream("truststores/" + userId + "_truststore.jks")) {
            trustStore.load(trustStoreInput, "password".toCharArray());
        }

        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustManagerFactory.getTrustManagers(), null);

        SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
    
        String ip = DatabaseUtil.getPeerIp(recipient);
        int port = DatabaseUtil.getPeerPort(recipient);
    
        return (SSLSocket) sslSocketFactory.createSocket(ip, port);
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
            try (ObjectInputStream ois = new ObjectInputStream(socket.getInputStream())) {
                Message message = (Message) ois.readObject();
                PublicKey pubKey = EncryptionUtil.getPublicKeyFromTrustStore("truststores/" + userId + "_truststore.jks","password", message.getSender());

                if (EncryptionUtil.verifySignature(message.getEncryptedContent(), message.getSignedMessage(), pubKey)) {
                    PrivateKey privKey = EncryptionUtil.getPrivateKeyFromKeystore("keystores/" + userId + "_keystore.jks", "password", userId, "password");
                    SecretKey skey = EncryptionUtil.decryptAESKey(message.getEncKey(), privKey);

                    String decryptedContent = EncryptionUtil.decrypt(message.getEncryptedContent(), skey);

                    javafx.application.Platform.runLater(() -> {
                        peerController.appendReceivedMessage(message.getSender(), decryptedContent);
                    });
                } else {
                    javafx.application.Platform.runLater(() -> {
                        peerController.appendError("HMAC verification failed for message from " + message.getSender());
                    });

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
