package com.peerapp;

import javax.net.ssl.*;

import javafx.application.Platform;

import java.io.*;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import javax.crypto.SecretKey;

public class Peer {
    private String userId;
    private String password;
    
    private SSLServerSocket serverSocket;
    private final int PORT;

    private PeerController peerController;

    private SSLSocket dbServer;
    private ObjectOutputStream oosServer;
    private ObjectInputStream oisServer;

    private HashMap<String, Integer> unreadMsgs = new HashMap<String, Integer>();
    private HashMap<String, Integer> unreadMsgsGroups = new HashMap<String, Integer>();

    public Peer(String userId, String password, int port, PeerController peerController) throws Exception {
        this.userId = userId;
        this.password = password;
        this.PORT = port;
        this.peerController = peerController;

        refreshServer();

        //Set up our receiving socket
        this.serverSocket = createServerSocket();

        //Start listening
        new Thread(this::listenForMessages).start();
    }

    public String getUserId() {
        return userId;
    }

    private void refreshServer() {
        try {
            //Set up connection to server
            this.dbServer = contactServer();
            this.oosServer = new ObjectOutputStream(dbServer.getOutputStream());
            this.oisServer = new ObjectInputStream(dbServer.getInputStream());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //Create our SSL/TLS socket for receiving (Peer server)
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

    //Listen for incoming messages
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

    //Send a message through client socket
    public synchronized boolean sendMessage(String recipient, String content) {
        SSLSocket socket = null;
        ObjectOutputStream oos = null;

        try {
            //Create the client SSL/TLS socket
            socket = createClientSocket(recipient);

            if (socket != null) {   
                //Generate a shared secret key for the message
                SecretKey key = EncryptionUtil.generateSecretKey();
                PublicKey pubKey = EncryptionUtil.getPublicKeyFromTrustStore("truststores/" + userId + "_truststore.jks", password, recipient);
                byte[] encryptedKey = EncryptionUtil.encryptAESKey(key, pubKey);
        
                //Encrypt the message content with the secret key
                String[] encryptedContent = EncryptionUtil.encrypt(content, key);
                byte[] iv = Base64.getDecoder().decode(encryptedContent[1]);
        
                //Sign the message for integrity
                PrivateKey privKey = EncryptionUtil.getPrivateKeyFromKeystore("keystores/" + userId + "_keystore.jks", password, userId, password);
                String signedMessage = EncryptionUtil.signMessage(encryptedContent[0], privKey);
        
                // Create message object
                Message message = new Message(userId, recipient, encryptedKey, encryptedContent[0], signedMessage, iv);
                oos = new ObjectOutputStream(socket.getOutputStream());
                oos.writeObject(message);
                oos.flush();

                return true;
            }
            else 
            {
                //The message is "sent" (to the server for offline storage) but stays in the offline messaging queue
                SecretKey key = EncryptionUtil.generateSecretKey();
                PublicKey pubKey = EncryptionUtil.getPublicKeyFromTrustStore("truststores/" + userId + "_truststore.jks", password, recipient);
                byte[] encryptedKey = EncryptionUtil.encryptAESKey(key, pubKey);
        
                String[] encryptedContent = EncryptionUtil.encrypt(content, key);
                byte[] iv = Base64.getDecoder().decode(encryptedContent[1]);
        
                PrivateKey privKey = EncryptionUtil.getPrivateKeyFromKeystore("keystores/" + userId + "_keystore.jks", password, userId, password);
                String signedMessage = EncryptionUtil.signMessage(encryptedContent[0], privKey);
        
                Message message = new Message(userId, recipient, encryptedKey, encryptedContent[0], signedMessage, iv);

                refreshServer();

                try {

                    oosServer.writeObject("ADDOFFLINE");
                    oosServer.flush();

                    oosServer.writeObject(message);
                    oosServer.flush();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                return true;

            }
    
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
    }

    public synchronized void sendGroupMessage(String group, String content) {
        try {
            oosServer.writeObject("GETGROUPMEMBERS");
            oosServer.flush();
    
            oosServer.writeObject(group);
            oosServer.flush();
    
            String res = (String) oisServer.readObject();
    
            if (res.equals("OK")) {
                List<String> members = (List<String>) oisServer.readObject();
    
                // Generate a shared secret key for the message
                SecretKey key = EncryptionUtil.generateSecretKey();
                PrivateKey privKey = EncryptionUtil.getPrivateKeyFromKeystore("keystores/" + userId + "_keystore.jks", password, userId, password);
                String[] encryptedContent = EncryptionUtil.encrypt(content, key);
                byte[] iv = Base64.getDecoder().decode(encryptedContent[1]);
                String signedMessage = EncryptionUtil.signMessage(encryptedContent[0], privKey);
    
                // Send messages in parallel
                members.parallelStream().forEach(mem -> {
                    SSLSocket socket = null;
                    ObjectOutputStream oos = null;
                
                    if (!mem.equals(userId)) {
                        try {
                            //Create the client SSL/TLS socket
                            socket = createClientSocket(mem);
                
                            if (socket != null) {
                                PublicKey pubKey = EncryptionUtil.getPublicKeyFromTrustStore("truststores/" + userId + "_truststore.jks", password, mem);
                                byte[] encryptedKey = EncryptionUtil.encryptAESKey(key, pubKey);
                        
                                // Create message object
                                Message message = new Message(userId, mem, encryptedKey, encryptedContent[0], signedMessage, iv, group);
                                oos = new ObjectOutputStream(socket.getOutputStream());
                                oos.writeObject(message);
                                oos.flush();
                            }
                            else 
                            {
                                PublicKey pubKey = EncryptionUtil.getPublicKeyFromTrustStore("truststores/" + userId + "_truststore.jks", password, mem);
                                byte[] encryptedKey = EncryptionUtil.encryptAESKey(key, pubKey);
                        
                                Message message = new Message(userId, mem, encryptedKey, encryptedContent[0], signedMessage, iv, group);
                
                                refreshServer();
                
                                try {
                
                                    oosServer.writeObject("ADDOFFLINEGROUP");
                                    oosServer.flush();
                
                                    oosServer.writeObject(message);
                                    oosServer.flush();

                                    oosServer.writeObject(group);
                                    oosServer.flush();
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                    
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //Create the socket to communicate with the client
    private SSLSocket createClientSocket(String recipient) throws Exception {
        String ip = null;
        int port = -1;
        byte[] cert = null;
        SSLSocket ret = null;
    
        try {
            KeyStore trustStore = KeyStore.getInstance("JKS");

            try (FileInputStream trustStoreInput = new FileInputStream("truststores/" + userId + "_truststore.jks")) {
                trustStore.load(trustStoreInput, password.toCharArray());
            }

            refreshServer();

            //Get peer data
            oosServer.writeObject("GETPEER");
            oosServer.flush();

            oosServer.writeObject(recipient);
            oosServer.flush();

            String res = (String) oisServer.readObject();

            //Add user to trusted for SSL/TLS sockets
            if (res.equals("OK")) {
                ip = (String) oisServer.readObject();
                port = (int) oisServer.readObject();
                cert = (byte[]) oisServer.readObject();

                CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
                ByteArrayInputStream certInputStream = new ByteArrayInputStream(cert);
                X509Certificate certificate = (X509Certificate) certFactory.generateCertificate(certInputStream);

                trustStore.setCertificateEntry(recipient, certificate);

                try (FileOutputStream fos = new FileOutputStream("truststores/" + userId + "_truststore.jks")) {
                    trustStore.store(fos, password.toCharArray());
                }
        
                TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                trustManagerFactory.init(trustStore);
        
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
        
                SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

                ret = (SSLSocket) sslSocketFactory.createSocket(ip, port);
            }

            return ret;

        } catch (Exception e) {
            return null;
        }
    }

    //Create a socket connection with the database server
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
    
        List<String> ipPortPairs = readIpPortPairsFromFile("serverAddresses.txt");

        for (String ipPort : ipPortPairs) {
            String[] parts = ipPort.split(":");
            String ip = parts[0];
            int port = Integer.parseInt(parts[1]);

            try {
                SSLSocket socket = (SSLSocket) sslSocketFactory.createSocket(ip, port);

                // Test the connection by starting the SSL handshake
                socket.startHandshake();
                return socket;
            } catch (IOException e) {
                System.err.println("Failed to connect to " + ip + ":" + port + ", trying next...");
            }
        }

        throw new IOException("Failed to connect to any of the specified server addresses.");
    }

    //Read server addresses from file
    private List<String> readIpPortPairsFromFile(String fileName) throws IOException {
        List<String> ipPortPairs = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
            String line;

            while ((line = br.readLine()) != null) {
                ipPortPairs.add(line.trim());
            }
        }

        return ipPortPairs;
    }

    //Get peer's info from the database server
    public void getPeerInfo(String username) {
        refreshServer();

        if (username.equals(userId)) {
            //Can't send a message to yourself
            peerController.updatePeerList(null);
        }
        else
        {
            try {
                //Ask the server if the user exists
                oosServer.writeObject("GETPEER");
                oosServer.flush();

                oosServer.writeObject(username);
                oosServer.flush();
    
                String status = (String) oisServer.readObject();
                
                if (status.equals("OK")) {
                    String ip = (String) oisServer.readObject();
                    int port = (int) oisServer.readObject();
                    byte[] cert = (byte[]) oisServer.readObject();
                    
                    //Send it to the controller
                    Platform.runLater(() -> {
                        peerController.updatePeerList(username);
                    });
                }
                else 
                {
                    //User does not exist
                    peerController.updatePeerList(null);
                }
    
            } catch (Exception e) {
                e.printStackTrace();

                Platform.runLater(() -> {
                    peerController.appendError("Error querying peer information: " + e.getMessage());
                });
            }
        }
    }

    public void saveConnectedPeers(List<String> peers) {
        refreshServer();

        try {
            oosServer.writeObject("SAVEPEERSORDER");
            oosServer.flush();

            oosServer.writeObject(userId);
            oosServer.flush();

            oosServer.writeObject(peers);
            oosServer.flush();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<String> getConnectedPeers() {
        refreshServer();

        try {
            oosServer.writeObject("GETALLPEERS");
            oosServer.flush();

            oosServer.writeObject(userId);
            oosServer.flush();

            List<String> peers = (List<String>) oisServer.readObject();

            peers.remove(userId);

            return peers;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public List<String> getMyGroups() {
        refreshServer();

        try {
            oosServer.writeObject("GETMYGROUPS");
            oosServer.flush();

            oosServer.writeObject(userId);
            oosServer.flush();

            List<String> groups = (List<String>) oisServer.readObject();

            return groups;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public List<String> getAvailableGroups() {
        refreshServer();

        try {
            oosServer.writeObject("GETGROUPS");
            oosServer.flush();

            oosServer.writeObject(userId);
            oosServer.flush();

            List<String> groups = (List<String>) oisServer.readObject();

            return groups;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    //Create a group chat
    public void createGroup(String groupTopic) {
        refreshServer();

        try {
            //Ask the server to create the group
            oosServer.writeObject("CREATEGROUP");
            oosServer.flush();

            oosServer.writeObject(groupTopic);
            oosServer.flush();

            oosServer.writeObject(userId);
            oosServer.flush();

            String status = (String) oisServer.readObject();

            if (status.equals("OK")) {
                //Send it to the controller
                Platform.runLater(() -> {
                    peerController.updateGroupList(groupTopic);
                });
            }
            else if (status.equals("EXISTS"))
            {
                //Group already exists
                Platform.runLater(() -> {
                    peerController.appendErrorGroup("Group already exists");
                });
            } else {
                //Error creating group
                Platform.runLater(() -> {
                    peerController.appendErrorGroup("Error creating group");
                });
            }

        } catch (Exception e) {
            e.printStackTrace();

            Platform.runLater(() -> {
                peerController.appendErrorGroup("Error creating group: " + e.getMessage());
            });
        }
    }

    // Join a group chat
    public void addMemberToGroup(String groupTopic, String member) {
        refreshServer();

        try {
            // Ask the server to join the group
            oosServer.writeObject("JOINGROUP");
            oosServer.flush();

            oosServer.writeObject(groupTopic);
            oosServer.flush();

            oosServer.writeObject(member);
            oosServer.flush();

            String status = (String) oisServer.readObject();

            if (status.equals("OK")) {
                // Successfully added to the group
                Platform.runLater(() -> {
                    try {
                        SSLSocket socket = createClientSocket(member);

                        if (socket != null) {
                            ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
                            
                            oos.writeObject("ADDEDTOGROUP");
                            oos.flush();

                            oos.writeObject(groupTopic);
                            oos.flush();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    peerController.appendErrorGroup("User successfully added to group");
                });
            } else if (status.equals("NOTFOUND")) {
                // Group does not exist
                Platform.runLater(() -> {
                    peerController.appendErrorGroup("Group does not exist");
                });
            } else if (status.equals("ALREADYIN")) {
                // Group does not exist
                Platform.runLater(() -> {
                    peerController.appendErrorGroup("User already in this group");
                });
            } else {
                // Error joining group
                Platform.runLater(() -> {
                    peerController.appendErrorGroup("Error joining group");
                });
            }

        } catch (Exception e) {
            e.printStackTrace();

            Platform.runLater(() -> {
                peerController.appendErrorGroup("Error joining group: " + e.getMessage());
            });
        }
    }

    //Get the messages we received while offline and the unread counts (by conversation)
    public HashMap<String, LinkedList<Message>> loadOfflineMessageHistory() {
        refreshServer();

        try {

            oosServer.writeObject("LOADOFFLINE");
            oosServer.flush();

            oosServer.writeObject(userId);
            oosServer.flush();

            String res = (String) oisServer.readObject();

            if (res.equals("OK")) {
                HashMap<String, LinkedList<Message>> convs = (HashMap<String, LinkedList<Message>>) oisServer.readObject();
                HashMap<String, Integer> unread = (HashMap<String, Integer>) oisServer.readObject();

                //Add the number of unread offline messages to the number of unread online messages
                for (String key : unread.keySet()) {
                    if (unreadMsgs.containsKey(key)) {
                        int temp = unread.get(key);
                        int temp2 = unreadMsgs.get(key);

                        unreadMsgs.put(key, (temp + temp2));
                    }
                    else {
                        int temp = unread.get(key);

                        unreadMsgs.put(key, temp);
                    }
                }

                return convs;
            }

            return null;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    //Get the messages we received while offline and the unread counts (by conversation)
    public HashMap<String, LinkedList<Message>> loadOfflineMessageHistoryGroups() {
        refreshServer();

        try {

            oosServer.writeObject("LOADOFFLINEGROUP");
            oosServer.flush();

            oosServer.writeObject(userId);
            oosServer.flush();

            String res = (String) oisServer.readObject();

            if (res.equals("OK")) {
                HashMap<String, LinkedList<Message>> convs = (HashMap<String, LinkedList<Message>>) oisServer.readObject();
                HashMap<String, Integer> unread = (HashMap<String, Integer>) oisServer.readObject();

                //Add the number of unread offline messages to the number of unread online messages
                for (String key : unread.keySet()) {
                    if (unreadMsgsGroups.containsKey(key)) {
                        int temp = unread.get(key);
                        int temp2 = unreadMsgsGroups.get(key);

                        unreadMsgsGroups.put(key, (temp + temp2));
                    }
                    else {
                        int temp = unread.get(key);

                        unreadMsgsGroups.put(key, temp);
                    }
                }

                return convs;
            }

            return null;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
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
            refreshServer();
            
            try (ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream ois = new ObjectInputStream(socket.getInputStream())) {

                Object o = ois.readObject();
                
                if (o instanceof Message) {
                    //Handle socket concurrency
                    synchronized (socket) {
                        Message message = (Message) o;
                        boolean notfound = false;

                        KeyStore trustStore = KeyStore.getInstance("JKS");

                        try (FileInputStream trustStoreInput = new FileInputStream("truststores/" + userId + "_truststore.jks")) {
                            trustStore.load(trustStoreInput, password.toCharArray());
                        }

                        if (message.getGroup() == null) {
                            if (trustStore.getCertificate(message.getSender()) == null) {
                                // Add trusted
                                try {
        
                                    oosServer.writeObject("GETPEER");
                                    oosServer.flush();
                                    oosServer.writeObject(message.getSender());
                                    oosServer.flush();
        
                                    String res = (String) oisServer.readObject();
        
                                    if (res.equals("OK")) {
                                        String ip = (String) oisServer.readObject();
                                        int port = (int) oisServer.readObject();
                                        byte[] cert = (byte[]) oisServer.readObject();
        
                                        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
                                        ByteArrayInputStream certInputStream = new ByteArrayInputStream(cert);
                                        X509Certificate certificate = (X509Certificate) certFactory.generateCertificate(certInputStream);
        
                                        trustStore.setCertificateEntry(message.getSender(), certificate);
        
                                        try (FileOutputStream fos = new FileOutputStream("truststores/" + userId + "_truststore.jks")) {
                                            trustStore.store(fos, password.toCharArray());
                                        }
                                    }
                                    else 
                                    {
                                        notfound = true;
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
        
                            if (notfound) {
                                javafx.application.Platform.runLater(() -> peerController.appendError("User does not exist: " + message.getSender()));
                                System.out.println("User does not exist: " + message.getSender());
                            }
                            else 
                            {
                                PublicKey pubKey = EncryptionUtil.getPublicKeyFromTrustStore("truststores/" + userId + "_truststore.jks", password, message.getSender());
                            
                                //Verify the integrity of the message
                                if (EncryptionUtil.verifySignature(message.getEncryptedContent(), message.getSignedMessage(), pubKey)) {
                                    //Decrypt the message
                                    PrivateKey privKey = EncryptionUtil.getPrivateKeyFromKeystore("keystores/" + userId + "_keystore.jks", password, userId, password);
                                    SecretKey skey = EncryptionUtil.decryptAESKey(message.getEncKey(), privKey);
        
                                    String decryptedContent = EncryptionUtil.decrypt(message.getEncryptedContent(), skey, message.getIV());
        
                                    //Send it to the controller
                                    javafx.application.Platform.runLater(() -> peerController.appendReceivedMessage(message.getSender(), decryptedContent));
                                } else {
                                    //Integrity check failed
                                    javafx.application.Platform.runLater(() -> peerController.appendError("Integrity verification failed for message from " + message.getSender()));
                                    System.out.println("Integrity verification failed for message from " + message.getSender());
                                }
                            }
                        }
                        else {
                            if (trustStore.getCertificate(message.getSender()) == null) {
                                // Add trusted
                                try {
        
                                    oosServer.writeObject("GETPEER");
                                    oosServer.flush();
                                    oosServer.writeObject(message.getSender());
                                    oosServer.flush();
        
                                    String res = (String) oisServer.readObject();
        
                                    if (res.equals("OK")) {
                                        String ip = (String) oisServer.readObject();
                                        int port = (int) oisServer.readObject();
                                        byte[] cert = (byte[]) oisServer.readObject();
        
                                        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
                                        ByteArrayInputStream certInputStream = new ByteArrayInputStream(cert);
                                        X509Certificate certificate = (X509Certificate) certFactory.generateCertificate(certInputStream);
        
                                        trustStore.setCertificateEntry(message.getSender(), certificate);
        
                                        try (FileOutputStream fos = new FileOutputStream("truststores/" + userId + "_truststore.jks")) {
                                            trustStore.store(fos, password.toCharArray());
                                        }
                                    }
                                    else 
                                    {
                                        notfound = true;
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
        
                            if (notfound) {
                                javafx.application.Platform.runLater(() -> peerController.appendError("User does not exist: " + message.getSender()));
                                System.out.println("User does not exist: " + message.getSender());
                            }
                            else 
                            {
                                PublicKey pubKey = EncryptionUtil.getPublicKeyFromTrustStore("truststores/" + userId + "_truststore.jks", password, message.getSender());
                            
                                //Verify the integrity of the message
                                if (EncryptionUtil.verifySignature(message.getEncryptedContent(), message.getSignedMessage(), pubKey)) {
                                    //Decrypt the message
                                    PrivateKey privKey = EncryptionUtil.getPrivateKeyFromKeystore("keystores/" + userId + "_keystore.jks", password, userId, password);
                                    SecretKey skey = EncryptionUtil.decryptAESKey(message.getEncKey(), privKey);
        
                                    String decryptedContent = EncryptionUtil.decrypt(message.getEncryptedContent(), skey, message.getIV());
        
                                    //Send it to the controller
                                    javafx.application.Platform.runLater(() -> peerController.appendReceivedMessageGroup(message.getGroup(), message.getSender(), decryptedContent));
                                } else {
                                    //Integrity check failed
                                    javafx.application.Platform.runLater(() -> peerController.appendError("Integrity verification failed for message from " + message.getSender()));
                                    System.out.println("Integrity verification failed for message from " + message.getSender());
                                }
                            }
                        }
                    }
                }
                else if (o instanceof String) {
                    String s = (String) o;

                    if (s.equals("ADDEDTOGROUP")) {
                        peerController.updateGroupList((String) ois.readObject());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    //Save the history of messages (on closing the app)
    public void saveMessageHistory(HashMap<String, LinkedList<String>> convs, HashMap<String, Integer> unread) {
        refreshServer();

        try {

            oosServer.writeObject("SAVEMSGS");
            oosServer.flush();

            oosServer.writeObject(userId);
            oosServer.flush();

            //Message history
            oosServer.writeObject(convs);
            oosServer.flush();

            //Number of unread messages
            oosServer.writeObject(unread);
            oosServer.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //Save the history of messages (on closing the app)
    public void saveMessageHistoryGroups(HashMap<String, LinkedList<String>> convs, HashMap<String, Integer> unread) {
        refreshServer();

        try {

            oosServer.writeObject("SAVEMSGSGROUPS");
            oosServer.flush();

            oosServer.writeObject(userId);
            oosServer.flush();

            //Message history
            oosServer.writeObject(convs);
            oosServer.flush();

            //Number of unread messages
            oosServer.writeObject(unread);
            oosServer.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    //Load the history of messages and the unread counts
    public HashMap<String, LinkedList<String>> loadMessageHistory() {
        refreshServer();

        try {

            oosServer.writeObject("LOADMSGS");
            oosServer.flush();

            oosServer.writeObject(userId);
            oosServer.flush();

            String res = (String) oisServer.readObject();

            if (res.equals("OK")) {
                //Messages history
                HashMap<String, LinkedList<String>> convs = (HashMap<String, LinkedList<String>>) oisServer.readObject();

                //Unread counts by conversation
                HashMap<String, Integer> unread = (HashMap<String, Integer>) oisServer.readObject();

                //Using a global variable because we can't return both maps
                unreadMsgs = unread;

                //Messages received offline
                HashMap<String, LinkedList<Message>> offMsgs = loadOfflineMessageHistory();

                if (offMsgs != null) {
                    //For each offline message, check the intergity and decrypt it
                    for (String key : offMsgs.keySet()) {
                        LinkedList<Message> msgs = offMsgs.get(key);
                        KeyStore trustStore = KeyStore.getInstance("JKS");

                        try (FileInputStream trustStoreInput = new FileInputStream("truststores/" + userId + "_truststore.jks")) {
                            trustStore.load(trustStoreInput, password.toCharArray());
                        }

                        for (Message msg : msgs) {
                            if (trustStore.getCertificate(msg.getSender()) == null) {
                                // Add trusted
                                try {
        
                                    oosServer.writeObject("GETPEER");
                                    oosServer.flush();

                                    oosServer.writeObject(msg.getSender());
                                    oosServer.flush();
        
                                    String resPeer = (String) oisServer.readObject();
        
                                    if (resPeer.equals("OK")) {
                                        String ip = (String) oisServer.readObject();
                                        int port = (int) oisServer.readObject();
                                        byte[] cert = (byte[]) oisServer.readObject();
        
                                        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
                                        ByteArrayInputStream certInputStream = new ByteArrayInputStream(cert);
                                        X509Certificate certificate = (X509Certificate) certFactory.generateCertificate(certInputStream);
        
                                        trustStore.setCertificateEntry(msg.getSender(), certificate);
        
                                        try (FileOutputStream fos = new FileOutputStream("truststores/" + userId + "_truststore.jks")) {
                                            trustStore.store(fos, password.toCharArray());
                                        }
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }

                            PublicKey pubKey = EncryptionUtil.getPublicKeyFromTrustStore("truststores/" + userId + "_truststore.jks", password, msg.getSender());
                    
                            // Verify the signature of the message
                            if (EncryptionUtil.verifySignature(msg.getEncryptedContent(), msg.getSignedMessage(), pubKey)) {
                                PrivateKey privKey = EncryptionUtil.getPrivateKeyFromKeystore("keystores/" + userId + "_keystore.jks", password, userId, password);
                                SecretKey skey = EncryptionUtil.decryptAESKey(msg.getEncKey(), privKey);

                                String decryptedContent = EncryptionUtil.decrypt(msg.getEncryptedContent(), skey, msg.getIV());

                                //Adding the "You" and "Other" identificators for the controller's visual verifications
                                if (msg.getSender().equals(userId)) {
                                    decryptedContent = "You: " + decryptedContent;
                                }
                                else 
                                {
                                    decryptedContent = "Other: " + decryptedContent;
                                }

                                //Add the offline messages to the conversations map
                                LinkedList<String> senderConvs = convs.get(msg.getSender());

                                if (senderConvs == null) {
                                    senderConvs = new LinkedList<String>();
                                }

                                senderConvs.add(decryptedContent);
                                convs.put(msg.getSender(), senderConvs);

                                //Update the unread count and add the sender to connected users (if it isn't already there) so it appears on the left side chat
                                javafx.application.Platform.runLater(() -> peerController.updateOfflineMsgCount(msg.getSender()));
                                javafx.application.Platform.runLater(() -> peerController.addToConnected(msg.getSender()));
                            } else {
                                //Integrity check failed
                                javafx.application.Platform.runLater(() -> peerController.appendError("Integrity verification failed for message from " + msg.getSender()));
                                System.out.println("Integrity verification failed for message from " + msg.getSender());
                            }
                        }
                    }

                }

                return convs;
            }

            return null;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    //Load the history of messages and the unread counts
    public HashMap<String, LinkedList<String>> loadMessageHistoryGroups() {
        refreshServer();

        try {

            oosServer.writeObject("LOADMSGSGROUPS");
            oosServer.flush();

            oosServer.writeObject(userId);
            oosServer.flush();

            String res = (String) oisServer.readObject();

            if (res.equals("OK")) {
                //Messages history
                HashMap<String, LinkedList<String>> convs = (HashMap<String, LinkedList<String>>) oisServer.readObject();

                //Unread counts by conversation
                HashMap<String, Integer> unread = (HashMap<String, Integer>) oisServer.readObject();

                //Using a global variable because we can't return both maps
                unreadMsgsGroups = unread;

                //Messages received offline
                HashMap<String, LinkedList<Message>> offMsgs = loadOfflineMessageHistoryGroups();

                if (offMsgs != null) {
                    //For each offline message, check the intergity and decrypt it
                    for (String key : offMsgs.keySet()) {
                        LinkedList<Message> msgs = offMsgs.get(key);
                        KeyStore trustStore = KeyStore.getInstance("JKS");

                        try (FileInputStream trustStoreInput = new FileInputStream("truststores/" + userId + "_truststore.jks")) {
                            trustStore.load(trustStoreInput, password.toCharArray());
                        }

                        for (Message msg : msgs) {
                            if (trustStore.getCertificate(msg.getSender()) == null) {
                                // Add trusted
                                try {
        
                                    oosServer.writeObject("GETPEER");
                                    oosServer.flush();

                                    oosServer.writeObject(msg.getSender());
                                    oosServer.flush();
        
                                    String resPeer = (String) oisServer.readObject();
        
                                    if (resPeer.equals("OK")) {
                                        String ip = (String) oisServer.readObject();
                                        int port = (int) oisServer.readObject();
                                        byte[] cert = (byte[]) oisServer.readObject();
        
                                        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
                                        ByteArrayInputStream certInputStream = new ByteArrayInputStream(cert);
                                        X509Certificate certificate = (X509Certificate) certFactory.generateCertificate(certInputStream);
        
                                        trustStore.setCertificateEntry(msg.getSender(), certificate);
        
                                        try (FileOutputStream fos = new FileOutputStream("truststores/" + userId + "_truststore.jks")) {
                                            trustStore.store(fos, password.toCharArray());
                                        }
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }

                            PublicKey pubKey = EncryptionUtil.getPublicKeyFromTrustStore("truststores/" + userId + "_truststore.jks", password, msg.getSender());
                    
                            // Verify the signature of the message
                            if (EncryptionUtil.verifySignature(msg.getEncryptedContent(), msg.getSignedMessage(), pubKey)) {
                                PrivateKey privKey = EncryptionUtil.getPrivateKeyFromKeystore("keystores/" + userId + "_keystore.jks", password, userId, password);
                                SecretKey skey = EncryptionUtil.decryptAESKey(msg.getEncKey(), privKey);

                                String decryptedContent = EncryptionUtil.decrypt(msg.getEncryptedContent(), skey, msg.getIV());

                                //Adding the "You" and "Other" identificators for the controller's visual verifications
                                if (msg.getSender().equals(userId)) {
                                    decryptedContent = "You: " + decryptedContent;
                                }
                                else 
                                {
                                    decryptedContent = "Other: " + decryptedContent;
                                }

                                //Add the offline messages to the conversations map
                                LinkedList<String> senderConvs = convs.get(key);

                                if (senderConvs == null) {
                                    senderConvs = new LinkedList<String>();
                                }

                                senderConvs.add(decryptedContent);
                                convs.put(key, senderConvs);

                                //Update the unread count and add the sender to connected users (if it isn't already there) so it appears on the left side chat
                                javafx.application.Platform.runLater(() -> peerController.updateOfflineMsgCountGroups(key));
                            } else {
                                //Integrity check failed
                                javafx.application.Platform.runLater(() -> peerController.appendError("Integrity verification failed for message from " + msg.getSender()));
                                System.out.println("Integrity verification failed for message from " + msg.getSender());
                            }
                        }
                    }

                }

                return convs;
            }

            return null;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    } 

    public HashMap<String, Integer> getMessageCounts() {
        return unreadMsgs;
    }

    public HashMap<String, Integer> getMessageCountsGroups() {
        return unreadMsgsGroups;
    }
}
