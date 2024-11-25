package com.peerapp;

import java.net.BindException;
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
    @FXML private Button createGroupButton;
    @FXML private Button joinGroupButton;
    @FXML private ListView<String> groupsListView;
    @FXML private TextField topicField;
    @FXML private Label groupStatusLabel;

    private Peer peer;

    //Peers that have already sent messages
    private ObservableList<String> connectedPeers = FXCollections.observableArrayList();

    //Peers after search
    private FilteredList<String> filteredPeers;

    //To use when unread messages
    private Map<String, List<ChatMessage>> messageHistory = new HashMap<>();

    //To create the bubble for unread messages
    private HashMap<String, Integer> unreadMessageCounts = new HashMap<String, Integer>();

    //To create the bubble for unread messages
    private HashMap<String, Integer> unreadMessageCountsGroup = new HashMap<String, Integer>();

    //User that we are currently chatting with
    private String activeConversationId;

    //Message history
    private HashMap<String, LinkedList<String>> convs = new HashMap<String, LinkedList<String>>();

    //Message history groups
    private HashMap<String, LinkedList<String>> convsGroups = new HashMap<String, LinkedList<String>>();

    public void initialize(String userId, int port, String password) {
        try {
            //Create peer object
            peer = new Peer(userId, password, port, this);

            configureMessageArea();
            initializeContactsList();
            initializeGroupsList();
            initializeSearch();
            configureMessageHandling();
            configureGroupFunctions();
            getMessages();
            getMessageCounts();

            currentUserIdLabel.setText("Your ID: " + userId);

        } catch (BindException e) {
            showAlert("Error", "Logged in from another location.");
            System.exit(0);
        }
        catch (Exception e) {
            e.printStackTrace();
            showError("Failed to initialize: " + e.getMessage());
            System.exit(0);
        }
    }

    private void configureGroupFunctions() {
        createGroupButton.setOnAction(event -> createGroup());
        joinGroupButton.setOnAction(event -> joinGroup());
    }

    private void createGroup() {
        String groupTopic = topicField.getText();

        if (groupTopic.isEmpty()) {
            showErrorGroup("Group topic cannot be empty.");
            return;
        }

        peer.createGroup(groupTopic);
    }

    private void joinGroup() {
        String groupTopic = topicField.getText();

        if (groupTopic.isEmpty()) {
            showErrorGroup("Group topic cannot be empty.");
            return;
        }

        peer.joinGroup(groupTopic);
    }

    public void updateGroupList(String topic) {
        Platform.runLater(() -> groupsListView.getItems().add(topic));
    }

    //Get message history (persistent) and fill the left side contact history list
    private void getMessages() {
        HashMap<String, LinkedList<String>> convsGet = peer.loadMessageHistory();

        if (convsGet != null) {
            this.convs = convsGet;

            for (String key : convsGet.keySet()) {
                addToConnected(key);
            }
        }
    }

    private void getMessageCounts() {
        unreadMessageCounts = peer.getMessageCounts();
    }

    //Visual specs and send message button configuration
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
    
    //Configure contacts list
    private void initializeContactsList() {
        //Already connected peers appear on the left side
        filteredPeers = new FilteredList<>(connectedPeers);
        contactsListView.setItems(filteredPeers);
        
        //On clicking another user's name we start the conversation
        contactsListView.getSelectionModel().selectedItemProperty().addListener(
            (observable, oldValue, newValue) -> {
                if (newValue != null) {
                    startConversationWithPeer(newValue);

                    //Reset the unread message count
                    unreadMessageCounts.put(newValue, 0);
                    refreshContactsList();

                    // Deselect the groups list
                    groupsListView.getSelectionModel().clearSelection();
                }
            }
        );
        
        //Visual updating stuff
        contactsListView.setCellFactory(lv -> new ListCell<String>() {
            @Override
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

    //Configure contacts list
    private void initializeGroupsList() {
        
        //On clicking another groups name we start the conversation
        groupsListView.getSelectionModel().selectedItemProperty().addListener(
            (observable, oldValue, newValue) -> {
                if (newValue != null) {
                    startConversationWithGroup(newValue);

                    //Reset the unread message count
                    unreadMessageCountsGroup.put(newValue, 0);
                    refreshGroupsList();

                    // Deselect the contacts list
                    contactsListView.getSelectionModel().clearSelection();
                }
            }
        );
        
        //Visual updating stuff
        groupsListView.setCellFactory(lv -> new ListCell<String>() {
            @Override
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
                    
                    Integer unreadCount = unreadMessageCountsGroup.getOrDefault(peerId, 0);
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

    private void startConversationWithGroup(String groupId) {
        activeConversationId = groupId;
        currentUserIdLabel.setText("Chatting in group: " + groupId);
    
        messagesVBox.getChildren().clear();

        // Get the history of messages of this conversation
        LinkedList<String> convGroups = getConversationMessagesGroups(groupId);

        // Display the message history (Each message is marked to separate your sent messages from the other person's sent messages)
        if (convGroups != null) {
            for (String msg : convGroups) {
                if (msg.startsWith("Me: ")) {
                    String actualMsg = msg.substring(4);
                    displayMessageBubble(peer.getUserId(), actualMsg, true);
                } else {
                    String actualMsg = msg.substring(7);
                    displayMessageBubble(groupId, actualMsg, false);
                }
            }
        }
    }
    
    //Configure search button
    private void initializeSearch() {
        searchProgress.setVisible(false);
        searchStatusLabel.setVisible(false);
        
        searchButton.setOnAction(event -> performSearch());
    }

    //Get (local but persistent) message history for a certain conversation
    private LinkedList<String> getConversationMessages(String recipient) {
        return convs.get(recipient);
    }

    //Get (local but persistent) message history for a certain conversation
    private LinkedList<String> getConversationMessagesGroups(String group) {
        return convsGroups.get(group);
    }

    //Add message to a conversation's history (local but persistent)
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

    //Add message to a conversation's history (local but persistent)
    private void addMessageToConvGroup(String message, String group) {
        LinkedList<String> list = convsGroups.get(group);

        if (list == null) {
            list = new LinkedList<String>();

            list.add(message);
            convsGroups.put(group, list);
        }
        else 
        {
            list.add(message);
            convsGroups.put(group, list);
        }
    }
    
    //Search for a user to chat with
    private void performSearch() {
        String searchText = searchField.getText().trim();

        if (!searchText.isEmpty()) {
            searchProgress.setVisible(true);
            searchStatusLabel.setText("Searching...");
            searchStatusLabel.setVisible(true);
            
            peer.getPeerInfo(searchText);
        }
    }
    
    //Enter a conversation tab with a certain user
    private void startConversationWithPeer(String peerId) {
        activeConversationId = peerId;
    	currentUserIdLabel.setText("Chatting with: " + peerId);

        messagesVBox.getChildren().clear();

        //Get the history of messages of this conversation
        LinkedList<String> conv = getConversationMessages(peerId);

        //Display the message history (Each message is marked to separate your sent messages from the other person's sent messages)
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
    
    //Configure the send message button
    private void configureMessageHandling() {
    	sendButton.setOnAction(event -> sendMessage());	
    }
    
    //Save the peer that we messaged in the left side (if not found, search for 3 seconds and then print message because it looks good)
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

    //Send a message
    private void sendMessage() {
        String message = messageField.getText().trim();

        if (activeConversationId == null) {
        	addErrorMessage("No conversation selected. Start a chat first.");
        	return;
        }
        
        if (!message.isEmpty()) {
        	boolean sent;

            if (isGroupConversation(activeConversationId)) {
                peer.sendGroupMessage(activeConversationId, message);

                //Still send the message if the user is not viewing the conversation
                messageHistory.computeIfAbsent(activeConversationId, k -> new ArrayList<>())
                .add(new ChatMessage(peer.getUserId(), message, true));
    
                displayMessageBubble(peer.getUserId(), message, true);
                
                messageField.clear();
            } else {
                sent = peer.sendMessage(activeConversationId, message);

                if (sent) {
                    //Still send the message if the user is not viewing the conversation
                    messageHistory.computeIfAbsent(activeConversationId, k -> new ArrayList<>())
                                .add(new ChatMessage(peer.getUserId(), message, true));
                    
                    displayMessageBubble(peer.getUserId(), message, true);
                      
                    messageField.clear();
                    updatePeerList(activeConversationId);
    
                    //Add message to conversation history
                    addMessageToConv("Me: " + message, activeConversationId);
                } 
                else {
                    addErrorMessage("Failed to send message: User does not exist");
                    messageField.clear();
                }
            }
        } else {
        	addErrorMessage("Please enter a message.");
        }
    }

    private boolean isGroupConversation(String conversationId) {
        // Implement logic to check if the conversationId belongs to a group
        return groupsListView.getItems().contains(conversationId);
    }

    //Visual representation of the message
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

    //Add to the users that we have already communicated with
    public void addToConnected(String sender) {
        if (!connectedPeers.contains(sender)) {
            connectedPeers.add(sender);
        }
    }

    //Display received message
    public void appendReceivedMessage(String sender, String content) {
        Platform.runLater(() -> {
            messageHistory.computeIfAbsent(sender, k -> new ArrayList<>())
                        .add(new ChatMessage(sender, content, false));

            //Add to users we already communicated with
            if (!connectedPeers.contains(sender)) {
                connectedPeers.add(sender);
            }

            addMessageToConv("Other: " + content, sender);

            if (activeConversationId != null && activeConversationId.equals(sender)) {
                displayMessageBubble(sender, content, false);
            } else {
                //Unread messages bubble
                unreadMessageCounts.merge(sender, 1, Integer::sum);
                refreshContactsList();
            }
        });
    }

    //Display received message
    public void appendReceivedMessageGroup(String group, String content) {
        Platform.runLater(() -> {
            messageHistory.computeIfAbsent(group, k -> new ArrayList<>())
                        .add(new ChatMessage(group, content, false));
            
            addMessageToConvGroup("Other: " + content, group);

            if (activeConversationId != null && activeConversationId.equals(group)) {
                displayMessageBubble(group, content, false);
            } else {
                //Unread messages bubble
                unreadMessageCountsGroup.merge(group, 1, Integer::sum);
                refreshGroupsList();
            }
        });
    }

    //Count bubble but for offline messages
    public void updateOfflineMsgCount(String sender) {
        unreadMessageCounts.merge(sender, 0, Integer::sum);
        refreshContactsList();
    }
    
    private void refreshContactsList() {
        contactsListView.refresh();
    }

    private void refreshGroupsList() {
        groupsListView.refresh();
    }

    public void appendError(String error) {
        Platform.runLater(() -> addErrorMessage(error));
    }

    public void appendErrorGroup(String error) {
        Platform.runLater(() -> addErrorMessageGroup(error));
    }

    private void addErrorMessage(String error) {
        Label errorLabel = new Label(error);
        errorLabel.setStyle("-fx-text-fill: red;");
        messagesVBox.getChildren().add(errorLabel);

        // Clear the error message after 3 seconds
        PauseTransition pause = new PauseTransition(javafx.util.Duration.seconds(3));
        pause.setOnFinished(event -> messagesVBox.getChildren().remove(errorLabel));
        pause.play();
    }

    private void addErrorMessageGroup(String error) {
        groupStatusLabel.setStyle("-fx-text-fill: red;");
        groupStatusLabel.setText(error);
        groupStatusLabel.setVisible(true);

        // Clear the error message after 3 seconds
        PauseTransition pause = new PauseTransition(javafx.util.Duration.seconds(3));
        pause.setOnFinished(event -> groupStatusLabel.setVisible(false));
        pause.play();
    }
    
    private void showError(String error) {
        Platform.runLater(() -> {
            searchProgress.setVisible(false);
            searchStatusLabel.setText("Error: " + error);
            searchStatusLabel.setStyle("-fx-text-fill: red;");
            searchStatusLabel.setVisible(true);

            // Clear the error message after 3 seconds
            PauseTransition pause = new PauseTransition(javafx.util.Duration.seconds(3));
            pause.setOnFinished(event -> searchStatusLabel.setVisible(false));
            pause.play();
        });
    }

    private void showErrorGroup(String error) {
        Platform.runLater(() -> {
            searchProgress.setVisible(false);
            groupStatusLabel.setText("Error: " + error);
            groupStatusLabel.setStyle("-fx-text-fill: red;");
            groupStatusLabel.setVisible(true);

            // Clear the error message after 3 seconds
            PauseTransition pause = new PauseTransition(javafx.util.Duration.seconds(3));
            pause.setOnFinished(event -> groupStatusLabel.setVisible(false));
            pause.play();
        });
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    //Save message history on close
    public void saveMessages() {
        peer.saveMessageHistory(convs, unreadMessageCounts);
    }
}