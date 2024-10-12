module com.peerapp {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.base;

    opens com.peerapp to javafx.fxml;
    exports com.peerapp;
}
