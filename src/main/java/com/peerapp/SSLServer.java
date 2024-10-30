package com.peerapp;

import javax.net.ssl.*;
import java.io.*;
import java.security.KeyStore;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.LinkedList;

public class SSLServer {

    private static final int PORT = 8080;
    private static final String DB_URL = "jdbc:sqlite:peers.db";
    private static Connection conn;
    private static SSLServerSocket svSocket;

    private static final String CREATE_PEER_TABLE_SQL = 
        "CREATE TABLE IF NOT EXISTS peers (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
        "username TEXT NOT NULL UNIQUE, " +
        "password TEXT NOT NULL, " +
        "salt BLOB NOT NULL, " +
        "ip TEXT NOT NULL, " +
        "port INTEGER NOT NULL, " +
        "cert BLOB NOT NULL);";

    private static final String CREATE_MESSAGE_TABLE_SQL = 
    "CREATE TABLE IF NOT EXISTS messages (" +
    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
    "username TEXT NOT NULL UNIQUE, " +
    "msgs BLOB NOT NULL, " +
    "unread BLOB NOT NULL);";

    public static void main(String[] args) {
        Connection conn = null;
        Statement stmt = null;

        try {
            //Create peer table
            conn = connect();
            stmt = conn.createStatement();
            stmt.execute(CREATE_PEER_TABLE_SQL);

            try {
                if (stmt != null) {
                    stmt.close();
                }
                if (conn != null) {
                    conn.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            //Create message table
            conn = connect();
            stmt = conn.createStatement();
            stmt.execute(CREATE_MESSAGE_TABLE_SQL);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
                if (conn != null) {
                    conn.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        try {
            //Create the server socket
            KeyStore keyStore = KeyStore.getInstance("JKS");
            try (FileInputStream keyStoreInput = new FileInputStream("keystores/server_keystore.jks")) {
                keyStore.load(keyStoreInput, "serverpass".toCharArray());
            }

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, "serverpass".toCharArray());

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), null, null);

            SSLServerSocketFactory sslServerSocketFactory = sslContext.getServerSocketFactory();
            svSocket = (SSLServerSocket) sslServerSocketFactory.createServerSocket(PORT);

            System.out.println("Server is listening on port: " + PORT);

            //Listen for connections
            while (true) {
                try {
                    SSLSocket socket = (SSLSocket) svSocket.accept();
                    System.out.println("Client connected: " + socket.getInetAddress() + ":" + socket.getPort());
                    new Thread(new ClientHandler(socket)).start();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            close();
        }
    }

    //Connect to the database
    private static Connection connect() throws SQLException {
        if (conn == null || conn.isClosed()) {
            conn = DriverManager.getConnection(DB_URL);
        }
        return conn;
    }
    
    //Close the server socket and the database connection
    private static void close() {
        try {
            if (svSocket != null && !svSocket.isClosed()) {
                svSocket.close();
            }

            if (conn != null && !conn.isClosed()) {
                conn.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing SSL server socket: " + e.getMessage());
        } catch (SQLException e) {
            System.err.println("Error closing database connection: " + e.getMessage());
        }
    }
}

class ClientHandler implements Runnable {
    private final SSLSocket sslSocket;
    private static final String DB_URL = "jdbc:sqlite:peers.db";
    private static Connection conn;

    public ClientHandler(SSLSocket sslSocket) {
        this.sslSocket = sslSocket;
    }

    @Override
    public void run() {
        try (ObjectInputStream in = new ObjectInputStream(sslSocket.getInputStream());
             ObjectOutputStream out = new ObjectOutputStream(sslSocket.getOutputStream())) {

            String clientMessage;

            //Handle user requests until dying
            while (true) {
                try {
                    clientMessage = (String) in.readObject();
                    
                    if (clientMessage != null) {
                        switch (clientMessage) {
                            case "REGISTER":
                                String user = (String) in.readObject();
                                String hashedPassword = (String) in.readObject();
                                byte[] salt = (byte[]) in.readObject();
                                String ip = (String) in.readObject();
                                int port = (int) in.readObject();
                                byte[] cert = (byte[]) in.readObject();

                                //Register the user's entry in the database
                                String resReg = saveUserInDatabase(user, hashedPassword, salt, ip, port, cert);

                                out.writeObject(resReg);
                                out.flush();

                                if (resReg.equals("OK")) {
                                    //Insert placeholders for unread messages and message history
                                    Connection connReg = null;
                                    PreparedStatement stmtReg = null;
                                    String insertQueryReg = "INSERT OR REPLACE INTO messages (username, msgs, unread) VALUES (?, ?, ?)";
                                    HashMap<String, LinkedList<String>> convsReg = new HashMap<String, LinkedList<String>>();
                                    HashMap<String, Integer> unreadReg = new HashMap<String, Integer>();
                                    byte[] mapReg = null;
                                    byte[] mapUnreadReg = null;

                                    try {
                                        connReg = connect();
                                        stmtReg = conn.prepareStatement(insertQueryReg);

                                        try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
                                            ObjectOutputStream outMap = new ObjectOutputStream(byteOut)) {

                                            outMap.writeObject(convsReg);
                                            mapReg = byteOut.toByteArray();
                                        }

                                        try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
                                            ObjectOutputStream outMap = new ObjectOutputStream(byteOut)) {

                                            outMap.writeObject(unreadReg);
                                            mapUnreadReg = byteOut.toByteArray();
                                        }
        
                                        stmtReg.setString(1, user);
                                        stmtReg.setBytes(2, mapReg);
                                        stmtReg.setBytes(3, mapUnreadReg);
                                
                                        stmtReg.executeUpdate();

                                        System.out.println("Message placeholder saved.");

                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    } finally {
                                        try {
                                            if (stmtReg != null) {
                                                stmtReg.close();
                                            }
                                            if (connReg != null) {
                                                connReg.close();
                                            }
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    }
                                }

                                break;
        
                            case "LOGIN":
                                String username = (String) in.readObject();
                                String password = (String) in.readObject();

                                //Handle the login
                                String resLog = authenticateUser(username, password);
        
                                if (resLog.equals("OK")) {
                                    out.writeObject("OK");
                                    out.flush();
        
                                    Connection conn = null;
                                    PreparedStatement stmt = null;
        
                                    try {
                                        conn = connect();
                                        String selectQuery = "SELECT * FROM peers WHERE username = ?";
                                        stmt = conn.prepareStatement(selectQuery);
                                        
                                        //Get the user's port
                                        try {
                                            stmt.setString(1, username);
                                            ResultSet rs = stmt.executeQuery();
                            
                                            if (rs.next()) {
                                                int storedPort = rs.getInt("port");
                                                out.writeObject(storedPort);
                                                out.flush();
                                            }
                                        } finally {
                                            try {
                                                if (stmt != null) {
                                                    stmt.close();
                                                }
                                                if (conn != null) {
                                                    conn.close();
                                                }
                                            } catch (Exception e) {
                                                e.printStackTrace();
                                            }
                                        }
                                    } catch (Exception e){
                                        e.printStackTrace();
                                    }
                                } else if (resLog.equals("WRONG")) {
                                    out.writeObject("WRONG");
                                    out.flush();
                                } else {
                                    out.writeObject("ERROR");
                                    out.flush();
                                }
        
                                break;
        
                            case "GETPEER":
                                String peerUsername = (String) in.readObject();
                                
                                //Get peer info
                                String peerIp = getPeerIp(peerUsername);
                                int peerPort = getPeerPort(peerUsername);
                                byte[] peerCert = getPeerCert(peerUsername);

                                if (peerIp.equals("NOTFOUND") || peerPort == -1 || peerCert == null) {
                                    out.writeObject("NOTFOUND");
                                    out.flush();
                                }
                                else {
                                    out.writeObject("OK");
                                    out.flush();

                                    out.writeObject(peerIp);
                                    out.flush();

                                    out.writeObject(peerPort);
                                    out.flush();
                                    
                                    out.writeObject(peerCert);
                                    out.flush();
                                }
        
                                break;

                            case "SAVEMSGS":
                                String peerId = (String) in.readObject();
                                HashMap<String, LinkedList<String>> convs = (HashMap<String, LinkedList<String>>) in.readObject();
                                HashMap<String, Integer> unreadSave = (HashMap<String, Integer>) in.readObject();
                                byte[] map = null;
                                byte[] mapUnread = null;

                                try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
                                    ObjectOutputStream outMap = new ObjectOutputStream(byteOut)) {

                                    outMap.writeObject(convs);
                                    map = byteOut.toByteArray();
                                }

                                try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
                                    ObjectOutputStream outMap = new ObjectOutputStream(byteOut)) {

                                    outMap.writeObject(unreadSave);
                                    mapUnread = byteOut.toByteArray();
                                }

                                //Save history of messages and unread message count in the database
                                Connection conn = null;
                                PreparedStatement stmt = null;
                                String insertQuery = "INSERT OR REPLACE INTO messages (username, msgs, unread) VALUES (?, ?, ?)";

                                try {
                                    conn = connect();
                                    stmt = conn.prepareStatement(insertQuery);
    
                                    stmt.setString(1, peerId);
                                    stmt.setBytes(2, map);
                                    stmt.setBytes(3, mapUnread);
                            
                                    stmt.executeUpdate();

                                    System.out.println("Messages saved.");

                                } catch (Exception e) {
                                    e.printStackTrace();
                                } finally {
                                    try {
                                        if (stmt != null) {
                                            stmt.close();
                                        }
                                        if (conn != null) {
                                            conn.close();
                                        }
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
        
                                break;
                            case "LOADMSGS":
                                String peerLoad = (String) in.readObject();
                                byte[] mapLoad = null;
                                byte[] mapUnreadLoad = null;
                            
                                Connection connLoad = null;
                                PreparedStatement stmtLoad = null;
                                ResultSet rs = null;
                                String selectQuery = "SELECT msgs, unread FROM messages WHERE username = ?";

                                //Deserialize the byte array back to HashMap
                                HashMap<String, LinkedList<String>> convsLoad = new HashMap<String, LinkedList<String>>();
                                HashMap<String, Integer> unreadLoad = new HashMap<String, Integer>();
                            
                                try {
                                    connLoad = connect();
                                    stmtLoad = connLoad.prepareStatement(selectQuery);
                                    stmtLoad.setString(1, peerLoad);
                                    rs = stmtLoad.executeQuery();
                            
                                    if (rs.next()) {
                                        mapLoad = rs.getBytes("msgs");
                                        mapUnreadLoad = rs.getBytes("unread");

                                        //Send the message history
                                        try (ByteArrayInputStream byteIn = new ByteArrayInputStream(mapLoad);
                                                ObjectInputStream inMap = new ObjectInputStream(byteIn)) {
                            
                                            convsLoad = (HashMap<String, LinkedList<String>>) inMap.readObject();

                                            out.writeObject("OK");
                                            out.flush();

                                            out.writeObject(convsLoad);
                                            out.flush();
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }

                                        //Send the unread message count
                                        try (ByteArrayInputStream byteIn = new ByteArrayInputStream(mapUnreadLoad);
                                                ObjectInputStream inMap = new ObjectInputStream(byteIn)) {
                            
                                            unreadLoad = (HashMap<String, Integer>) inMap.readObject();

                                            out.writeObject(unreadLoad);
                                            out.flush();
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }

                                        if (rs != null) {
                                            rs.close();
                                        }

                                        if (stmtLoad != null) {
                                            stmtLoad.close();
                                        }

                                        byte[] unreadUpdateLoad = null;

                                        try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
                                            ObjectOutputStream outMap = new ObjectOutputStream(byteOut)) {

                                            outMap.writeObject(new HashMap<String, Integer>());
                                            unreadUpdateLoad = byteOut.toByteArray();
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }

                                        //Delete unread count, to be updated in the next app iteration
                                        String updateQueryLoad = "UPDATE messages SET unread = ? WHERE username = ?";

                                        try {
                                            stmtLoad = connLoad.prepareStatement(updateQueryLoad);
                                            stmtLoad.setBytes(1, unreadUpdateLoad);
                                            stmtLoad.setString(2, peerLoad);
                                        } catch (SQLException e) {
                                            e.printStackTrace();
                                        }
                                    } else {
                                        out.writeObject("NOK");
                                        out.flush();

                                        System.out.println("No messages found for user: " + peerLoad);
                                    }
                            
                                } catch (Exception e) {
                                    e.printStackTrace();
                                } finally {
                                    try {
                                        if (rs != null) {
                                            rs.close();
                                        }

                                        if (stmtLoad != null) {
                                            stmtLoad.close();
                                        }

                                        if (connLoad != null) {
                                            connLoad.close();
                                        }
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                            
                                break;

                            case "ADDOFFLINE":
                                Message msg = (Message) in.readObject();
                                byte[] mapOff = null;
                                byte[] unreadMapOff = null;
                            
                                Connection connOff = null;
                                PreparedStatement stmtOff = null;
                                ResultSet rsOff = null;
                                String selectQueryOff = "SELECT msgs, unread FROM messages WHERE username = ?";
                                HashMap<String, LinkedList<Message>> convsOff = null;
                                LinkedList<Message> myMsgs = null;

                                HashMap<String, Integer> unreadOff = null;
                                
                                try {
                                    connOff = connect();
                                    stmtOff = connOff.prepareStatement(selectQueryOff);
                                    stmtOff.setString(1, "offline:" + msg.getRecipient());
                                    rsOff = stmtOff.executeQuery();
                            
                                    if (rsOff.next()) {
                                        //Update the offline conversation history of the user
                                        mapOff = rsOff.getBytes("msgs");

                                        try (ByteArrayInputStream byteIn = new ByteArrayInputStream(mapOff);
                                                ObjectInputStream inMap = new ObjectInputStream(byteIn)) {
                            
                                            convsOff = (HashMap<String, LinkedList<Message>>) inMap.readObject();
                                        }

                                        myMsgs = convsOff.get(msg.getSender());

                                        if (myMsgs == null) {
                                            myMsgs = new LinkedList<Message>();
                                            myMsgs.add(msg);
                                        }
                                        else 
                                        {
                                            myMsgs.add(msg);
                                        }

                                        convsOff.put(msg.getSender(), myMsgs);

                                        //Update the offline unread message count of the user
                                        unreadMapOff = rsOff.getBytes("unread");

                                        try (ByteArrayInputStream byteIn = new ByteArrayInputStream(unreadMapOff);
                                                ObjectInputStream inMap = new ObjectInputStream(byteIn)) {
                            
                                            unreadOff = (HashMap<String, Integer>) inMap.readObject();
                                        }

                                        int count = 0;

                                        if (unreadOff.get(msg.getSender()) != null) {
                                            count = unreadOff.get(msg.getSender());
                                        }

                                        unreadOff.put(msg.getSender(), count + 1);
                                    }
                                    else 
                                    {
                                        //If it is the first time, start count at 1 and add message to an empty list
                                        convsOff = new HashMap<String, LinkedList<Message>>();
                                        myMsgs = new LinkedList<Message>();
                                        unreadOff = new HashMap<String, Integer>();

                                        myMsgs.add(msg);
                                        convsOff.put(msg.getSender(), myMsgs);
                                        unreadOff.put(msg.getSender(), 1);
                                    }
                            
                                } catch (Exception e) {
                                    e.printStackTrace();
                                } finally {
                                    try {
                                        if (rsOff != null) {
                                            rsOff.close();
                                        }
                                        if (stmtOff != null) {
                                            stmtOff.close();
                                        }
                                        if (connOff != null) {
                                            connOff.close();
                                        }
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }

                                //Update the entry in the database (new entry called offline:username)
                                String insertQueryOff = "INSERT OR REPLACE INTO messages (username, msgs, unread) VALUES (?, ?, ?)";

                                try {
                                    byte[] mapInsert = null;

                                    try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
                                        ObjectOutputStream outMap = new ObjectOutputStream(byteOut)) {

                                        outMap.writeObject(convsOff);
                                        mapInsert = byteOut.toByteArray();
                                    }

                                    byte[] unreadInsert = null;

                                    try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
                                        ObjectOutputStream outMap = new ObjectOutputStream(byteOut)) {

                                        outMap.writeObject(unreadOff);
                                        unreadInsert = byteOut.toByteArray();
                                    }

                                    connOff = connect();
                                    stmtOff = connOff.prepareStatement(insertQueryOff);

                                    stmtOff.setString(1, "offline:" + msg.getRecipient());
                                    stmtOff.setBytes(2, mapInsert);
                                    stmtOff.setBytes(3, unreadInsert);
                            
                                    stmtOff.executeUpdate();

                                    System.out.println("Offline messages saved.");

                                } catch (Exception e) {
                                    e.printStackTrace();
                                } finally {
                                    try {
                                        if (rsOff != null) {
                                            rsOff.close();
                                        }
                                        if (stmtOff != null) {
                                            stmtOff.close();
                                        }
                                        if (connOff != null) {
                                            connOff.close();
                                        }
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
        
                                break;
                                
                            case "LOADOFFLINE":
                                String peerLoadOff = (String) in.readObject();
                                byte[] getMapLoadOff = null;
                                byte[] getUnreadLoadOff = null;
                            
                                Connection connLoadOff = null;
                                PreparedStatement stmtLoadOff = null;
                                ResultSet rsLoadOff = null;
                                String selectQueryLoadOff = "SELECT msgs, unread FROM messages WHERE username = ?";
                            
                                try {
                                    connLoadOff = connect();
                                    stmtLoadOff = connLoadOff.prepareStatement(selectQueryLoadOff);
                                    stmtLoadOff.setString(1, "offline:" + peerLoadOff);
                                    rsLoadOff = stmtLoadOff.executeQuery();
                            
                                    if (rsLoadOff.next()) {
                                        getMapLoadOff = rsLoadOff.getBytes("msgs");
                            
                                        //Deserialize the byte array back to HashMap
                                        HashMap<String, LinkedList<Message>> convsLoadOff;

                                        try (ByteArrayInputStream byteIn = new ByteArrayInputStream(getMapLoadOff);
                                                ObjectInputStream inMap = new ObjectInputStream(byteIn)) {
                            
                                            convsLoadOff = (HashMap<String, LinkedList<Message>>) inMap.readObject();

                                            out.writeObject("OK");
                                            out.flush();

                                            out.writeObject(convsLoadOff);
                                            out.flush();
                                        }

                                        getUnreadLoadOff = rsLoadOff.getBytes("unread");
                            
                                        //Deserialize the byte array back to HashMap
                                        HashMap<String, Integer> unreadLoadOff;

                                        try (ByteArrayInputStream byteIn = new ByteArrayInputStream(getUnreadLoadOff);
                                                ObjectInputStream inMap = new ObjectInputStream(byteIn)) {
                            
                                            unreadLoadOff = (HashMap<String, Integer>) inMap.readObject();

                                            out.writeObject(unreadLoadOff);
                                            out.flush();
                                        }

                                        if (stmtLoadOff != null) {
                                            stmtLoadOff.close();
                                        }

                                        //Delete old offline entry, as the messages were merged with the conversation history
                                        String deleteQuery = "DELETE FROM messages WHERE username = ?";

                                        stmtLoadOff = connLoadOff.prepareStatement(deleteQuery);

                                        stmtLoadOff.setString(1, "offline:" + peerLoadOff);
                                        stmtLoadOff.executeUpdate();

                                    } else {
                                        out.writeObject("NOK");
                                        out.flush();

                                        System.out.println("No offline messages found for user: " + peerLoadOff);
                                    }
                            
                                } catch (Exception e) {
                                    e.printStackTrace();
                                } finally {
                                    try {
                                        if (rsLoadOff != null) {
                                            rsLoadOff.close();
                                        }
                                        if (stmtLoadOff != null) {
                                            stmtLoadOff.close();
                                        }
                                        if (connLoadOff != null) {
                                            connLoadOff.close();
                                        }
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                            
                                break;

                            default:
                                System.out.println("Unknown command: " + clientMessage);
        
                                break;
                        }
                    }
                } catch (EOFException e) {
                    // Client has disconnected
                    System.out.println("Client disconnected: " + sslSocket.getInetAddress() + ":" + sslSocket.getPort());
                    break;
                } catch (IOException e) {
                    System.out.println("Client disconnected: " + sslSocket.getInetAddress() + ":" + sslSocket.getPort());
                    break;
                } catch (ClassNotFoundException e) {
                    System.out.println("Client disconnected: " + sslSocket.getInetAddress() + ":" + sslSocket.getPort());
                }
            }
        } catch (IOException e) {
            System.out.println("Error in client handler: " + e.getMessage());
        }
    }

    //Connect to the database
    public static Connection connect() throws SQLException {
        if (conn == null || conn.isClosed()) {
            conn = DriverManager.getConnection(DB_URL);
        }
        return conn;
    }

    //Save the user in the database
    private String saveUserInDatabase(String username, String hashedPassword, byte[] salt, String ip, int port, byte[] cert) {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
    
        try {
            conn = connect();

            // Check if the user already exists
            String checkUserQuery = "SELECT username FROM peers WHERE username = ?";
            stmt = conn.prepareStatement(checkUserQuery);
            stmt.setString(1, username);
            rs = stmt.executeQuery();
    
            if (rs.next()) {
                return "EXISTS";
            }
    
            String insertQuery = "INSERT INTO peers (username, password, salt, ip, port, cert) VALUES (?, ?, ?, ?, ?, ?)";
            stmt.close();
            stmt = conn.prepareStatement(insertQuery);
    
            stmt.setString(1, username);
            stmt.setString(2, hashedPassword);
            stmt.setBytes(3, salt);
            stmt.setString(4, ip); // Same machine
            stmt.setInt(5, port);
            stmt.setBytes(6, cert);
    
            stmt.executeUpdate();

            return "OK";
        } catch (Exception e) {
            System.out.println("Error while updating peer information: " + e.getMessage());
            e.printStackTrace();
            return "ERROR";
        } finally {
            try {
                if (rs != null) {
                    rs.close();
                }
                if (stmt != null) {
                    stmt.close();
                }
                if (conn != null) {
                    conn.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    //Authenticate user
    private String authenticateUser(String username, String password) {
        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            conn = connect();
            String selectQuery = "SELECT * FROM peers WHERE username = ?";
            stmt = conn.prepareStatement(selectQuery);

            try {
                stmt.setString(1, username);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    byte[] salt = rs.getBytes("salt");
                    String storedPassword = rs.getString("password");

                    //Verify with stored encrypted password
                    boolean res = EncryptionUtil.verifyPassword(password, storedPassword, salt);

                    if (res) {
                        return "OK";
                    }
                    else {
                        return "WRONG";
                    }
                }
                else 
                {
                    return "WRONG";
                }
            } finally {
                try {
                    if (stmt != null) {
                        stmt.close();
                    }
                    if (conn != null) {
                        conn.close();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    return "ERROR";
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR";
        }
    }

    //Retrieve the peer IP
    private static String getPeerIp(String username) {
        String selectQuery = "SELECT ip FROM peers WHERE username = ?";
        String ip = "";
        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            conn = connect();
            stmt = conn.prepareStatement(selectQuery);
            stmt.setString(1, username);

            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                ip = rs.getString("ip");
            } else {
                System.out.println("No peer found with the username: " + username);
                return "NOTFOUND";
            }

        } catch (SQLException e) {
            System.out.println("Error while fetching port: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
                if (conn != null) {
                    conn.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return ip;
    }

    //Retrieve the peer port
    private static int getPeerPort(String username) {
        String selectQuery = "SELECT port FROM peers WHERE username = ?";
        int port = -1;
        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            conn = connect();
            stmt = conn.prepareStatement(selectQuery);
            stmt.setString(1, username);

            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                port = rs.getInt("port");
            } else {
                System.out.println("No peer found with the username: " + username);
                return -1;
            }

        } catch (SQLException e) {
            System.out.println("Error while fetching port: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
                if (conn != null) {
                    conn.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return port;
    }

    //Retrieve the peer's public key certificate
    private static byte[] getPeerCert(String username) {
        String selectQuery = "SELECT cert FROM peers WHERE username = ?";
        byte[] cert = null;
        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            conn = connect();
            stmt = conn.prepareStatement(selectQuery);
            stmt.setString(1, username);

            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                cert = rs.getBytes("cert");
            } else {
                System.out.println("No peer found with the username: " + username);
                return null;
            }

        } catch (SQLException e) {
            System.out.println("Error while fetching cert: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
                if (conn != null) {
                    conn.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return cert;
    }
}

