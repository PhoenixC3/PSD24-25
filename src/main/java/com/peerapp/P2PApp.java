package com.peerapp;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class P2PApp extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        String userId = System.getProperty("userId");
        
        if (userId == null || userId.isEmpty()) {
            showLoginScreen();
        } else {
            String userPassword = System.getProperty("userPassword");
            int port = Integer.parseInt(System.getProperty("userPort"));
            showMessagingApp(userId, port, userPassword);
        }
    }

    private void showLoginScreen() throws Exception {
        FXMLLoader fxmlLoader = new FXMLLoader(P2PApp.class.getResource("/com/peerapp/login.fxml"));
        Parent root = fxmlLoader.load();
        Stage loginStage = new Stage();
        loginStage.setTitle("Login");
        loginStage.setScene(new Scene(root, 400, 200));
        loginStage.show();

        loginStage.setOnCloseRequest(event -> {
            System.exit(0);
        });
    }

    private void showMessagingApp(String userId, int port, String userPassword) throws Exception {
        FXMLLoader fxmlLoader = new FXMLLoader(P2PApp.class.getResource("/com/peerapp/peer.fxml"));
        Parent root = fxmlLoader.load();
        PeerController controller = fxmlLoader.getController();
        controller.initialize(userId, port, userPassword);

        Stage appStage = new Stage();
        appStage.setTitle("P2P Messaging App - " + userId);
        appStage.setScene(new Scene(root, 600, 400));
        appStage.show();

        appStage.setOnCloseRequest(event -> {
            System.exit(0);
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}
