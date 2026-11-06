package com.vibestudio.app.chat.model;

public class AiResponse {

    private final boolean isSuccess;
    private final int statusCode;
    private final String content;
    private final String reasoningContent;
    private final String errorMessage;
    private final int usedTokens;

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
