module com.peerapp {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.base;
    requires java.sql;
    requires javafx.graphics;
    requires org.bouncycastle.provider;
    requires org.bouncycastle.pkix;
    requires org.bouncycastle.util;
    requires javafx.base;

    opens com.peerapp to javafx.fxml;
    exports com.peerapp;
}
