package com.peerapp;

import javax.net.ssl.*;
import java.io.*;
import java.net.InetAddress;
import java.net.SocketException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class SSLServer {

    private static String DB_URL = null;
    private static Connection conn;
    private static SSLServerSocket svSocket;
    private static int PORT = -1;

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
    
    private static final String CREATE_GROUPS_TABLE_SQL = 
        "CREATE TABLE IF NOT EXISTS groups (" +
        "topic TEXT NOT NULL UNIQUE, " +
        "members BLOB NOT NULL);";
    
    private static final String CREATE_GROUPS_MESSAGES_TABLE_SQL = 
        "CREATE TABLE IF NOT EXISTS groupmsgs (" +
        "username TEXT NOT NULL UNIQUE, " +
        "msgs BLOB NOT NULL, " +
        "unread BLOB NOT NULL);";

    private static final String CREATE_PEER_ORDER_TABLE_SQL = 
        "CREATE TABLE IF NOT EXISTS peerorder (" +
        "username TEXT NOT NULL UNIQUE, " +
        "orderlist BLOB NOT NULL);";
    
    public static void main(String[] args) {
        initializeDatabase();
        initializeServerSocket();
        listenForConnections();
    }
    
    private static void initializeDatabase() {
        // Get PORT from user input
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("Enter the port number for the server (1024-65535): ");

            try {
                String input = scanner.nextLine().trim();
                PORT = Integer.parseInt(input);

                // Validate that the port is in the valid range (1024 to 65535)
                if (PORT >= 1024 && PORT <= 65535) {
                    break; // Valid port, exit loop
                } else {
                    System.out.println("Invalid port number. Please enter a number between 1024 and 65535.");
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a numeric value.");
            }
        }

        scanner.close();

        DB_URL = "jdbc:sqlite:peers" + PORT + ".db";

        try (Connection conn = connect(); Statement stmt = conn.createStatement()) {
            stmt.execute(CREATE_PEER_TABLE_SQL);
            stmt.execute(CREATE_MESSAGE_TABLE_SQL);
            stmt.execute(CREATE_GROUPS_TABLE_SQL);
            stmt.execute(CREATE_GROUPS_MESSAGES_TABLE_SQL);
            stmt.execute(CREATE_PEER_ORDER_TABLE_SQL);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void initializeServerSocket() {
        try {
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

            crashRecovery();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void crashRecovery() {
        try {
            for (String sv : readIpPortPairsFromFile("serverAddresses.txt")) {
                String[] ipPort = sv.split(":");
                String ip = ipPort[0];
                int port = Integer.parseInt(ipPort[1]);

                KeyStore trustStore = KeyStore.getInstance("JKS");
                try (FileInputStream keystoreStream = new FileInputStream("truststores/server_truststore.jks")) {
                    trustStore.load(keystoreStream, "serverpass".toCharArray());
                }

                X509Certificate server_cert = loadCertificate("certs/server_cert.cer");
                trustStore.setCertificateEntry(sv, server_cert);

                try (FileOutputStream fos = new FileOutputStream("truststores/server_truststore.jks")) {
                    trustStore.store(fos, "serverpass".toCharArray());
                }

                TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                trustManagerFactory.init(trustStore);

                SSLContext sslContextSv = SSLContext.getInstance("TLS");
                sslContextSv.init(null, trustManagerFactory.getTrustManagers(), null);

                SSLSocketFactory sslSocketFactory = sslContextSv.getSocketFactory();

                try (SSLSocket sock = (SSLSocket) sslSocketFactory.createSocket(ip, port);
                     ObjectOutputStream out = new ObjectOutputStream(sock.getOutputStream())) {

                    System.out.println("Asking for help: " + ip + ":" + port);
                    out.writeObject("IMBACK");
                    out.flush();
                    break;
                } catch (IOException e) {
                    System.out.println("Failed to synchronize with server or not crashed: " + ip + ":" + port);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void listenForConnections() {
        try {
            while (true) {
                try {
                    SSLSocket socket = (SSLSocket) svSocket.accept();
                    System.out.println("Client connected: " + socket.getInetAddress() + ":" + socket.getPort());
                    new Thread(new ClientHandler(socket, PORT)).start();
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

    //Load a certificate from a file
    private static X509Certificate loadCertificate(String certFilePath) throws Exception {
        try (FileInputStream certInput = new FileInputStream(certFilePath)) {
            CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
            return (X509Certificate) certFactory.generateCertificate(certInput);
        } catch (CertificateException e) {
            throw new Exception("Failed to load certificate: " + e.getMessage(), e);
        }
    }

    //Read server addresses from file
    private static List<String> readIpPortPairsFromFile(String fileName) throws IOException {
        List<String> ipPortPairs = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
            String line;

            while ((line = br.readLine()) != null) {
                ipPortPairs.add(line.trim());
            }
        }

        ipPortPairs.remove(InetAddress.getLocalHost().getHostAddress() + ":" + PORT);
        ipPortPairs.remove("localhost:" + PORT);

        return ipPortPairs;
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
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}

class ClientHandler implements Runnable {
    private final SSLSocket sslSocket;
    private static String DB_URL = null;
    private static String DB_FILE = null;
    private static Connection conn;
    private static int PORT;

    private static Map<String, List<SecretSharing.Share>> shares = new HashMap<String, List<SecretSharing.Share>>();

    public ClientHandler(SSLSocket sslSocket, int port) {
        this.sslSocket = sslSocket;
        DB_URL = "jdbc:sqlite:peers" + port + ".db";
        DB_FILE = "peers" + port + ".db";
        PORT = port;
    }

    @Override
    public void run() {
        try (ObjectInputStream in = new ObjectInputStream(sslSocket.getInputStream());
             ObjectOutputStream out = new ObjectOutputStream(sslSocket.getOutputStream())) {

            out.flush();

            String clientMessage;

            //Handle user requests until dying
            while (true) {
                try {
                    clientMessage = (String) in.readObject();
                    
                    if (clientMessage != null) {
                        switch (clientMessage) {
                            case "GETSHARE":
                                String usernameGet = (String) in.readObject();
                                List<SecretSharing.Share> shareGet = shares.get(usernameGet);

                                out.writeObject(shareGet);
                                out.flush();

                                out.reset();

                                break;

                            case "SHARE":
                                String usernameShare = (String) in.readObject();
                                SecretSharing.Share shareMod = (SecretSharing.Share) in.readObject();
                                SecretSharing.Share sharePriv = (SecretSharing.Share) in.readObject();

                                List<SecretSharing.Share> sharesList = new ArrayList<SecretSharing.Share>();
                                sharesList.add(shareMod);
                                sharesList.add(sharePriv);

                                shares.put(usernameShare, sharesList);

                                break;

                            case "IMBACK":
                                //Synchronize with the server that died
                                synchronizeDB();

                                break;

                            case "SYNC":
                                //Receive the database file
                                receiveFile(in, DB_FILE);

                                break;

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

                                    try {
                                        connReg = connect();
                                        stmtReg = conn.prepareStatement(insertQueryReg);

                                        byte[] mapReg = serialize(convsReg);
                                        byte[] mapUnreadReg = serialize(unreadReg);
        
                                        stmtReg.setString(1, user);
                                        stmtReg.setBytes(2, mapReg);
                                        stmtReg.setBytes(3, mapUnreadReg);
                                
                                        stmtReg.executeUpdate();

                                        System.out.println("Message placeholder saved.");

                                        synchronizeDB();

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
                            
                            case "GETALLPEERS":
                                String getAllUsername = (String) in.readObject();
                                String allQuery = "SELECT orderlist FROM peerorder WHERE username = ?";

                                //Save history of messages and unread message count in the database
                                Connection getAllConn = null;
                                PreparedStatement stmtAllCon = null;

                                try {
                                    getAllConn = connect();
                                    stmtAllCon = getAllConn.prepareStatement(allQuery);

                                    stmtAllCon.setString(1, getAllUsername);
                            
                                    ResultSet rsAllCon = stmtAllCon.executeQuery();

                                    if (rsAllCon.next()) {
                                        byte[] allConOrderBytes = rsAllCon.getBytes("orderlist");

                                        List<String> allConOrder = (List<String>) deserialize(allConOrderBytes);

                                        out.writeObject(allConOrder);
                                        out.flush();
                                    }
                                    else {
                                        out.writeObject(getAllPeerUsernames());
                                        out.flush();
                                    }

                                } catch (Exception e) {
                                    e.printStackTrace();
                                } finally {
                                    try {
                                        if (stmtAllCon != null) {
                                            stmtAllCon.close();
                                        }
                                        if (getAllConn != null) {
                                            getAllConn.close();
                                        }
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
        
                                break;

                            case "GETGROUPS":
                                String myGroupsUsernameAll = (String) in.readObject();

                                List<String> groupsAll = new ArrayList<>();
                                String sqlAll = "SELECT topic, members FROM groups";

                                Connection connGetGroups = null;
                                PreparedStatement stmtGetGroups = null;

                                try {
                                    connGetGroups = connect();
                                    stmtGetGroups = connGetGroups.prepareStatement(sqlAll);

                                    ResultSet rs = stmtGetGroups.executeQuery();

                                    while (rs.next()) {
                                        List<String> members = deserialize(rs.getBytes("members"));
                                        
                                        if (!members.contains(myGroupsUsernameAll)) {
                                            groupsAll.add(rs.getString("topic"));
                                        }
                                    }
                                } catch (SQLException e) {
                                    e.printStackTrace();
                                } finally {
                                    try {
                                        if (stmtGetGroups != null) {
                                            stmtGetGroups.close();
                                        }

                                        if (connGetGroups != null) {
                                            connGetGroups.close();
                                        }
                                    } catch (SQLException e) {
                                        e.printStackTrace();
                                    }
                                }

                                out.writeObject(groupsAll);
                                out.flush();

                                break;

                            case "GETMYGROUPS":
                                String myGroupsUsername = (String) in.readObject();

                                List<String> groups = new ArrayList<>();
                                String sql = "SELECT topic, members FROM groups";

                                Connection connMyGroups = null;
                                PreparedStatement stmtMyGroups = null;

                                try {
                                    connMyGroups = connect();
                                    stmtMyGroups = connMyGroups.prepareStatement(sql);
                                    ResultSet rs = stmtMyGroups.executeQuery();

                                    while (rs.next()) {
                                        List<String> members = deserialize(rs.getBytes("members"));
                                        
                                        if (members.contains(myGroupsUsername)) {
                                            groups.add(rs.getString("topic"));
                                        }
                                    }
                                } catch (SQLException e) {
                                    e.printStackTrace();
                                } finally {
                                    try {
                                        if (stmtMyGroups != null) {
                                            stmtMyGroups.close();
                                        }

                                        if (connMyGroups != null) {
                                            connMyGroups.close();
                                        }
                                    } catch (SQLException e) {
                                        e.printStackTrace();
                                    }
                                }

                                out.writeObject(groups);
                                out.flush();

                                break;

                            case "SAVEPEERSORDER":
                                String peerIdSaveOrder = (String) in.readObject();
                                List<String> saveOrder = (List<String>) in.readObject();

                                byte[] saveOrderbytes = serialize(saveOrder);

                                //Save history of messages and unread message count in the database
                                Connection connSaveOrder = null;
                                PreparedStatement stmtSaveOrder = null;
                                String insertQuerySaveOrder = "INSERT OR REPLACE INTO peerorder (username, orderlist) VALUES (?, ?)";

                                try {
                                    connSaveOrder = connect();
                                    stmtSaveOrder = connSaveOrder.prepareStatement(insertQuerySaveOrder);

                                    stmtSaveOrder.setString(1, peerIdSaveOrder);
                                    stmtSaveOrder.setBytes(2, saveOrderbytes);
                            
                                    stmtSaveOrder.executeUpdate();

                                    System.out.println("Order saved.");
                                } catch (Exception e) {
                                    e.printStackTrace();
                                } finally {
                                    try {
                                        if (stmtSaveOrder != null) {
                                            stmtSaveOrder.close();
                                        }
                                        if (connSaveOrder != null) {
                                            connSaveOrder.close();
                                        }
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }

                                break;

                            case "SAVEMSGS":
                                String peerId = (String) in.readObject();
                                HashMap<String, LinkedList<String>> convs = (HashMap<String, LinkedList<String>>) in.readObject();
                                HashMap<String, Integer> unreadSave = (HashMap<String, Integer>) in.readObject();

                                byte[] map = serialize(convs);
                                byte[] mapUnread = serialize(unreadSave);

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
                            
                            case "SAVEMSGSGROUPS":
                                String peerIdGSave = (String) in.readObject();
                                HashMap<String, LinkedList<String>> convsGSave = (HashMap<String, LinkedList<String>>) in.readObject();
                                HashMap<String, Integer> unreadSaveGSave = (HashMap<String, Integer>) in.readObject();

                                byte[] mapGSave = serialize(convsGSave);
                                byte[] mapUnreadGSave = serialize(unreadSaveGSave);

                                //Save history of messages and unread message count in the database
                                Connection connGSave = null;
                                PreparedStatement stmtGSave = null;
                                String insertQueryGSave = "INSERT OR REPLACE INTO groupmsgs (username, msgs, unread) VALUES (?, ?, ?)";

                                try {
                                    connGSave = connect();
                                    stmtGSave = connGSave.prepareStatement(insertQueryGSave);

                                    stmtGSave.setString(1, peerIdGSave);
                                    stmtGSave.setBytes(2, mapGSave);
                                    stmtGSave.setBytes(3, mapUnreadGSave);
                            
                                    stmtGSave.executeUpdate();

                                    System.out.println("Messages saved.");

                                    synchronizeDB();

                                } catch (Exception e) {
                                    e.printStackTrace();
                                } finally {
                                    try {
                                        if (stmtGSave != null) {
                                            stmtGSave.close();
                                        }
                                        if (connGSave != null) {
                                            connGSave.close();
                                        }
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }

                                break;

                            case "LOADMSGS":
                                String peerLoad = (String) in.readObject();
                            
                                Connection connLoad = null;
                                PreparedStatement stmtLoad = null;
                                ResultSet rs = null;
                                String selectQuery = "SELECT msgs, unread FROM messages WHERE username = ?";
                            
                                try {
                                    connLoad = connect();
                                    stmtLoad = connLoad.prepareStatement(selectQuery);
                                    stmtLoad.setString(1, peerLoad);
                                    rs = stmtLoad.executeQuery();
                            
                                    if (rs.next()) {
                                        byte[] mapLoad = rs.getBytes("msgs");
                                        byte[] mapUnreadLoad = rs.getBytes("unread");

                                        //Deserialize the byte array back to HashMap
                                        HashMap<String, LinkedList<String>> convsLoad = (HashMap<String, LinkedList<String>>) deserialize(mapLoad);

                                        out.writeObject("OK");
                                        out.flush();

                                        out.writeObject(convsLoad);
                                        out.flush();

                                        HashMap<String, Integer> unreadLoad = (HashMap<String, Integer>) deserialize(mapUnreadLoad);

                                        out.writeObject(unreadLoad);
                                        out.flush();

                                        byte[] unreadUpdateLoad = serialize(new HashMap<String, Integer>());

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

                            case "LOADMSGSGROUPS":
                                String peerLoadGLoad = (String) in.readObject();
                                
                                Connection connLoadGLoad = null;
                                PreparedStatement stmtLoadGLoad = null;
                                ResultSet rsGLoad = null;
                                String selectQueryGLoad = "SELECT msgs, unread FROM groupmsgs WHERE username = ?";
                            
                                try {
                                    connLoadGLoad = connect();
                                    stmtLoadGLoad = connLoadGLoad.prepareStatement(selectQueryGLoad);
                                    stmtLoadGLoad.setString(1, peerLoadGLoad);
                                    rsGLoad = stmtLoadGLoad.executeQuery();
                            
                                    if (rsGLoad.next()) {
                                        byte[] mapLoadGLoad = rsGLoad.getBytes("msgs");
                                        byte[] mapUnreadLoadGLoad = rsGLoad.getBytes("unread");

                                        //Deserialize the byte array back to HashMap
                                        HashMap<String, LinkedList<String>> convsLoadGLoad = (HashMap<String, LinkedList<String>>) deserialize(mapLoadGLoad);

                                        out.writeObject("OK");
                                        out.flush();

                                        out.writeObject(convsLoadGLoad);
                                        out.flush();

                                        HashMap<String, Integer> unreadLoadGLoad = (HashMap<String, Integer>) deserialize(mapUnreadLoadGLoad);

                                        out.writeObject(unreadLoadGLoad);
                                        out.flush();

                                        byte[] unreadUpdateLoadGLoad = serialize(new HashMap<String, Integer>());

                                        //Delete unread count, to be updated in the next app iteration
                                        String updateQueryLoadGLoad = "UPDATE groupmsgs SET unread = ? WHERE username = ?";

                                        try {
                                            stmtLoadGLoad = connLoadGLoad.prepareStatement(updateQueryLoadGLoad);
                                            stmtLoadGLoad.setBytes(1, unreadUpdateLoadGLoad);
                                            stmtLoadGLoad.setString(2, peerLoadGLoad);
                                        } catch (SQLException e) {
                                            e.printStackTrace();
                                        }

                                        synchronizeDB();
                                    } else {
                                        out.writeObject("NOK");
                                        out.flush();

                                        System.out.println("No messages found for user: " + peerLoadGLoad);
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                } finally {
                                    try {
                                        if (rsGLoad != null) {
                                            rsGLoad.close();
                                        }

                                        if (stmtLoadGLoad != null) {
                                            stmtLoadGLoad.close();
                                        }

                                        if (connLoadGLoad != null) {
                                            connLoadGLoad.close();
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
                                LinkedList<Message> myMsgs = null;
                                HashMap<String, Integer> unreadOff = null;

                                HashMap<String, LinkedList<Message>> convsOff = null;
                                
                                try {
                                    connOff = connect();
                                    stmtOff = connOff.prepareStatement(selectQueryOff);
                                    stmtOff.setString(1, "offline:" + msg.getRecipient());
                                    rsOff = stmtOff.executeQuery();
                            
                                    if (rsOff.next()) {
                                        //Update the offline conversation history of the user
                                        mapOff = rsOff.getBytes("msgs");
                                        convsOff = (HashMap<String, LinkedList<Message>>) deserialize(mapOff);

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
                                        unreadOff = (HashMap<String, Integer>) deserialize(unreadMapOff);

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
                                    byte[] mapInsert = serialize(convsOff);
                                    byte[] unreadInsert = serialize(unreadOff);

                                    connOff = connect();
                                    stmtOff = connOff.prepareStatement(insertQueryOff);

                                    stmtOff.setString(1, "offline:" + msg.getRecipient());
                                    stmtOff.setBytes(2, mapInsert);
                                    stmtOff.setBytes(3, unreadInsert);
                            
                                    stmtOff.executeUpdate();

                                    System.out.println("Offline messages saved.");

                                    synchronizeDB();

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

                            case "ADDOFFLINEGROUP":
                                Message msgAg = (Message) in.readObject();
                                String groupAg = (String) in.readObject();

                                byte[] mapOffAg = null;
                                byte[] unreadMapOffAg = null;
                            
                                Connection connOffAg = null;
                                PreparedStatement stmtOffAg = null;
                                ResultSet rsOffAg = null;
                                String selectQueryOffAg = "SELECT msgs, unread FROM groupmsgs WHERE username = ?";
                                LinkedList<Message> myMsgsAg = null;
                                HashMap<String, Integer> unreadOffAg = null;

                                HashMap<String, LinkedList<Message>> convsOffAg = null;
                                
                                try {
                                    connOffAg = connect();
                                    stmtOffAg = connOffAg.prepareStatement(selectQueryOffAg);
                                    stmtOffAg.setString(1, "offline:" + msgAg.getRecipient());
                                    rsOffAg = stmtOffAg.executeQuery();
                            
                                    if (rsOffAg.next()) {
                                        //Update the offline conversation history of the user
                                        mapOffAg = rsOffAg.getBytes("msgs");
                                        convsOffAg = (HashMap<String, LinkedList<Message>>) deserialize(mapOffAg);

                                        myMsgsAg = convsOffAg.get(groupAg);

                                        if (myMsgsAg == null) {
                                            myMsgsAg = new LinkedList<Message>();
                                            myMsgsAg.add(msgAg);
                                        }
                                        else 
                                        {
                                            myMsgsAg.add(msgAg);
                                        }

                                        convsOffAg.put(groupAg, myMsgsAg);

                                        //Update the offline unread message count of the user
                                        unreadMapOffAg = rsOffAg.getBytes("unread");
                                        unreadOffAg = (HashMap<String, Integer>) deserialize(unreadMapOffAg);

                                        int countAg = 0;

                                        if (unreadOffAg.get(groupAg) != null) {
                                            countAg = unreadOffAg.get(groupAg);
                                        }

                                        unreadOffAg.put(groupAg, countAg + 1);
                                    }
                                    else 
                                    {
                                        //If it is the first time, start count at 1 and add message to an empty list
                                        convsOffAg = new HashMap<String, LinkedList<Message>>();
                                        myMsgsAg = new LinkedList<Message>();
                                        unreadOffAg = new HashMap<String, Integer>();

                                        myMsgsAg.add(msgAg);
                                        convsOffAg.put(groupAg, myMsgsAg);
                                        unreadOffAg.put(groupAg, 1);
                                    }
                            
                                } catch (Exception e) {
                                    e.printStackTrace();
                                } finally {
                                    try {
                                        if (rsOffAg != null) {
                                            rsOffAg.close();
                                        }
                                        if (stmtOffAg != null) {
                                            stmtOffAg.close();
                                        }
                                        if (connOffAg != null) {
                                            connOffAg.close();
                                        }
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }

                                //Update the entry in the database (new entry called offline:username)
                                String insertQueryOffAg = "INSERT OR REPLACE INTO groupmsgs (username, msgs, unread) VALUES (?, ?, ?)";

                                try {
                                    byte[] mapInsertAg = serialize(convsOffAg);
                                    byte[] unreadInsertAg = serialize(unreadOffAg);

                                    connOffAg = connect();
                                    stmtOffAg = connOffAg.prepareStatement(insertQueryOffAg);

                                    stmtOffAg.setString(1, "offline:" + msgAg.getRecipient());
                                    stmtOffAg.setBytes(2, mapInsertAg);
                                    stmtOffAg.setBytes(3, unreadInsertAg);
                            
                                    stmtOffAg.executeUpdate();

                                    System.out.println("Offline messages saved.");

                                    synchronizeDB();

                                } catch (Exception e) {
                                    e.printStackTrace();
                                } finally {
                                    try {
                                        if (rsOffAg != null) {
                                            rsOffAg.close();
                                        }
                                        if (stmtOffAg != null) {
                                            stmtOffAg.close();
                                        }
                                        if (connOffAg != null) {
                                            connOffAg.close();
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
                                        HashMap<String, LinkedList<Message>> convsLoadOff = (HashMap<String, LinkedList<Message>>) deserialize(getMapLoadOff);

                                        out.writeObject("OK");
                                        out.flush();

                                        out.writeObject(convsLoadOff);
                                        out.flush();

                                        getUnreadLoadOff = rsLoadOff.getBytes("unread");
                            
                                        //Deserialize the byte array back to HashMap
                                        HashMap<String, Integer> unreadLoadOff = (HashMap<String, Integer>) deserialize(getUnreadLoadOff);

                                        out.writeObject(unreadLoadOff);
                                        out.flush();

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

                            case "LOADOFFLINEGROUP":
                                String peerLoadOffLg = (String) in.readObject();
                                byte[] getMapLoadOffLg = null;
                                byte[] getUnreadLoadOffLg = null;
                            
                                Connection connLoadOffLg = null;
                                PreparedStatement stmtLoadOffLg = null;
                                ResultSet rsLoadOffLg = null;
                                String selectQueryLoadOffLg = "SELECT msgs, unread FROM groupmsgs WHERE username = ?";
                            
                                try {
                                    connLoadOffLg = connect();
                                    stmtLoadOffLg = connLoadOffLg.prepareStatement(selectQueryLoadOffLg);
                                    stmtLoadOffLg.setString(1, "offline:" + peerLoadOffLg);
                                    rsLoadOffLg = stmtLoadOffLg.executeQuery();
                            
                                    if (rsLoadOffLg.next()) {
                                        getMapLoadOffLg = rsLoadOffLg.getBytes("msgs");
                            
                                        //Deserialize the byte array back to HashMap
                                        HashMap<String, LinkedList<Message>> convsLoadOffLg = (HashMap<String, LinkedList<Message>>) deserialize(getMapLoadOffLg);

                                        out.writeObject("OK");
                                        out.flush();

                                        out.writeObject(convsLoadOffLg);
                                        out.flush();

                                        getUnreadLoadOffLg = rsLoadOffLg.getBytes("unread");
                            
                                        //Deserialize the byte array back to HashMap
                                        HashMap<String, Integer> unreadLoadOffLg = (HashMap<String, Integer>) deserialize(getUnreadLoadOffLg);

                                        out.writeObject(unreadLoadOffLg);
                                        out.flush();

                                        //Delete old offline entry, as the messages were merged with the conversation history
                                        String deleteQueryLg = "DELETE FROM groupmsgs WHERE username = ?";

                                        stmtLoadOffLg = connLoadOffLg.prepareStatement(deleteQueryLg);

                                        stmtLoadOffLg.setString(1, "offline:" + peerLoadOffLg);
                                        stmtLoadOffLg.executeUpdate();

                                        synchronizeDB();

                                    } else {
                                        out.writeObject("NOK");
                                        out.flush();

                                        System.out.println("No offline messages found for user: " + peerLoadOffLg);
                                    }
                            
                                } catch (Exception e) {
                                    e.printStackTrace();
                                } finally {
                                    try {
                                        if (rsLoadOffLg != null) {
                                            rsLoadOffLg.close();
                                        }
                                        if (stmtLoadOffLg != null) {
                                            stmtLoadOffLg.close();
                                        }
                                        if (connLoadOffLg != null) {
                                            connLoadOffLg.close();
                                        }
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }
                                
                                break;
                            
                            case "CREATEGROUP":
                                String topic = (String) in.readObject();
                                String usernameCreateGroup = (String) in.readObject();

                                String resCreateGroup = createGroup(topic);

                                if (resCreateGroup.equals("OK")) {
                                    out.writeObject("OK");
                                    out.flush();

                                    Connection connRegJoinGroup = null;
                                    PreparedStatement stmtRegJoinGroup = null;
                                    String insertQueryRegJoinGroup = "INSERT OR REPLACE INTO groupmsgs (username, msgs, unread) VALUES (?, ?, ?)";
                                    HashMap<String, LinkedList<String>> convsRegJoinGroup = new HashMap<String, LinkedList<String>>();
                                    HashMap<String, Integer> unreadRegJoinGroup = new HashMap<String, Integer>();

                                    //Insert placeholders for unread messages and message history
                                    try {
                                        connRegJoinGroup = connect();
                                        stmtRegJoinGroup = connRegJoinGroup.prepareStatement(insertQueryRegJoinGroup);

                                        byte[] mapRegJoinGroup = serialize(convsRegJoinGroup);
                                        byte[] mapUnreadRegJoinGroup = serialize(unreadRegJoinGroup);
        
                                        stmtRegJoinGroup.setString(1, usernameCreateGroup);
                                        stmtRegJoinGroup.setBytes(2, mapRegJoinGroup);
                                        stmtRegJoinGroup.setBytes(3, mapUnreadRegJoinGroup);
                                
                                        stmtRegJoinGroup.executeUpdate();

                                        System.out.println("Group meessage placeholder saved.");

                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    } finally {
                                        try {
                                            if (stmtRegJoinGroup != null) {
                                                stmtRegJoinGroup.close();
                                            }
                                            if (connRegJoinGroup != null) {
                                                connRegJoinGroup.close();
                                            }
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                        }
                                    }

                                    joinGroup(topic, usernameCreateGroup);
                                } else if (resCreateGroup.equals("EXISTS")) {
                                    out.writeObject("EXISTS");
                                    out.flush();
                                } else {
                                    out.writeObject("ERROR");
                                    out.flush();
                                }

                                synchronizeDB();
                            
                                break;

                            case "JOINGROUP":
                                String topicJoin = (String) in.readObject();
                                String usernameJoin = (String) in.readObject();

                                String resJoinGroup = joinGroup(topicJoin, usernameJoin);

                                if (resJoinGroup.equals("OK")) {
                                    out.writeObject("OK");
                                    out.flush();
                                } else if (resJoinGroup.equals("NOTFOUND")) {
                                    out.writeObject("NOTFOUND");
                                    out.flush();
                                } else if (resJoinGroup.equals("ALREADYIN")) {
                                    out.writeObject("ALREADYIN");
                                    out.flush();
                                } else {
                                    out.writeObject("ERROR");
                                    out.flush();
                                }

                                synchronizeDB();

                                break;

                            case "GETGROUPMEMBERS":
                                String topicMembers = (String) in.readObject();

                                List<String> members = getGroupMembers(topicMembers);

                                if (members != null) {
                                    out.writeObject("OK");
                                    out.flush();

                                    out.writeObject(members);
                                    out.flush();
                                } else {
                                    out.writeObject("NOTFOUND");
                                    out.flush();
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
                } catch (Exception e) {
                    System.out.println("Client disconnected: " + sslSocket.getInetAddress() + ":" + sslSocket.getPort());
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //Connect to the database
    public static Connection connect() throws SQLException {
        if (conn == null || conn.isClosed()) {
            conn = DriverManager.getConnection(DB_URL);
        }
        return conn;
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

    public static List<String> getAllPeerUsernames() {
        List<String> usernames = new ArrayList<>();
        String query = "SELECT username FROM peers";

        try (Connection conn = connect(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                usernames.add(rs.getString("username"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return usernames;
    }

    private void synchronizeDB() {
        try {
            for (String sv : readIpPortPairsFromFile("serverAddresses.txt")) {
                String[] ipPort = sv.split(":");
                String ip = ipPort[0];
                int port = Integer.parseInt(ipPort[1]);
        
                KeyStore trustStore = KeyStore.getInstance("JKS");
                try (FileInputStream keystoreStream = new FileInputStream("truststores/server_truststore.jks")) {
                    trustStore.load(keystoreStream, "serverpass".toCharArray());
                }
        
                // Get the server certificate
                X509Certificate server_cert = loadCertificate("certs/server_cert.cer");
        
                // Store the server's certificate as trusted by default in the user's truststore
                trustStore.setCertificateEntry(sv, server_cert);
        
                // Save the truststore file in the folder
                try (FileOutputStream fos = new FileOutputStream("truststores/server_truststore.jks")) {
                    trustStore.store(fos, "serverpass".toCharArray());
                }
        
                TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                trustManagerFactory.init(trustStore);
        
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
        
                SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

                SSLSocket sock = null;
                ObjectOutputStream out = null;
        
                try {
                    sock = (SSLSocket) sslSocketFactory.createSocket(ip, port);
                    out = new ObjectOutputStream(sock.getOutputStream());
                        
                    System.out.println("Synchronizing with server: " + ip + ":" + port);

                    out.writeObject("SYNC");
                    out.flush();
        
                    // Send the database file
                    sendFile(DB_FILE, out);
        
                } catch (IOException e) {
                    System.out.println("Failed to synchronize with server: " + ip + ":" + port);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendFile(String filePath, ObjectOutputStream out) throws IOException {
        File file = new File(filePath);
        long fileSize = file.length();
    
        // Send the file size first
        out.writeLong(fileSize);
        out.flush();

        BufferedInputStream bis = null;
    
        try {
            bis = new BufferedInputStream(new FileInputStream(file));
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = bis.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }

            out.flush();
            System.out.println("DB File sent successfully.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void receiveFile(ObjectInputStream in, String destPath) throws IOException {
        BufferedOutputStream bos = null;

        try {
            bos = new BufferedOutputStream(new FileOutputStream(destPath));
            long fileSize = in.readLong();
            byte[] buffer = new byte[4096];
            int bytesRead;
            long totalBytesRead = 0;
    
            while (totalBytesRead < fileSize && (bytesRead = in.read(buffer, 0, (int)Math.min(buffer.length, fileSize - totalBytesRead))) != -1) {
                bos.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
            }
            
            bos.flush();
            System.out.println("DB File received successfully.");
        } catch (SocketException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
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

        ipPortPairs.remove(InetAddress.getLocalHost().getHostAddress() + ":" + PORT);
        ipPortPairs.remove("localhost:" + PORT);

        return ipPortPairs;
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

    public static String createGroup(String topic) {
        //See if already exists
        String sql = "SELECT topic FROM groups WHERE topic = ?";

        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, topic);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return "EXISTS";
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR";
        }

        //Create the group
        sql = "INSERT INTO groups(topic, members) VALUES(?, ?)";

        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, topic);
            pstmt.setBytes(2, serialize(new ArrayList<String>()));
            pstmt.executeUpdate();

            return "OK";
        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR";
        }
    }

    public static String joinGroup(String topic, String member) {
        String sql = "SELECT members FROM groups WHERE topic = ?";

        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, topic);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                List<String> members = deserialize(rs.getBytes("members"));

                if (members.contains(member)) {
                    return "ALREADYIN";
                }

                members.add(member);

                String updateSql = "UPDATE groups SET members = ? WHERE topic = ?";

                try (PreparedStatement updatePstmt = conn.prepareStatement(updateSql)) {
                    updatePstmt.setBytes(1, serialize(members));
                    updatePstmt.setString(2, topic);
                    updatePstmt.executeUpdate();
                }

                return "OK";
            }
            else {
                return "NOTFOUND";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR";
        }
    }

    public static List<String> getGroupMembers(String topic) {
        String sql = "SELECT members FROM groups WHERE topic = ?";

        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, topic);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                List<String> members = deserialize(rs.getBytes("members"));
                return members;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    private static byte[] serialize(Object obj) {
        try (ByteArrayOutputStream b = new ByteArrayOutputStream();
             ObjectOutputStream o = new ObjectOutputStream(b)) {
            o.writeObject(obj);
            return b.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static <T> T deserialize(byte[] bytes) {
        try (ByteArrayInputStream b = new ByteArrayInputStream(bytes);
             ObjectInputStream o = new ObjectInputStream(b)) {
            return (T) o.readObject();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}

