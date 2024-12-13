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
    private String messageId;
    private String[] keywords;

    public Message(String sender, String recipient, byte[] encKey, String encryptedContent, String signedMessage, byte[] iv, String messageId, String[] keywords) {
        this.sender = sender;
        this.recipient = recipient;
        this.encryptedContent = encryptedContent;
        this.signedMessage = signedMessage;
        this.encKey = encKey;
        this.iv = iv;
        this.group = null;
        this.messageId = messageId;
        this.keywords = keywords;
    }

    public Message(String sender, String recipient, byte[] encKey, String encryptedContent, String signedMessage, byte[] iv, String messageId, String[] keywords, String group) {
        this.sender = sender;
        this.recipient = recipient;
        this.encryptedContent = encryptedContent;
        this.signedMessage = signedMessage;
        this.encKey = encKey;
        this.iv = iv;
        this.group = group;
        this.messageId = messageId;
        this.keywords = keywords;
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

    public String getMessageId() {
        return messageId;
    }

    public String[] getKeywords() {
        return keywords;
    }
}
