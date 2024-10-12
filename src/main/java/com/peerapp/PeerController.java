package com.peerapp;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import javax.crypto.SecretKey;
import javax.crypto.KeyGenerator;

public class PeerController {
    @FXML
    private TextField recipientField;
    @FXML
    private Button sendButton;
    @FXML
    private TextArea messagesArea;

    private Peer peer;
    private SecretKey secretKey;

    public void initialize(String userId, int port) {
        try {
            // Generate a secret key for encryption
            KeyGenerator keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(128);
            secretKey = keyGen.generateKey();

            // Start peer
            peer = new Peer(userId, port, secretKey);
            sendButton.setOnAction(event -> sendMessage());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendMessage() {
        String recipient = recipientField.getText().trim(); // Ensure no leading/trailing spaces
        if (!recipient.isEmpty()) {
            String message = "Hello from " + peer.getUserId(); // Default message to be sent
            peer.sendMessage(recipient, message, "localhost");
            messagesArea.appendText("Sent to " + recipient + ": " + message + "\n");
            recipientField.clear();
        } else {
            messagesArea.appendText("Please enter a recipient.\n");
        }
    }
}
