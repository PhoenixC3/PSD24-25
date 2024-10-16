package com.peerapp;

import java.io.*;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;

public class UserDatabase {
    public boolean registerUser(String username, String password) {
        byte[] salt = PasswordUtil.generateSalt();
        Connection conn = null;
        PreparedStatement stmt = null;
        try {
            String hashedPassword = PasswordUtil.hashPassword(password, salt);
            conn = DatabaseUtil.connect();
            String insertQuery = "INSERT INTO peers (username, password, salt, ip, port) VALUES (?, ?, ?, ?, ?)";
            
            stmt = conn.prepareStatement(insertQuery);
            stmt.setString(1, username);
            stmt.setString(2, password); //MUDAR AQ
            stmt.setBytes(3, salt);
            stmt.setString(4, "127.0.0.1");
            stmt.setInt(5, 0);
            stmt.executeUpdate();

            return true;
        } catch (Exception e) {
            System.out.println("Error while updating peer information: " + e.getMessage());
            e.printStackTrace();
            return false;
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
    }

    public boolean authenticateUser(String username, String password) {
        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            conn = DatabaseUtil.connect();
            String selectQuery = "SELECT * FROM peers WHERE username = ?";
            stmt = conn.prepareStatement(selectQuery);

            try {
                stmt.setString(1, username);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    byte[] salt = rs.getBytes("salt");
                    String storedPassword = rs.getString("password");
                    String hashedPassword = PasswordUtil.hashPassword(password, salt);

                    return storedPassword.equals(hashedPassword);
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
        } catch (SQLException | NoSuchAlgorithmException | InvalidKeySpecException e) {
            e.printStackTrace();
        }
        return false;
    }
}
