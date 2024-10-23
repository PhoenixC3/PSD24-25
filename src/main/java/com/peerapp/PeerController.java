package com.peerapp;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.geometry.Insets;
import javafx.scene.layout.HBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.scene.layout.Priority;

public class PeerController {
    @FXML
    private TextField recipientField;

    @FXML
    private TextField messageField;

    @FXML
    private Button sendButton;

    @FXML
    private ScrollPane messagesArea;

    @FXML
    private VBox messagesVBox;

    private Peer peer;

    public void initialize(String userId, int port, String password) {
        try {
            // Configure ScrollPane and VBox
            messagesArea.setFitToWidth(true);
            messagesArea.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            messagesArea.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            
            messagesVBox.setSpacing(10);
            messagesVBox.setPadding(new Insets(10));
            
            // Start peer
            peer = new Peer(userId, password, port, this);
            
            // Configure message field to send on Enter
            messageField.setOnAction(event -> sendMessage());
            sendButton.setOnAction(event -> sendMessage());
            
            // Auto-scroll to bottom when new messages are added
            messagesVBox.heightProperty().addListener((observable, oldValue, newValue) -> 
                messagesArea.setVvalue(1.0));
                
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendMessage() {
        String recipient = recipientField.getText().trim();
        String message = messageField.getText().trim();

        if (!recipient.isEmpty() && !message.isEmpty()) {
            peer.sendMessage(recipient, message);
            addMessageBubble("You", message, true);
            messageField.clear();
        } else {
            String error = recipient.isEmpty() ? "Please enter a recipient." :
                          message.isEmpty() ? "Please enter a message." :
                          "Please enter a recipient and a message.";
            addErrorMessage(error);
        }
    }

    public void appendReceivedMessage(String sender, String message) {
        Platform.runLater(() -> addMessageBubble(sender, message, false));
    }

    public void appendError(String error) {
        Platform.runLater(() -> addErrorMessage(error));
    }

    private void addMessageBubble(String sender, String message, boolean isSent) {
        HBox messageContainer = new HBox(10);
        messageContainer.setPadding(new Insets(5));
        
        TextFlow bubble = new TextFlow();
        Text senderText = new Text(sender + ": ");
        Text messageText = new Text(message);
        
        bubble.getChildren().addAll(senderText, messageText);
        bubble.setPadding(new Insets(8));
        bubble.setStyle(String.format("-fx-background-color: %s; -fx-background-radius: 10;",
                isSent ? "#0084FF" : "#E9ECEF"));
        
        if (isSent) {
            messageContainer.setStyle("-fx-alignment: center-right;");
        }
        
        messageContainer.getChildren().add(bubble);
        HBox.setHgrow(bubble, Priority.ALWAYS);
        
        messagesVBox.getChildren().add(messageContainer);
    }

    private void addErrorMessage(String error) {
        Label errorLabel = new Label(error);
        errorLabel.setStyle("-fx-text-fill: red;");
        messagesVBox.getChildren().add(errorLabel);
    }
}