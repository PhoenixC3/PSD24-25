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
    "msgs BLOB NOT NULL);";

    public static void main(String[] args) {
        Connection conn = null;
        Statement stmt = null;

        try {
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
            // Load the keystore
            KeyStore keyStore = KeyStore.getInstance("JKS");
            try (FileInputStream keyStoreInput = new FileInputStream("keystores/server_keystore.jks")) {
                keyStore.load(keyStoreInput, "serverpass".toCharArray());
            }

            // Set up the KeyManagerFactory
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, "serverpass".toCharArray());

            // Set up the SSL context
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), null, null);

            // Create an SSLServerSocketFactory and SSLServerSocket
            SSLServerSocketFactory sslServerSocketFactory = sslContext.getServerSocketFactory();
            svSocket = (SSLServerSocket) sslServerSocketFactory.createServerSocket(PORT);

            System.out.println("Server is listening on port: " + PORT);

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

    private static Connection connect() throws SQLException {
        if (conn == null || conn.isClosed()) {
            conn = DriverManager.getConnection(DB_URL);
        }
        return conn;
    }

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

// ClientHandler class to handle client connections
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
        
                                String resReg = saveUserInDatabase(user, hashedPassword, salt, ip, port, cert);

                                out.writeObject(resReg);
                                out.flush();
                                break;
        
                            case "LOGIN":
                                String username = (String) in.readObject();
                                String password = (String) in.readObject();

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
                                byte[] map = null;

                                try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
                                    ObjectOutputStream outMap = new ObjectOutputStream(byteOut)) {

                                    outMap.writeObject(convs);
                                    map = byteOut.toByteArray();
                                }

                                Connection conn = null;
                                PreparedStatement stmt = null;
                                String insertQuery = "INSERT OR REPLACE INTO messages (username, msgs) VALUES (?, ?)";

                                try {
                                    conn = connect();
                                    stmt = conn.prepareStatement(insertQuery);
    
                                    stmt.setString(1, peerId);
                                    stmt.setBytes(2, map);
                            
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
                            
                                Connection connLoad = null;
                                PreparedStatement stmtLoad = null;
                                ResultSet rs = null;
                                String selectQuery = "SELECT msgs FROM messages WHERE username = ?";
                            
                                try {
                                    connLoad = connect();
                                    stmtLoad = connLoad.prepareStatement(selectQuery);
                                    stmtLoad.setString(1, peerLoad);
                                    rs = stmtLoad.executeQuery();
                            
                                    if (rs.next()) {
                                        mapLoad = rs.getBytes("msgs");
                            
                                        // Deserialize the byte array back to HashMap
                                        HashMap<String, LinkedList<String>> convsLoad;

                                        try (ByteArrayInputStream byteIn = new ByteArrayInputStream(mapLoad);
                                                ObjectInputStream inMap = new ObjectInputStream(byteIn)) {
                            
                                            convsLoad = (HashMap<String, LinkedList<String>>) inMap.readObject();

                                            out.writeObject("OK");
                                            out.flush();

                                            out.writeObject(convsLoad);
                                            out.flush();
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

                                // Get the messages map of recipient
                                byte[] mapOff = null;
                            
                                Connection connOff = null;
                                PreparedStatement stmtOff = null;
                                ResultSet rsOff = null;
                                String selectQueryOff = "SELECT msgs FROM messages WHERE username = ?";
                                HashMap<String, LinkedList<Message>> convsOff = null;
                                LinkedList<Message> myMsgs = null;
                            
                                try {
                                    connOff = connect();
                                    stmtOff = connOff.prepareStatement(selectQueryOff);
                                    stmtOff.setString(1, "offline:" + msg.getRecipient());
                                    rsOff = stmtOff.executeQuery();
                            
                                    if (rsOff.next()) {
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
                                    }
                                    else 
                                    {
                                        convsOff = new HashMap<String, LinkedList<Message>>();
                                        myMsgs = new LinkedList<Message>();

                                        myMsgs.add(msg);
                                        convsOff.put(msg.getSender(), myMsgs);
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

                                String insertQueryOff = "INSERT OR REPLACE INTO messages (username, msgs) VALUES (?, ?)";

                                try {
                                    byte[] mapInsert = null;

                                    try (ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
                                        ObjectOutputStream outMap = new ObjectOutputStream(byteOut)) {

                                        outMap.writeObject(convsOff);
                                        mapInsert = byteOut.toByteArray();
                                    }

                                    connOff = connect();
                                    stmtOff = connOff.prepareStatement(insertQueryOff);

                                    stmtOff.setString(1, "offline:" + msg.getRecipient());
                                    stmtOff.setBytes(2, mapInsert);
                            
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
                            
                                Connection connLoadOff = null;
                                PreparedStatement stmtLoadOff = null;
                                ResultSet rsLoadOff = null;
                                String selectQueryLoadOff = "SELECT msgs FROM messages WHERE username = ?";
                            
                                try {
                                    connLoadOff = connect();
                                    stmtLoadOff = connLoadOff.prepareStatement(selectQueryLoadOff);
                                    stmtLoadOff.setString(1, "offline:" + peerLoadOff);
                                    rsLoadOff = stmtLoadOff.executeQuery();
                            
                                    if (rsLoadOff.next()) {
                                        getMapLoadOff = rsLoadOff.getBytes("msgs");
                            
                                        // Deserialize the byte array back to HashMap
                                        HashMap<String, LinkedList<Message>> convsLoadOff;

                                        try (ByteArrayInputStream byteIn = new ByteArrayInputStream(getMapLoadOff);
                                                ObjectInputStream inMap = new ObjectInputStream(byteIn)) {
                            
                                            convsLoadOff = (HashMap<String, LinkedList<Message>>) inMap.readObject();

                                            out.writeObject("OK");
                                            out.flush();

                                            out.writeObject(convsLoadOff);
                                            out.flush();
                                        }
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

    public static Connection connect() throws SQLException {
        if (conn == null || conn.isClosed()) {
            conn = DriverManager.getConnection(DB_URL);
        }
        return conn;
    }

    private String saveUserInDatabase(String username, String hashedPassword, byte[] salt, String ip, int port, byte[] cert) {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
    
        try {
            conn = connect();
            // Check if user already exists
            String checkUserQuery = "SELECT COUNT(*) FROM peers WHERE username = ?";
            stmt = conn.prepareStatement(checkUserQuery);
            stmt.setString(1, username);
            rs = stmt.executeQuery();
    
            if (rs.next() && rs.getInt(1) > 0) {
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

    // Method to retrieve the port based on the username
    private static String getPeerIp(String username) {
        String selectQuery = "SELECT ip FROM peers WHERE username = ?";
        String ip = "";
        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            conn = connect();
            stmt = conn.prepareStatement(selectQuery);

            // Set the username in the query
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

    // Method to retrieve the ip:port based on the username
    private static int getPeerPort(String username) {
        String selectQuery = "SELECT port FROM peers WHERE username = ?";
        int port = -1;
        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            conn = connect();
            stmt = conn.prepareStatement(selectQuery);

            // Set the username in the query
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

    // Method to retrieve the certificate based on the username
    private static byte[] getPeerCert(String username) {
        String selectQuery = "SELECT cert FROM peers WHERE username = ?";
        byte[] cert = null;
        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            conn = connect();
            stmt = conn.prepareStatement(selectQuery);

            // Set the username in the query
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

