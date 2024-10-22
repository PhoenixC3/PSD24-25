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

public class SSLServer {

    private static final int PORT = 8080;
    private static final String DB_URL = "jdbc:sqlite:peers.db";
    private static Connection conn;

    private static final String CREATE_PEER_TABLE_SQL = 
        "CREATE TABLE IF NOT EXISTS peers (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
        "username TEXT NOT NULL UNIQUE, " +
        "password TEXT NOT NULL, " +
        "salt BLOB NOT NULL, " +
        "ip TEXT NOT NULL, " +
        "port INTEGER NOT NULL, " +
        "pubKey BLOB NOT NULL);";

    public static void main(String[] args) {
        Connection conn = null;
        Statement stmt = null;

        try {
            conn = connect();
            stmt = conn.createStatement();
            stmt.execute(CREATE_PEER_TABLE_SQL);
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
                keyStore.load(keyStoreInput, "serverpass".toCharArray()); // Provide keystore password
            }

            // Set up the KeyManagerFactory
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, "serverpass".toCharArray()); // Provide key password

            // Set up the SSL context
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), null, null);

            // Create an SSLServerSocketFactory and SSLServerSocket
            SSLServerSocketFactory sslServerSocketFactory = sslContext.getServerSocketFactory();
            SSLServerSocket sslServerSocket = (SSLServerSocket) sslServerSocketFactory.createServerSocket(PORT);

            // Enable all cipher suites
            sslServerSocket.setEnabledCipherSuites(sslServerSocket.getSupportedCipherSuites());
            System.out.println("SSL Server is listening on port " + PORT);

            // Accept connections in a loop
            while (true) {
                SSLSocket sslSocket = (SSLSocket) sslServerSocket.accept();
                System.out.println("Client connected: " + sslSocket.getInetAddress() + ":" + sslSocket.getPort());

                // Handle the client connection in a separate thread
                new ClientHandler(sslSocket).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static Connection connect() throws SQLException {
        if (conn == null || conn.isClosed()) {
            conn = DriverManager.getConnection(DB_URL);
        }
        return conn;
    }
}

// ClientHandler class to handle client connections
class ClientHandler extends Thread {
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
        
                                saveUserInDatabase(user, hashedPassword, salt, ip, port, cert);
                                break;
        
                            case "LOGIN":
                                String username = (String) in.readObject();
                                String password = (String) in.readObject();
        
                                if (authenticateUser(username, password) == true) {
                                    out.writeObject(true);
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
                                } else {
                                    out.writeObject(false);
                                }
        
                                break;
        
                            case "GETPEER":
                                String peerUsername = (String) in.readObject();
        
                                String peerIp = getPeerIp(peerUsername);
                                int peerPort = getPeerPort(peerUsername);
        
                                out.writeObject(peerIp);
                                out.flush();
                                out.writeObject(peerPort);
                                out.flush();
        
                                break;
        
                            default:
                                out.writeObject("Unknown command: " + clientMessage);
                                out.flush();
        
                                break;
                        }
                    }
                } catch (EOFException e) {
                    break;
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        } finally {
            try {
                sslSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static Connection connect() throws SQLException {
        if (conn == null || conn.isClosed()) {
            conn = DriverManager.getConnection(DB_URL);
        }
        return conn;
    }

    private void saveUserInDatabase(String username, String hashedPassword, byte[] salt, String ip, int port, byte[] cert) {
        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            conn = connect();
            String insertQuery = "INSERT INTO peers (username, password, salt, ip, port, pubKey) VALUES (?, ?, ?, ?, ?, ?)";
            
            stmt = conn.prepareStatement(insertQuery);

            stmt.setString(1, username);
            stmt.setString(2, hashedPassword);
            stmt.setBytes(3, salt);
            stmt.setString(4, ip); //Mesma maquina
            stmt.setInt(5, port);
            stmt.setBytes(6, cert);

            stmt.executeUpdate();
        } catch (Exception e) {
            System.out.println("Error while updating peer information: " + e.getMessage());
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
    }

    private boolean authenticateUser(String username, String password) {
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

                    return EncryptionUtil.verifyPassword(password, storedPassword, salt);
                }
                else 
                {
                    return false;
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
                    return false;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return false;
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

    // Method to retrieve the port based on the username
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
}

