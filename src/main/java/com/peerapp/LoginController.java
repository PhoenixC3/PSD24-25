package com.peerapp;

import javafx.fxml.FXML;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.text.Text;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.stage.Stage;

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

    private DatabaseUtil userDatabase = new DatabaseUtil();

    @FXML
    public void initialize() {
        loginButton.setOnAction(event -> login());
        registerButton.setOnAction(event -> handleRegister());
    }

    @FXML
    private void login() {
        String username = usernameField.getText();
        String password = passwordField.getText();
        
        if (username.isEmpty() || password.isEmpty()) {
            showAlert("Error", "Both fields must be filled.");
            return;
        }

        try {
            int port = userDatabase.authenticateUser(username, password);
            
            if (port == -1) {
                showAlert("Error", "Unknown user or incorrect password.");
                passwordField.clear();
                return;
            }
            else if (port == -2) {
                showAlert("Error", "An error occurred while trying to log in.");
                System.exit(0);
            }
            else 
            {
                openP2PApp(username, port, password);
            }
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Error", "An error occurred while trying to log in.");
            System.exit(0);
        }
    }

    private void handleRegister() {
        String userId = usernameField.getText().trim();
        String password = passwordField.getText().trim();

        if (userId.isEmpty() || password.isEmpty()) {
            showAlert("Error", "User ID or Password cannot be empty!");
            return;
        }

        String res = userDatabase.registerUser(userId, password);

        if (res.equals("OK")) {
            statusText.setText("Registration successful! Please login.");
        } else if (res.equals("EXISTS")) {
            showAlert("Error", "User already exists.");
            usernameField.clear();
            passwordField.clear();
            return;
        } else {
            showAlert("Error", "Error while registering");
            System.exit(0);
            return;
        }
    }

    private void openP2PApp(String userId, int port, String password) {
        // Close login window
        Stage stage = (Stage) loginButton.getScene().getWindow();
        stage.close();

        // Open the P2P messaging app
        System.setProperty("userId", userId);
        System.setProperty("userPort", Integer.toString(port));
        System.setProperty("userPassword", password);

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