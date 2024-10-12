package com.peerapp;

import java.io.Serializable;

public class Message implements Serializable {
    private String sender;
    private String recipient;
    private String encryptedContent;
    private String hmac;
    private String originalContent;

    public Message(String sender, String recipient, String encryptedContent, String hmac, String originalContent) {
        this.sender = sender;
        this.recipient = recipient;
        this.encryptedContent = encryptedContent;
        this.hmac = hmac;
        this.originalContent = originalContent;
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

    public String getHmac() {
        return hmac;
    }

    public String getOriginalContent() {
        return originalContent;
    }
}
