package com.peerapp;

// To use in PeerController (getting messages when not in chat)
public class ChatMessage {
    final String sender;
    final String content;
    final boolean isSent;

    public ChatMessage(String sender, String content, boolean isSent) {
        this.sender = sender;
        this.content = content;
        this.isSent = isSent;
    }
}
