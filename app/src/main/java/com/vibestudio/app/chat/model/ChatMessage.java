package com.vibestudio.app.chat.model;

import org.json.JSONObject;

public class ChatMessage {

    public enum MessageType {
        NORMAL,
        TOOL_CALL,
        TOOL_RESULT,
        ERROR,
        REASONING
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

    public ChatMessage(String sender, String text, boolean isUser) {
        this(sender, text, isUser, MessageType.NORMAL);
    }

    public ChatMessage(String sender, String text, boolean isUser, MessageType type) {
        this(sender, text, isUser, type, null, null);
    }

    public ChatMessage(String sender, String text, boolean isUser, MessageType type, String toolName, JSONObject toolArgs) {
        this(sender, text, isUser, type, toolName, toolArgs, null, null, null, null);
    }

    private ChatMessage(String sender, String text, boolean isUser, MessageType type, String toolName, JSONObject toolArgs,
                        String callId, String turnId, String callState, String callStateKind) {
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
    }

    /**
     * A copy of this tool call or tool result message tied to its counterpart, so providers can send the history
     * in their native function-calling format instead of as text.
     *
     * @param callId        id shared by a call and its result
     * @param turnId        id shared by all calls that came from one model reply
     * @param state         provider-specific data needed to replay the call (a Gemini function-call part, Responses
     *                      reasoning items), or null
     * @param callStateKind which API produced {@code state}, so only that API replays it
     */
    public ChatMessage withToolLink(String callId, String turnId, String state, String callStateKind) {
        return new ChatMessage(sender, text, isUser, type, toolName, toolArgs, callId, turnId, state, callStateKind);
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
}
