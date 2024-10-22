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
    public void login() {
        String username = usernameField.getText();
        String password = passwordField.getText();
        
        if (username.isEmpty() || password.isEmpty()) {
            showAlert("Error", "Both fields must be filled.");
            return;
        }

        try {
            int port = userDatabase.authenticateUser(username, password);
            
            if (port != -1) {
                openP2PApp(username, port, password);
            }
            else {
                showAlert("Error", "Incorrect password.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Error", "An error occurred while trying to log in.");
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