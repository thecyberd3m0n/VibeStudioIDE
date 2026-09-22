package com.vibestudio.app.chat.model;

public class AiResponse {

    private final boolean isSuccess;
    private final int statusCode;
    private final String content;
    private final String errorMessage;

    public AiResponse(boolean isSuccess, int statusCode, String content, String errorMessage) {
        this.isSuccess = isSuccess;
        this.statusCode = statusCode;
        this.content = content;
        this.errorMessage = errorMessage;
    }

    public static AiResponse success(String content) {
        return new AiResponse(true, 200, content, null);
    }

    public static AiResponse error(int statusCode, String errorMessage) {
        return new AiResponse(false, statusCode, null, errorMessage);
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
}
