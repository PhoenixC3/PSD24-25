module com.peerapp {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.base;
    requires java.sql;
    requires javafx.graphics;

    opens com.peerapp to javafx.fxml;
    exports com.peerapp;
}
