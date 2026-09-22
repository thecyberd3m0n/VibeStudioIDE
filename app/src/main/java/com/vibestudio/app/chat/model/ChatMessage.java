package com.vibestudio.app.chat.model;

public class ChatMessage {

    public enum MessageType {
        NORMAL,
        TOOL_CALL,
        TOOL_RESULT
    }

    private final String sender;
    private final String text;
    private final boolean isUser;
    private final MessageType type;

    public ChatMessage(String sender, String text, boolean isUser) {
        this(sender, text, isUser, MessageType.NORMAL);
    }

    public ChatMessage(String sender, String text, boolean isUser, MessageType type) {
        this.sender = sender;
        this.text = text;
        this.isUser = isUser;
        this.type = type != null ? type : MessageType.NORMAL;
    }

    public String getSender() {
        return sender;
    }

    public String getText() {
        return text;
    }

    public boolean isUser() {
        return isUser;
    }

    public MessageType getType() {
        return type;
    }
}
