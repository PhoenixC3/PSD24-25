package com.peerapp;

import java.io.Serializable;

public class Message implements Serializable {
    private String sender;
    private String recipient;
    private byte[] encKey;
    private String encryptedContent;
    private String signedMessage;
    private byte[] iv;
    private String group;

    public Message(String sender, String recipient, byte[] encKey, String encryptedContent, String signedMessage, byte[] iv) {
        this.sender = sender;
        this.recipient = recipient;
        this.encryptedContent = encryptedContent;
        this.signedMessage = signedMessage;
        this.encKey = encKey;
        this.iv = iv;
        this.group = null;
    }

    public Message(String sender, String recipient, byte[] encKey, String encryptedContent, String signedMessage, byte[] iv, String group) {
        this.sender = sender;
        this.recipient = recipient;
        this.encryptedContent = encryptedContent;
        this.signedMessage = signedMessage;
        this.encKey = encKey;
        this.iv = iv;
        this.group = group;
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

    public byte[] getIV() {
        return iv;
    }

    public String getGroup() {
        return group;
    }
}
