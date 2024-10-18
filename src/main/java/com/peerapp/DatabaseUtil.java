package com.peerapp;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class DatabaseUtil {
    private static final String DB_URL = "jdbc:sqlite:peers.db";
    private static Connection conn;

    // Singleton pattern to get the same connection
    public static Connection connect() throws SQLException {
        if (conn == null || conn.isClosed()) {
            conn = DriverManager.getConnection(DB_URL);
        }
        return conn;
    }

    // Close connection at the end of the application
    public static void closeConnection() {
        try {
            if (conn != null && !conn.isClosed()) {
                conn.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    public static void updatePeerPort(String username, int newPort) {
        String updateQuery = "UPDATE peers SET port = ? WHERE username = ?";
        Connection conn = null;
        PreparedStatement stmt = null;
    
        try {
            conn = connect();
            conn.setAutoCommit(false);  // Start transaction
            stmt = conn.prepareStatement(updateQuery);
            stmt.setInt(1, newPort);
            stmt.setString(2, username);
    
            int affectedRows = stmt.executeUpdate();
            if (affectedRows > 0) {
                System.out.println("Peer information updated successfully.");
            } else {
                System.out.println("No peer found with the username: " + username);
            }
            conn.commit();  // Commit transaction
    
        } catch (SQLException e) {
            System.out.println("Error while updating peer information: " + e.getMessage());
            e.printStackTrace();
            if (conn != null) {
                try {
                    conn.rollback();  // Rollback on error
                } catch (SQLException rollbackEx) {
                    rollbackEx.printStackTrace();
                }
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
    }
    

    // Method to retrieve the port based on the username
    public static String getPeerIp(String username) {
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
    public static int getPeerPort(String username) {
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
