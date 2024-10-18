package com.peerapp;

import javafx.fxml.FXML;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.text.Text;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.stage.Stage;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class LoginController {
    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Button loginButton;
    @FXML
    private Button registerButton;
    @FXML
    private Text statusText;

    private UserDatabase userDatabase = new UserDatabase();

    @FXML
    public void initialize() {
        loginButton.setOnAction(event -> login());
        registerButton.setOnAction(event -> handleRegister());
    }

    @FXML
    public void login() {
        String username = usernameField.getText();
        String password = passwordField.getText();
        
        if (username.isEmpty() || password.isEmpty()) {
            showAlert("Error", "Both fields must be filled.");
            return;
        }

        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            String query = "SELECT * FROM peers WHERE username = ?";

            conn = DatabaseUtil.connect();
            stmt = conn.prepareStatement(query);
            stmt.setString(1, username);
            ResultSet resultSet = stmt.executeQuery();
            
            if (resultSet.next()) {
                if (userDatabase.authenticateUser(username, password)) {
                    openP2PApp(username);
                }
                else {
                    showAlert("Error", "Incorrect password.");
                }
            } else {
                showAlert("Error", "User does not exist.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Error", "An error occurred while trying to log in.");
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

    private void handleRegister() {
        String userId = usernameField.getText().trim();
        String password = passwordField.getText().trim();

        if (userId.isEmpty() || password.isEmpty()) {
            statusText.setText("User ID or Password cannot be empty!");
            return;
        }

        if (userDatabase.registerUser(userId, password)) {
            statusText.setText("Registration successful! Please login.");
        } else {
            statusText.setText("User already exists.");
        }
    }

    private void openP2PApp(String userId) {
        // Close login window
        Stage stage = (Stage) loginButton.getScene().getWindow();
        stage.close();

        // Open the P2P messaging app
        System.setProperty("userId", userId);

        P2PApp app = new P2PApp();

        try {
            app.start(new Stage());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}