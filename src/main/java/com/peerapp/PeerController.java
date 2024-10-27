package com.peerapp;

import java.net.BindException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
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
    private Map<String, List<ChatMessage>> messageHistory = new HashMap<>();
    private Map<String, Integer> unreadMessageCounts = new HashMap<String, Integer>();
    private String activeConversationId;
    private HashMap<String, LinkedList<String>> convs = new HashMap<String, LinkedList<String>>();

    public void initialize(String userId, int port, String password) {
        try {
            peer = new Peer(userId, password, port, this);

            configureMessageArea();
            initializeContactsList();
            initializeSearch();
            configureMessageHandling();
            getMessages();

            currentUserIdLabel.setText("Your ID: " + userId);

        } catch (BindException e) {
            showAlert("Error", "Logged in from another location.");
            System.exit(0);
        }
        catch (Exception e) {
            e.printStackTrace();
            showError("Failed to initialize: " + e.getMessage());
        }
    }
    
    private void getMessages() {
        HashMap<String, LinkedList<String>> convsGet = peer.loadMessageHistory();

        if (convsGet != null) {
            this.convs = convsGet;
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

        messageField.setOnAction(event -> sendMessage());
    }
    
    private void initializeContactsList() {
        filteredPeers = new FilteredList<>(connectedPeers);
        contactsListView.setItems(filteredPeers);
        
        contactsListView.getSelectionModel().selectedItemProperty().addListener(
            (observable, oldValue, newValue) -> {
                if (newValue != null) {
                    startConversationWithPeer(newValue);
                    unreadMessageCounts.put(newValue, 0);
                    refreshContactsList();
                }
            }
        );
        
        contactsListView.setCellFactory(lv -> new ListCell<String>() {
            @Override
        /*     protected void updateItem(String peerId, boolean empty) {
                super.updateItem(peerId, empty);
                if (empty || peerId == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    HBox container = new HBox(10);
                    Label peerLabel = new Label(peerId);
                    Label messageCount = new Label();
                    
                    List<MessageBubble> messages = messageHistory.get(peerId);
                    if (messages != null && !messages.isEmpty()) {
                        messageCount.setText(String.valueOf(messages.size()));
                        messageCount.setStyle("-fx-background-color: #0084FF; -fx-text-fill: white; " +
                                            "-fx-padding: 2 6; -fx-background-radius: 10;");
                    }
                    
                    container.getChildren().addAll(peerLabel, messageCount);
                    setGraphic(container);
                }
            } */
            protected void updateItem(String peerId, boolean empty) {
                super.updateItem(peerId, empty);
                if (empty || peerId == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    HBox container = new HBox(10);
                    container.setAlignment(Pos.CENTER_LEFT);
                    
                    Label peerLabel = new Label(peerId);
                    container.getChildren().add(peerLabel);
                    
                    Integer unreadCount = unreadMessageCounts.getOrDefault(peerId, 0);
                    if (unreadCount > 0) {
                        Label badge = new Label(String.valueOf(unreadCount));
                        badge.setStyle(
                            "-fx-background-color: #0084FF;" +
                            "-fx-text-fill: white;" +
                            "-fx-padding: 2 6;" +
                            "-fx-background-radius: 10;"
                        );
                        container.getChildren().add(badge);
                    }
                    
                    setGraphic(container);
                }
            }
        });
    }
    
    private void initializeSearch() {
        searchProgress.setVisible(false);
        searchStatusLabel.setVisible(false);
        
        searchButton.setOnAction(event -> performSearch());
    }

    private LinkedList<String> getConversationMessages(String recipient) {
        return convs.get(recipient);
    }

    private void addMessageToConv(String message, String recipient) {
        LinkedList<String> list = convs.get(recipient);

        if (list == null) {
            list = new LinkedList<String>();

            list.add(message);
            convs.put(recipient, list);
        }
        else 
        {
            list.add(message);
            convs.put(recipient, list);
        }
    }
    
    private void performSearch() {
        String searchText = searchField.getText().trim();

        if (!searchText.isEmpty()) {
            searchProgress.setVisible(true);
            searchStatusLabel.setText("Searching...");
            searchStatusLabel.setVisible(true);
            
            peer.getPeerInfo(searchText);
        }
    }
    
    private void startConversationWithPeer(String peerId) {
        activeConversationId = peerId;
    	currentUserIdLabel.setText("Chatting with: " + peerId);

        messagesVBox.getChildren().clear();

        LinkedList<String> conv = getConversationMessages(peerId);

        if (conv != null) {
            for (String msg : conv) {
                if (msg.startsWith("Me: ")) {
                    String actualMsg = msg.substring(4);
    
                    displayMessageBubble(peer.getUserId(), actualMsg, true);
                }
                else
                {
                    String actualMsg = msg.substring(7);
    
                    displayMessageBubble(peerId, actualMsg, false);
                }
            }
        }
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
            });
        }
        else 
        {
            PauseTransition delay = new PauseTransition(javafx.util.Duration.seconds(3));

            delay.setOnFinished(event -> {
                searchProgress.setVisible(false);
                searchStatusLabel.setText("No peers found");
            });
            
            delay.play();
        }
    }

    private void sendMessage() {
        String message = messageField.getText().trim();

        if (activeConversationId == null) {
        	addErrorMessage("No conversation selected. Start a chat first.");
        	return;
        }
        
        if (!message.isEmpty()) {
        	boolean sent = peer.sendMessage(activeConversationId, message);
        	
            if (sent) {
                messageHistory.computeIfAbsent(activeConversationId, k -> new ArrayList<>())
                            .add(new ChatMessage(peer.getUserId(), message, true));
                
                displayMessageBubble(peer.getUserId(), message, true);
                  
                messageField.clear();
                updatePeerList(activeConversationId);

                addMessageToConv("Me: " + message, activeConversationId);
            } 
            else {
                addErrorMessage("Failed to send message: User does not exist");
                messageField.clear();
            }
        } else {
        	addErrorMessage("Please enter a message.");
        }
    }

    private void displayMessageBubble(String senderId, String content, boolean isSent) {
        HBox messageContainer = new HBox(10);
        messageContainer.setPadding(new Insets(5));
        
        TextFlow bubble = new TextFlow();
        Text senderText = new Text(isSent ? "You: " : senderId + ": ");
        Text messageText = new Text(content);
        
        bubble.getChildren().addAll(senderText, messageText);
        bubble.setPadding(new Insets(8));
        bubble.setStyle(String.format("-fx-background-color: %s; -fx-background-radius: 10;",
                isSent ? "#0084FF" : "#E9ECEF"));
        
        if (isSent) {
            messageContainer.setAlignment(Pos.CENTER_RIGHT);
            senderText.setStyle("-fx-fill: white;");
            messageText.setStyle("-fx-fill: white;");
        } else {
            messageContainer.setAlignment(Pos.CENTER_LEFT);
        }
        
        messageContainer.getChildren().add(bubble);
        HBox.setHgrow(bubble, Priority.ALWAYS);
        
        messagesVBox.getChildren().add(messageContainer);
    }

    public void addToConnected(String sender) {
        if (!connectedPeers.contains(sender)) {
            connectedPeers.add(sender);
        }
    }

    public void appendReceivedMessage(String sender, String content) {
        Platform.runLater(() -> {
            messageHistory.computeIfAbsent(sender, k -> new ArrayList<>())
                        .add(new ChatMessage(sender, content, false));

            if (!connectedPeers.contains(sender)) {
                connectedPeers.add(sender);
            }

            addMessageToConv("Other: " + content, sender);

            if (activeConversationId != null && activeConversationId.equals(sender)) {
                displayMessageBubble(sender, content, false);
            } else {
                unreadMessageCounts.merge(sender, 1, Integer::sum);
                refreshContactsList();
            }
        });
    }

    public void updateOfflineMsgCount(String sender) {
        unreadMessageCounts.merge(sender, 1, Integer::sum);
        refreshContactsList();
    }

    private void refreshContactsList() {
        contactsListView.refresh();
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

    private void showAlert(String title, String message) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public void saveMessages() {
        peer.saveMessageHistory(convs);
    }
}