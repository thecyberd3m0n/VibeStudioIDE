package com.vibestudio.app.chat.model;

import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChatMessage {

    public enum MessageType {
        NORMAL,
        TOOL_CALL,
        TOOL_RESULT,
        ERROR,
        REASONING,
        TOOL_IMAGE
    }

    private final String sender;
    private final String text;
    private final boolean isUser;
    private final MessageType type;
    private final String toolName;
    private final JSONObject toolArgs;
    private final String callId;
    private final String turnId;
    private final String callState;
    private final String callStateKind;
    private final List<ChatAttachment> attachments;

    public ChatMessage(String sender, String text, boolean isUser) {
        this(sender, text, isUser, MessageType.NORMAL);
    }

    public ChatMessage(String sender, String text, boolean isUser, MessageType type) {
        this(sender, text, isUser, type, null, null);
    }

    public ChatMessage(String sender, String text, boolean isUser, MessageType type, String toolName, JSONObject toolArgs) {
        this(sender, text, isUser, type, toolName, toolArgs, null, null, null, null, null);
    }

    public ChatMessage(String sender, String text, boolean isUser, List<ChatAttachment> attachments) {
        this(sender, text, isUser, MessageType.NORMAL, null, null, null, null, null, null, attachments);
    }
    /** Image returned by a tool (e.g. screenshot): sent to the model as a user image, shown in the chat as a tool message. */
    public static ChatMessage toolImage(String toolName, String text, List<ChatAttachment> attachments) {
        return new ChatMessage("System Tool", text, true, MessageType.TOOL_IMAGE, toolName, null, null, null, null, null, attachments);
    }



    private ChatMessage(String sender, String text, boolean isUser, MessageType type, String toolName, JSONObject toolArgs,
                        String callId, String turnId, String callState, String callStateKind) {
        this(sender, text, isUser, type, toolName, toolArgs, callId, turnId, callState, callStateKind, null);
    }

    private ChatMessage(String sender, String text, boolean isUser, MessageType type, String toolName, JSONObject toolArgs,
                        String callId, String turnId, String callState, String callStateKind, List<ChatAttachment> attachments) {
        this.sender = sender;
        this.text = text;
        this.isUser = isUser;
        this.type = type != null ? type : MessageType.NORMAL;
        this.toolName = toolName;
        this.toolArgs = toolArgs;
        this.callId = callId;
        this.turnId = turnId;
        this.callState = callState;
        this.callStateKind = callStateKind;
        this.attachments = attachments != null ? new ArrayList<>(attachments) : Collections.<ChatAttachment>emptyList();
    }

    /**
     * A copy of this tool call or tool result message tied to its counterpart, so providers can send the history
     * in their native function-calling format instead of as text.
     */
    public ChatMessage withToolLink(String callId, String turnId, String state, String callStateKind) {
        return new ChatMessage(sender, text, isUser, type, toolName, toolArgs, callId, turnId, state, callStateKind, attachments);
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

    /** Id shared by a tool call and its result; null for messages that aren't linked. */
    public String getCallId() {
        return callId;
    }

    /** Id shared by the tool calls (and their results) that came from the same model reply. */
    public String getTurnId() {
        return turnId;
    }

    public String getCallState() {
        return callState;
    }

    public String getCallStateKind() {
        return callStateKind;
    }

    public List<ChatAttachment> getAttachments() {
        return Collections.unmodifiableList(attachments);
    }

    public boolean hasAttachments() {
        return !attachments.isEmpty();
    }
}