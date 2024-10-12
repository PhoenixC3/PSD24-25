package com.peerapp;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.net.BindException;
import java.net.ServerSocket;
import java.util.Random;
import java.util.Scanner;

public class P2PApp extends Application {
    private static final int MIN_PORT = 1024; // Minimum port number
    private static final int MAX_PORT = 65535; // Maximum port number

    @Override
    public void start(Stage stage) throws Exception {
        String userId = System.getProperty("userId", "User1"); // Default to "User1" if not specified

        // Dynamically find an available port
        int port = findAvailableRandomPort();

        FXMLLoader fxmlLoader = new FXMLLoader(P2PApp.class.getResource("/com/peerapp/peer.fxml"));
        Parent root = fxmlLoader.load();
        PeerController controller = fxmlLoader.getController();

        // Initialize with user ID and port
        controller.initialize(userId, port);

        Scene scene = new Scene(root, 600, 400);
        stage.setTitle("P2P Messaging App - " + userId);
        stage.setScene(scene);
        stage.show();
    }

    // Method to find an available random port
    private int findAvailableRandomPort() {
        Random random = new Random();
        int port;

        while (true) {
            port = random.nextInt((MAX_PORT - MIN_PORT) + 1) + MIN_PORT; // Generate random port

            try (ServerSocket serverSocket = new ServerSocket(port)) {
                // Successfully created the server socket, break the loop
                break; // If we successfully create the ServerSocket, exit the loop
            } catch (BindException e) {
                // Port is already in use; try the next random port
                System.out.println("Port " + port + " is already in use. Trying another one.");
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException("Error while trying to find available port: " + e.getMessage());
            }
        }

        return port; // Return the found available port
    }

    public static void main(String[] args) {
        System.setProperty("userId", "User2");
    
        System.out.println("User ID: " + System.getProperty("userId"));
        launch(args);
    }
    
}
