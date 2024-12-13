package com.peerapp;

import java.io.File;
import java.net.BindException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.*;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

public class PeerController {
    @FXML private TextField messageField;
    @FXML private TextField searchField;
    @FXML private TextField topicField;
    @FXML private TextField memberField;

    @FXML private Button sendButton;
    @FXML private Button searchButton;
    @FXML private Button searchKeywordButton;
    @FXML private Button refreshButton;
    @FXML private Button refreshButtonGroups;
    @FXML private Button createGroupButton;
    @FXML private Button addMemberButton;

    @FXML private ScrollPane messagesArea;

    @FXML private VBox messagesVBox;

    @FXML private ProgressIndicator searchProgress;

    @FXML private ListView<String> contactsListView;
    @FXML private ListView<String> groupsListView;
    @FXML private ListView<String> additionalGroupsListView;
    @FXML private ListView<String> searchResultsListView;

    @FXML private Label currentUserIdLabel;
    @FXML private Label searchStatusLabel;
    @FXML private Label groupStatusLabel;

    private Peer peer;
    private SSEUtil.Client sseClient;

    //Peers that have already sent messages
    private ObservableList<String> connectedPeers = FXCollections.observableArrayList();

    //Peers that have already sent messages
    private ObservableList<String> shownGroups = FXCollections.observableArrayList();

    //Peers after search
    private FilteredList<String> filteredPeers;

    //Peers after search
    private FilteredList<String> filteredGroups;

    //To use when unread messages
    private Map<String, List<ChatMessage>> messageHistory = new HashMap<>();

    //To use when unread messages
    private Map<String, List<ChatMessage>> messageHistoryGroups = new HashMap<>();

    //To create the bubble for unread messages
    private HashMap<String, Integer> unreadMessageCounts = new HashMap<String, Integer>();

    //To create the bubble for unread messages
    private HashMap<String, Integer> unreadMessageCountsGroup = new HashMap<String, Integer>();

    //User that we are currently chatting with
    private String activeConversationId;

    //User that we are currently chatting with
    private String selectedGroup;

    //Message history
    private HashMap<String, LinkedList<String>> shownConvs = new HashMap<String, LinkedList<String>>();

    //Message history
    private HashMap<String, LinkedList<String>> shownConvsGroups = new HashMap<String, LinkedList<String>>();

    //Message history
    private HashMap<String, LinkedList<Message>> convs = new HashMap<String, LinkedList<Message>>();

    //Message history groups
    private HashMap<String, LinkedList<Message>> convsGroups = new HashMap<String, LinkedList<Message>>();

    //Store messages with their IDs
    private Map<String, Message> messageStore = new HashMap<>();

    private ChangeListener<String> contactSelectionListener = (observable, oldValue, newValue) -> {
        if (newValue != null) {
            startConversationWithPeer(newValue);
    
            // Reset the unread message count
            unreadMessageCounts.put(newValue, 0);
            refreshContactsList();
    
            // Deselect the groups list
            groupsListView.getSelectionModel().clearSelection();
            addMemberButton.setVisible(false);
        }
    };

    private ChangeListener<String> groupSelectionListener = (observable, oldValue, newValue) -> {
        if (newValue != null) {
            startConversationWithGroup(newValue);

            //Reset the unread message count
            unreadMessageCountsGroup.put(newValue, 0);
            refreshGroupsList();

            // Deselect the contacts list
            contactsListView.getSelectionModel().clearSelection();
            addMemberButton.setVisible(false);
        }
    };

    private ChangeListener<String> joinSelectionListener = (observable, oldValue, newValue) -> {
        if (newValue != null) {
            selectedGroup = newValue;

            // Deselect the contacts and my groups list
            contactsListView.getSelectionModel().clearSelection();
            groupsListView.getSelectionModel().clearSelection();

            addMemberButton.setVisible(true);
        }
    };

    public void initialize(String userId, int port, String password) {
        try {
            //Create peer object
            peer = new Peer(userId, password, port, this);
            SSEUtil.Server sseServer = new SSEUtil.Server();
            this.sseClient = new SSEUtil.Client(sseServer);

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
        addMemberButton.setOnAction(event -> addToGroup());
    }

    private void createGroup() {
        String groupTopic = topicField.getText();

        String groupId = "group_" + groupTopic;

        if (groupTopic.isEmpty()) {
            showErrorGroup("Group topic cannot be empty.");
            return;
        }

        peer.createGroup(groupId);
        topicField.clear();
    }

    private void addToGroup() {
        String groupTopic = selectedGroup;
        String member = peer.getUserId();

        if (member.isEmpty()) {
            showErrorGroup("Member username cannot be empty.");
            return;
        }

        peer.addMemberToGroup(groupTopic, member);

        additionalGroupsListView.setItems(FXCollections.observableArrayList(peer.getAvailableGroups()));
    }

    public void updateGroupList(String topic) {
        Platform.runLater(() -> {
            shownGroups.add(topic);
            refreshGroupsList();
        });
    }

    //Get message history (persistent) and fill the left side contact history list
    private void getMessages() {
        HashMap<String, LinkedList<String>> convsGet = peer.loadMessageHistory();
        HashMap<String, LinkedList<String>> convsGetGroups = peer.loadMessageHistoryGroups();

        if (convsGet != null) {
            this.shownConvs = convsGet;

            for (String key : convsGet.keySet()) {
                addToConnected(key);
            }
        }

        if (convsGetGroups != null) {
            this.shownConvsGroups = convsGetGroups;
        }
    }

    public void addToConnected(String peerId) {
        if (!connectedPeers.contains(peerId) && !peer.getUserId().equals(peerId)) {
            connectedPeers.add(peerId);
        }
    }

    private void getMessageCounts() {
        unreadMessageCounts = peer.getMessageCounts();
        unreadMessageCountsGroup = peer.getMessageCountsGroups();
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
        connectedPeers.clear();
        connectedPeers.addAll(peer.getConnectedPeers());

        filteredPeers = new FilteredList<>(connectedPeers);
        contactsListView.setItems(filteredPeers);
        
        //On clicking another user's name we start the conversation
        contactsListView.getSelectionModel().selectedItemProperty().addListener(contactSelectionListener);
        
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
        //MY GROUPS
        shownGroups.clear();
        shownGroups.addAll(peer.getMyGroups());

        filteredGroups = new FilteredList<>(shownGroups);
        groupsListView.setItems(filteredGroups);

        groupsListView.getSelectionModel().selectedItemProperty().addListener(groupSelectionListener);

        //AVAILABLE GROUPS
        additionalGroupsListView.setItems(FXCollections.observableArrayList(peer.getAvailableGroups()));

        additionalGroupsListView.getSelectionModel().selectedItemProperty().addListener(joinSelectionListener);
        
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
                    // Splits message into two
                    // Where [0] stores the message sender and [1] stores the actual message
                    String[] msgSplit = msg.split(":", 2);
                    displayMessageBubble(msgSplit[0], msgSplit[1], false);
                }
            }
        }
    }
    
    //Configure search button
    private void initializeSearch() {
        searchProgress.setVisible(false);
        searchStatusLabel.setVisible(false);
        searchStatusLabel.setStyle("-fx-text-fill: white;");
        refreshButton.setOnAction(event -> refreshContacts());
        refreshButtonGroups.setOnAction(event -> additionalGroupsListView.setItems(FXCollections.observableArrayList(peer.getAvailableGroups())));
        
        searchButton.setOnAction(event -> performSearch());
        searchKeywordButton.setOnAction(event -> performKeywordSearch());
    }

    private void refreshContacts() {
        connectedPeers.clear();
        connectedPeers.addAll(peer.getConnectedPeers());
        searchStatusLabel.setVisible(false);
        searchStatusLabel.setStyle("-fx-text-fill: white;");
        refreshContactsList();
    }

    //Get (local but persistent) message history for a certain conversation
    private LinkedList<String> getConversationMessages(String recipient) {
        return shownConvs.get(recipient);
    }

    //Get (local but persistent) message history for a certain conversation
    private LinkedList<String> getConversationMessagesGroups(String group) {
        return shownConvsGroups.get(group);
    }

    //Add message to a conversation's history (local but persistent)
    private void addMessageToConv(String message, Message enc, String conversationId) {
        shownConvs.computeIfAbsent(conversationId, k -> new LinkedList<>())
                .add(message);
        convs.computeIfAbsent(conversationId, k -> new LinkedList<>())
                .add(enc);
    }

    //Add message to a conversation's history (local but persistent)
    private void addMessageToConvGroup(String message, Message enc, String group) {
        shownConvsGroups.computeIfAbsent(group, k -> new LinkedList<>())
               .add(message);
        convsGroups.computeIfAbsent(group, k -> new LinkedList<>())
               .add(enc);
    }
    
    // Perform search for a keyword
    private void performSearch() {
        String searchText = searchField.getText().trim();

        if (!searchText.isEmpty()) {
            searchProgress.setVisible(true);
            searchStatusLabel.setText("Searching...");
            searchStatusLabel.setVisible(true);
            searchStatusLabel.setStyle("-fx-text-fill: white;");
            
            peer.getPeerInfo(searchText);
        }
        else {
            connectedPeers.clear();
            connectedPeers.addAll(peer.getConnectedPeers());
            searchStatusLabel.setVisible(false);
            searchStatusLabel.setStyle("-fx-text-fill: white;");
        }
    }

    // Perform search for a keyword
    private void performKeywordSearch() {
        String keyword = searchField.getText().trim();
        if (!keyword.isEmpty()) {
            searchProgress.setVisible(true);
            searchStatusLabel.setText("Searching...");
            searchStatusLabel.setVisible(true);
            searchStatusLabel.setStyle("-fx-text-fill: white;");
            new Thread(() -> {
                try {
                    List<String> results = sseClient.search(keyword);
                    List<String> formattedResults = new ArrayList<>();
                    for (String messageId : results) {
                        formattedResults.add(getMessageDetails(messageId));
                    }
                    Platform.runLater(() -> {
                        searchResultsListView.getItems().clear();
                        if (formattedResults.isEmpty()) {
                            searchStatusLabel.setText("No results found");
                        } else {
                            searchResultsListView.getItems().addAll(formattedResults);
                            System.out.println("found keyword: " + keyword + " in messages: " + results);
                            System.out.println("found keyword: " + keyword + " in messages: " + formattedResults);
                            searchStatusLabel.setText("Search complete");
                        }
                        searchProgress.setVisible(false);
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    Platform.runLater(() -> {
                        searchProgress.setVisible(false);
                        searchStatusLabel.setText("Search failed");
                    });
                }
            }).start();
        }
    }

    // Update keyword index when a message is sent
    private void updateKeywordIndex(String keyword, String messageId) {
        new Thread(() -> {
            try {
                sseClient.update(keyword, messageId);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
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
                connectedPeers.clear();
                connectedPeers.add(peerId);
                searchProgress.setVisible(false);
                searchStatusLabel.setText("Found peer: " + peerId);
                searchStatusLabel.setStyle("-fx-text-fill: white;");
                refreshContactsList();
            });
        }
        else 
        {
            PauseTransition delay = new PauseTransition(javafx.util.Duration.seconds(3));

            delay.setOnFinished(event -> {
                searchProgress.setVisible(false);
                searchStatusLabel.setText("No peers found");
                searchStatusLabel.setStyle("-fx-text-fill: white;");
            });
            
            delay.play();
        }
    }

    private String generateMessageId() {
        return UUID.randomUUID().toString();
    }

    //Send a message
    private void sendMessage() {
        String message = messageField.getText().trim();

        if (activeConversationId == null) {
        	addErrorMessage("No conversation selected. Start a chat first.");
        	return;
        }
        
        if (!message.isEmpty()) {
        	Message sent;
            String messageId = generateMessageId();

            if (isGroupConversation(activeConversationId)) {
                // Send group message
                sent = peer.sendGroupMessage(activeConversationId, message);

                // Add message to group history
                messageHistoryGroups.computeIfAbsent(activeConversationId, k -> new ArrayList<>())
                                    .add(new ChatMessage(peer.getUserId(), message, true));
    
                displayMessageBubble(peer.getUserId(), message, true);
                
                messageField.clear();

                //Add message to conversation history
                addMessageToConvGroup("Me: " + message, sent, activeConversationId);

                moveGroupUp(activeConversationId);

            } else {
                // Send individual message
                sent = peer.sendMessage(activeConversationId, message);

                if (sent != null) {
                    // Add message to individual history
                    messageHistory.computeIfAbsent(activeConversationId, k -> new ArrayList<>())
                                    .add(new ChatMessage(peer.getUserId(), message, true));
                    
                    displayMessageBubble(peer.getUserId(), message, true);
                      
                    messageField.clear();
    
                    //Add message to conversation history
                    addMessageToConv("Me: " + message, sent, activeConversationId);

                    moveContactUp(activeConversationId);

                } else {
                    addErrorMessage("Failed to send message: User does not exist");
                    messageField.clear();
                }
            }

            // Store the message with its ID
            messageStore.put(messageId, sent);

            // Update keyword index
            String[] keywords = message.split("\\s+");
            for (String keyword : keywords) {
                updateKeywordIndex(keyword, messageId);
                System.out.println("keyword: " + keyword + " added to index: " + messageId);
            }
        } else {
        	addErrorMessage("Please enter a message.");
        }
    }

    //Get the message details
    private String getMessageDetails(String messageId) {
        Message message = messageStore.get(messageId);
        if (message != null) {
            String sender = message.getSender();
            String recipient = message.getRecipient();
            String keyword = message.getEncryptedContent();
            return "\"" + keyword + "\" from chat with \"" + (sender.equals(peer.getUserId()) ? recipient : sender) + "\"";
        }
        return "Message not found";
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

    //Display received message
    public void appendReceivedMessage(Message msg, String sender, String content, String enc) {
        Platform.runLater(() -> {
            messageHistory.computeIfAbsent(sender, k -> new ArrayList<>())
                        .add(new ChatMessage(sender, content, false));

            addMessageToConv("Other: " + content, msg, sender);
            moveContactUp(sender);

            if (activeConversationId != null && activeConversationId.equals(sender)) {
                displayMessageBubble(sender, content, false);
            } else {
                //Unread messages bubble
                unreadMessageCounts.merge(sender, 1, Integer::sum);
                refreshContactsList();
            }

            String musicFile = "zap.mp3";
            Media sound = new Media(new File(musicFile).toURI().toString());
            MediaPlayer mediaPlayer = new MediaPlayer(sound);
            mediaPlayer.setVolume(0.2);
            mediaPlayer.play();
        });
    }

    //Display received message
    public void appendReceivedMessageGroup(Message msg, String group, String sender, String content, String enc) {
        Platform.runLater(() -> {
            messageHistoryGroups.computeIfAbsent(group, k -> new ArrayList<>())
                        .add(new ChatMessage(sender, content, false));
            
            addMessageToConvGroup(sender + ": " + content, msg, group);
            moveGroupUp(group);

            if (activeConversationId != null && activeConversationId.equals(group)) {
                displayMessageBubble(sender, content, false);
            } else {
                //Unread messages bubble
                unreadMessageCountsGroup.merge(group, 1, Integer::sum);
                refreshGroupsList();
            }

            String musicFile = "zap.mp3";
            Media sound = new Media(new File(musicFile).toURI().toString());
            MediaPlayer mediaPlayer = new MediaPlayer(sound);
            mediaPlayer.setVolume(0.2);
            mediaPlayer.play();
        });
    }

    // Handle incoming messages
    private void moveContactUp(String sender) {
        // Move the sender to the top of the contacts list
        contactsListView.getSelectionModel().selectedItemProperty().removeListener(contactSelectionListener);

        connectedPeers.remove(sender);
        connectedPeers.add(0, sender);
        refreshContactsList();

        contactsListView.getSelectionModel().selectedItemProperty().addListener(contactSelectionListener);
    }

    // Handle incoming messages
    private void moveGroupUp(String group) {
        // Move the sender to the top of the contacts list
        groupsListView.getSelectionModel().selectedItemProperty().removeListener(groupSelectionListener);

        shownGroups.remove(group);
        shownGroups.add(0, group);
        refreshGroupsList();

        groupsListView.getSelectionModel().selectedItemProperty().addListener(groupSelectionListener);
    }

    // Refresh the contacts list
    private void refreshContactsList() {
        contactsListView.setItems(null);
        contactsListView.setItems(filteredPeers);
        contactsListView.refresh();
    }

    //Count bubble but for offline messages
    public void updateOfflineMsgCount(String sender) {
        unreadMessageCounts.merge(sender, 0, Integer::sum);
        refreshContactsList();
    }

    //Count bubble but for offline messages
    public void updateOfflineMsgCountGroups(String sender) {
        unreadMessageCountsGroup.merge(sender, 0, Integer::sum);
        refreshGroupsList();
    }

    private void refreshGroupsList() {
        groupsListView.setItems(null);
        groupsListView.setItems(filteredGroups);
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
        peer.saveConnectedPeers(new ArrayList<String>(connectedPeers));

        peer.saveMessageHistoryGroups(convsGroups, unreadMessageCountsGroup);
    }
}