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
import javafx.animation.Timeline;
import javafx.util.Duration;

public class PeerController {
    @FXML private TextField messageField;
    @FXML private TextField searchField;
    @FXML private Button sendButton;
    @FXML private Button startChatButton;
    @FXML private ScrollPane messagesArea;
    @FXML private VBox messagesVBox;
    @FXML private ListView<String> contactsListView;
    @FXML private Label currentUserIdLabel;
    @FXML private ProgressIndicator searchProgress;
    @FXML private Label searchStatusLabel;

    private Peer peer;
    private ObservableList<String> connectedPeers = FXCollections.observableArrayList();
    private FilteredList<String> filteredPeers;
    private Map<String, List<Message>> encryptedMessageCache = new HashMap<>();
    private Timeline searchDebouncer;
    private final Duration SEARCH_DEBOUNCE_TIME = Duration.millis(500);
    private String activeConversationId;

    public void initialize(String userId, int port, String password) {
        try {
            configureMessageArea();
            initializeContactsList();
            initializeSearch();
            
            // Start peer
            peer = new Peer(userId, password, port, this);
            
            // Configure message handling
            configureMessageHandling();
            
            // Setup start chat button
            startChatButton.setOnAction(event -> startNewConversation());
                
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
        
        // Auto-scroll to bottom when new messages are added
        messagesVBox.heightProperty().addListener((observable, oldValue, newValue) -> 
            messagesArea.setVvalue(1.0));
    }
    
    private void initializeContactsList() {
        filteredPeers = new FilteredList<>(connectedPeers);
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
        
        searchDebouncer = new Timeline(
            new javafx.animation.KeyFrame(SEARCH_DEBOUNCE_TIME, event -> performSearch())
        );
        searchDebouncer.setCycleCount(1);
        
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            searchDebouncer.stop();
            searchDebouncer.playFromStart();
            
            filteredPeers.setPredicate(peerId -> {
                if (newValue == null || newValue.isEmpty()) {
                    return true;
                }
                return peerId.toLowerCase().contains(newValue.toLowerCase());
            });
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
    
    private void startNewConversation() {
        String recipient = searchField.getText().trim();

        if (!recipient.isEmpty()) {
            if (!connectedPeers.contains(recipient)) {
                // Add recipient to the contact list and update the list view
                updatePeerList(recipient);
            }
            // Set the active conversation to the selected user
            activeConversationId = recipient;
            
            // Open the chat window for this user
            loadEncryptedMessages(recipient);
        } else {
            addErrorMessage("Please enter a user ID to start a conversation.");
        }
    }
    
    private void startConversationWithPeer(String peerId) {
    	// Set the active conversation to the selected user
    	activeConversationId = peerId;
    	currentUserIdLabel.setText("Chatting with: " + peerId);
    	
    	// Load the conversation history for this user
    	loadEncryptedMessages(peerId);
    }
    
    private void configureMessageHandling() {
        // messageField.setOnAction(event -> sendSecureMessage());
        // searchField.setOnAction(event -> sendSecureMessage());
    	sendButton.setOnAction(event -> sendSecureMessage());	
    }
    
    public void updatePeerList(String peerId) {
        Platform.runLater(() -> {
            if (!connectedPeers.contains(peerId)) {
                connectedPeers.add(peerId);
                searchProgress.setVisible(false);
                searchStatusLabel.setText("Found peer: " + peerId);
            }
            contactsListView.refresh();
        });
    }

    private void sendSecureMessage() {
//    	String recipient = searchField.getText().trim();
        String message = messageField.getText().trim();

//        if (!recipient.isEmpty() && !message.isEmpty()) {
//            peer.sendMessage(recipient, message);
//            addMessageBubble("You", message, true);
//            messageField.clear();
//            updatePeerList(recipient);
//        } else {
//            String error = recipient.isEmpty() ? "Please enter a recipient." :
//                          message.isEmpty() ? "Please enter a message." :
//                          "Please enter a recipient and a message.";
//            addErrorMessage(error);
//        }
        if (activeConversationId == null) {
        	addErrorMessage("No conversation selected. Start a chat first.");
        	return;
        }
        
        if (!message.isEmpty()) {
        	peer.sendMessage(activeConversationId, message);
        	addMessageBubble("You", message, true);
        	messageField.clear();
        	updatePeerList(activeConversationId);
        } else {
        	addErrorMessage("Please enter a message.");
        }
    }
    
    public void handleReceivedMessage(Message message) {
    	Platform.runLater(() -> {
    		// Store encrypted message
    		encryptedMessageCache.computeIfAbsent(message.getSender(), k -> new ArrayList<>()).add(message);
    		
    		// Update UI with decrypted content (assuming Peer handles decryption)
    		String decryptedContent = peer.decryptMessage(message);
    		addMessageBubble(message.getSender(), decryptedContent, false);
    		
    		// Update peer list
    		updatePeerList(message.getSender());
    	}); 
    }
    
    private void loadEncryptedMessages(String peerId) {
    	messagesVBox.getChildren().clear();
    	List<Message> messages = encryptedMessageCache.getOrDefault(peerId, new ArrayList<>());
    	for (Message msg : messages) {
    		String decryptedContent = peer.decryptMessage(msg);
    		addMessageBubble(msg.getSender(), decryptedContent, msg.getSender().equals(peer.getUserId()));
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
    
    public void removePeerConnection(String peerId) {
    	Platform.runLater(() -> {
    		connectedPeers.remove(peerId);
    	});
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
    
    public void showError(String error) {
        Platform.runLater(() -> {
            searchProgress.setVisible(false);
            searchStatusLabel.setText("Error: " + error);
            searchStatusLabel.setStyle("-fx-text-fill: red;");
            searchStatusLabel.setVisible(true);
        });
    }
    
    private void showNotification(String sender) {
        System.out.println("New message from: " + sender);
        // Add system notification implementation if desired
    }
}