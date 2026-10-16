package com.vibestudio.app.chat.model;

import org.json.JSONObject;

public class ChatMessage {

    public enum MessageType {
        NORMAL,
        TOOL_CALL,
        TOOL_RESULT,
        ERROR
    }

    private final String sender;
    private final String text;
    private final boolean isUser;
    private final MessageType type;
    private final String toolName;
    private final JSONObject toolArgs;

    public ChatMessage(String sender, String text, boolean isUser) {
        this(sender, text, isUser, MessageType.NORMAL);
    }

    public ChatMessage(String sender, String text, boolean isUser, MessageType type) {
        this(sender, text, isUser, type, null, null);
    }

    public ChatMessage(String sender, String text, boolean isUser, MessageType type, String toolName, JSONObject toolArgs) {
        this.sender = sender;
        this.text = text;
        this.isUser = isUser;
        this.type = type != null ? type : MessageType.NORMAL;
        this.toolName = toolName;
        this.toolArgs = toolArgs;
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

    public boolean isError() {
        return type == MessageType.ERROR;
    }

    public String getToolName() {
        return toolName;
    }

    public JSONObject getToolArgs() {
        return toolArgs;
    }
}
