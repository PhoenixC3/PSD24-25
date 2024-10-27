package com.peerapp;

import java.io.Serializable;

public class Message implements Serializable {
    private String sender;
    private String recipient;
    private byte[] encKey;
    private String encryptedContent;
    private String signedMessage;

    public Message(String sender, String recipient, byte[] encKey, String encryptedContent, String signedMessage) {
        this.sender = sender;
        this.recipient = recipient;
        this.encryptedContent = encryptedContent;
        this.signedMessage = signedMessage;
        this.encKey = encKey;
    }

    public String getSender() {
        return sender;
    }

    public String getRecipient() {
        return recipient;
    }

    public String getEncryptedContent() {
        return encryptedContent;
    }

    public String getSignedMessage() {
        return signedMessage;
    }

    public byte[] getEncKey() {
        return encKey;
    }
}
