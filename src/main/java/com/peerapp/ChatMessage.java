package com.peerapp;

public class ChatMessage {
    final String sender;
    final String content;
    final boolean isSent;

    ChatMessage(String sender, String content, boolean isSent) {
        this.sender = sender;
        this.content = content;
        this.isSent = isSent;
    }
}
