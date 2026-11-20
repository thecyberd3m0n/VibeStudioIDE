package com.vibestudio.app.chat.model;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AiResponse {

    /** One tool call returned through a provider's native function-calling API. */
    public static final class ToolCall {
        private final String id;
        private final JSONObject call;
        private final String state;
        private final String stateKind;

        /**
         * @param id        the API's id for the call (generated when the API has none)
         * @param call      {"planned_delay_ms", "tool", "args"} as ToolExecutionManager expects it
         * @param state     data the provider needs to replay this call later, or null
         * @param stateKind which API produced {@code state}
         */
        public ToolCall(String id, JSONObject call, String state, String stateKind) {
            this.id = id;
            this.call = call;
            this.state = state;
            this.stateKind = stateKind;
        }

        public String getId() {
            return id;
        }

        public JSONObject getCall() {
            return call;
        }

        public String getState() {
            return state;
        }

        public String getStateKind() {
            return stateKind;
        }
    }

    private final boolean isSuccess;
    private final int statusCode;
    private final String content;
    private final String reasoningContent;
    private final String errorMessage;
    private final int usedTokens;
    private List<ToolCall> toolCalls = Collections.emptyList();

    public AiResponse(boolean isSuccess, int statusCode, String content, String reasoningContent, String errorMessage, int usedTokens) {
        this.isSuccess = isSuccess;
        this.statusCode = statusCode;
        this.content = content;
        this.reasoningContent = reasoningContent;
        this.errorMessage = errorMessage;
        this.usedTokens = usedTokens;
    }

    public AiResponse(boolean isSuccess, int statusCode, String content, String errorMessage, int usedTokens) {
        this(isSuccess, statusCode, content, null, errorMessage, usedTokens);
    }

    public static AiResponse success(String content) {
        return new AiResponse(true, 200, content, null, null, 0);
    }

    public static AiResponse success(String content, int usedTokens) {
        return new AiResponse(true, 200, content, null, null, usedTokens);
    }

    public static AiResponse success(String content, String reasoningContent, int usedTokens) {
        return new AiResponse(true, 200, content, reasoningContent, null, usedTokens);
    }

    public static AiResponse error(int statusCode, String errorMessage) {
        return new AiResponse(false, statusCode, null, null, errorMessage, 0);
    }

    /** Attaches the tool calls the provider received natively, in order; the content still holds them as JSON text. */
    public AiResponse withToolCalls(List<ToolCall> calls) {
        if (calls != null && !calls.isEmpty()) {
            this.toolCalls = Collections.unmodifiableList(new ArrayList<>(calls));
        }
        return this;
    }

    public List<ToolCall> getToolCalls() {
        return toolCalls;
    }

    public boolean isSuccess() {
        return isSuccess;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getContent() {
        return content;
    }

    public String getReasoningContent() {
        return reasoningContent;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public int getUsedTokens() {
        return usedTokens;
    }
}
