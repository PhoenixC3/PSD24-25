package com.peerapp;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

public class PeerController {
    @FXML
    private TextField recipientField;

    @FXML
    private TextField messageField;

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
        String recipient = recipientField.getText().trim();
        String message = messageField.getText().trim();

        if (recipient.isEmpty() && message.isEmpty()) {
            messagesArea.appendText("Please enter a recipient and a message.\n");
        }
        else if (recipient.isEmpty()) {
            messagesArea.appendText("Please enter a recipient.\n");
        } 
        else if (message.isEmpty()) {
            messagesArea.appendText("Please enter a message.\n");
        }
        else {
            peer.sendMessage(recipient, message);
            messagesArea.appendText("Sent to " + recipient + ": " + message + "\n");
            recipientField.clear();
        }
    }
}
