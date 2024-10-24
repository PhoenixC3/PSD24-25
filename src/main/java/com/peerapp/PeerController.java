package com.peerapp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.geometry.Insets;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

public class PeerController {
    @FXML private TextField messageField;
    @FXML private TextField searchField;
    @FXML private Button sendButton;
    @FXML private ScrollPane messagesArea;
    @FXML private VBox messagesVBox;
    @FXML private ListView<String> contactsListView;
    @FXML private Label currentUserIdLabel;
    @FXML private ProgressIndicator searchProgress;
    @FXML private Label searchStatusLabel;
    @FXML private Button searchButton;

    private Peer peer;
    private ObservableList<String> connectedPeers = FXCollections.observableArrayList();
    private FilteredList<String> filteredPeers;
    private Map<String, List<Message>> encryptedMessageCache = new HashMap<>();
    private String activeConversationId;

    public void initialize(String userId, int port, String password) {
        try {
            // Start peer
            peer = new Peer(userId, password, port, this);

            configureMessageArea();
            initializeContactsList();
            initializeSearch();
            configureMessageHandling();
        } catch (Exception e) {
            e.printStackTrace();
            showError("Failed to initialize: " + e.getMessage());
        }
    }
    
    private void configureMessageArea() {
        messagesArea.setFitToWidth(true);
        messagesArea.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        messagesArea.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        
        messagesVBox.setSpacing(10);
        messagesVBox.setPadding(new Insets(10));
        
        messagesVBox.heightProperty().addListener((observable, oldValue, newValue) -> 
            messagesArea.setVvalue(1.0));
    }
    
    private void initializeContactsList() {
        filteredPeers = new FilteredList<>(connectedPeers);
        filteredPeers.remove(peer.getUserId());
        contactsListView.setItems(filteredPeers);
        
        contactsListView.getSelectionModel().selectedItemProperty().addListener(
            (observable, oldValue, newValue) -> {
                if (newValue != null) {
                    startConversationWithPeer(newValue);
                }
            }
        );
        
        contactsListView.setCellFactory(lv -> new ListCell<String>() {
            @Override
            protected void updateItem(String peerId, boolean empty) {
                super.updateItem(peerId, empty);
                if (empty || peerId == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    HBox container = new HBox(10);
                    Label peerLabel = new Label(peerId);
                    Label messageCount = new Label();
                    
                    List<Message> messages = encryptedMessageCache.get(peerId);
                    if (messages != null && !messages.isEmpty()) {
                        messageCount.setText(String.valueOf(messages.size()));
                        messageCount.setStyle("-fx-background-color: #0084FF; -fx-text-fill: white; " +
                                           "-fx-padding: 2 6; -fx-background-radius: 10;");
                    }
                    
                    container.getChildren().addAll(peerLabel, messageCount);
                    setGraphic(container);
                }
            }
        });
    }
    
    private void initializeSearch() {
        searchProgress.setVisible(false);
        searchStatusLabel.setVisible(false);
        
        searchButton.setOnAction(event -> {
            performSearch();
        });
    }
    
    private void performSearch() {
        String searchText = searchField.getText().trim();

        if (!searchText.isEmpty()) {
            searchProgress.setVisible(true);
            searchStatusLabel.setText("Searching...");
            searchStatusLabel.setVisible(true);
            
            peer.getPeerInfo(searchText);
        } else {
            searchProgress.setVisible(false);
            searchStatusLabel.setVisible(false);
        }
    }
    
    private void startConversationWithPeer(String peerId) {
    	activeConversationId = peerId;
    	currentUserIdLabel.setText("Chatting with: " + peerId);
    }
    
    private void configureMessageHandling() {
    	sendButton.setOnAction(event -> sendMessage());	
    }
    
    public void updatePeerList(String peerId) {
        if (peerId != null) {
            Platform.runLater(() -> {
                if (!connectedPeers.contains(peerId)) {
                    connectedPeers.add(peerId);
                    searchProgress.setVisible(false);
                    searchStatusLabel.setText("Found peer: " + peerId);
                }
                contactsListView.refresh();
            });
        }
    }

    private void sendMessage() {
        String message = messageField.getText().trim();

        if (activeConversationId == null) {
        	addErrorMessage("No conversation selected. Start a chat first.");
        	return;
        }
        
        if (!message.isEmpty()) {
        	boolean res = peer.sendMessage(activeConversationId, message);
        	
            if (res) {
                addMessageBubble("You", message, true);
                messageField.clear();
                updatePeerList(activeConversationId);
            } 
            else {
                addMessageBubble("You", "User does not exist", false);
                messageField.clear();
            }
        } else {
        	addErrorMessage("Please enter a message.");
        }
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

    public void appendReceivedMessage(String sender, String message) {
        Platform.runLater(() -> addMessageBubble(sender, message, false));
    }

    public void appendError(String error) {
        Platform.runLater(() -> addErrorMessage(error));
    }

    private void addErrorMessage(String error) {
        Label errorLabel = new Label(error);
        errorLabel.setStyle("-fx-text-fill: red;");
        messagesVBox.getChildren().add(errorLabel);
    }
    
    private void showError(String error) {
        Platform.runLater(() -> {
            searchProgress.setVisible(false);
            searchStatusLabel.setText("Error: " + error);
            searchStatusLabel.setStyle("-fx-text-fill: red;");
            searchStatusLabel.setVisible(true);
        });
    }
}