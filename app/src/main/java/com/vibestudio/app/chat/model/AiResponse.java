package com.vibestudio.app.chat.model;

public class AiResponse {

    private final boolean isSuccess;
    private final int statusCode;
    private final String content;
    private final String errorMessage;
    private final int usedTokens;

    public AiResponse(boolean isSuccess, int statusCode, String content, String errorMessage, int usedTokens) {
        this.isSuccess = isSuccess;
        this.statusCode = statusCode;
        this.content = content;
        this.errorMessage = errorMessage;
        this.usedTokens = usedTokens;
    }

    public static AiResponse success(String content) {
        return new AiResponse(true, 200, content, null, 0);
    }

    public static AiResponse success(String content, int usedTokens) {
        return new AiResponse(true, 200, content, null, usedTokens);
    }

    public static AiResponse error(int statusCode, String errorMessage) {
        return new AiResponse(false, statusCode, null, errorMessage, 0);
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

    public String getErrorMessage() {
        return errorMessage;
    }

    public int getUsedTokens() {
        return usedTokens;
    }
}
