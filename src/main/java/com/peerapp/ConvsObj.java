package com.peerapp;

import java.util.HashMap;
import java.util.LinkedList;

public class ConvsObj<T, U> {
    private HashMap<String, LinkedList<String>> first;
    private HashMap<String, LinkedList<Message>> second;

    public ConvsObj(HashMap<String, LinkedList<String>> first, HashMap<String, LinkedList<Message>> second) {
        this.first = first;
        this.second = second;
    }

    public HashMap<String, LinkedList<String>> getFirst() {
        return first;
    }

    public HashMap<String, LinkedList<Message>> getSecond() {
        return second;
    }
}
