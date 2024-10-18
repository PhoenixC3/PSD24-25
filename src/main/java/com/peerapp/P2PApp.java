package com.peerapp;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import java.net.BindException;
import java.net.ServerSocket;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Random;

public class P2PApp extends Application {

    private static final int MIN_PORT = 1024;
    private static final int MAX_PORT = 65535;

    private static final String CREATE_PEER_TABLE_SQL = 
        "CREATE TABLE IF NOT EXISTS peers (" +
        "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
        "username TEXT NOT NULL UNIQUE, " +
        "password TEXT NOT NULL, " +
        "salt BLOB NOT NULL, " +
        "ip TEXT NOT NULL, " +
        "port INTEGER NOT NULL);";

    @Override
    public void start(Stage stage) throws Exception {
        String userId = System.getProperty("userId");

        Connection conn = null;
        Statement stmt = null;

        try {
            conn = DatabaseUtil.connect();
            stmt = conn.createStatement();
            stmt.execute(CREATE_PEER_TABLE_SQL);
            
            System.out.println("Table 'users' created successfully.");
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (stmt != null) {
                    stmt.close();
                }
                if (conn != null) {
                    conn.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        if (userId == null || userId.isEmpty()) {
            showLoginScreen();
        } else {
            showMessagingApp(userId);
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

    private void showMessagingApp(String userId) throws Exception {
        FXMLLoader fxmlLoader = new FXMLLoader(P2PApp.class.getResource("/com/peerapp/peer.fxml"));
        Parent root = fxmlLoader.load();
        PeerController controller = fxmlLoader.getController();
        int port = findAvailableRandomPort();
        controller.initialize(userId, port);

        DatabaseUtil.updatePeerPort(userId, port);

        Stage appStage = new Stage();
        appStage.setTitle("P2P Messaging App - " + userId);
        appStage.setScene(new Scene(root, 600, 400));
        appStage.show();

        appStage.setOnCloseRequest(event -> {
            System.exit(0);
        });
    }

    private int findAvailableRandomPort() {
        Random random = new Random();
        int port;

        while (true) {
            port = random.nextInt((MAX_PORT - MIN_PORT) + 1) + MIN_PORT;

            try (ServerSocket serverSocket = new ServerSocket(port)) {
                break;
            } catch (BindException e) {
                // Port is already in use, try the next random port
                findAvailableRandomPort();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return port;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
