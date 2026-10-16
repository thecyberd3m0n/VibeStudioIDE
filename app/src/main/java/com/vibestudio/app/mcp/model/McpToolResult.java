package com.vibestudio.app.mcp.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class McpToolResult {
    private final boolean success;
    private final String output;
    private final String errorMessage;
    private final String summary;
    private final Map<String, Object> extraFields;

    private McpToolResult(boolean success, String output, String errorMessage, String summary, Map<String, Object> extraFields) {
        this.success = success;
        this.output = output;
        this.errorMessage = errorMessage;
        this.summary = summary;
        this.extraFields = extraFields != null ? extraFields : new HashMap<>();
    }

    public static McpToolResult success(String output) {
        return new McpToolResult(true, output, null, null, null);
    }

    public static McpToolResult success(String output, String summary) {
        return new McpToolResult(true, output, null, summary, null);
    }

    public static McpToolResult success(Map<String, Object> extraFields) {
        return new McpToolResult(true, null, null, null, extraFields);
    }

    public static McpToolResult success(Map<String, Object> extraFields, String summary) {
        return new McpToolResult(true, null, null, summary, extraFields);
    }

    public static McpToolResult error(String errorMessage) {
        return new McpToolResult(false, null, errorMessage, null, null);
    }

    public static McpToolResult error(String errorMessage, String summary) {
        return new McpToolResult(false, null, errorMessage, summary, null);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getOutput() {
        return output;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getSummary() {
        return summary;
    }

    public Map<String, Object> getExtraFields() {
        return extraFields;
    }

    public JSONObject toJsonObject() {
        JSONObject json = new JSONObject();
        try {
            json.put("status", success ? "success" : "error");
            if (summary != null && !summary.isEmpty()) {
                json.put("summary", summary);
            }
            if (success) {
                if (output != null) {
                    json.put("output", output);
                }
                for (Map.Entry<String, Object> entry : extraFields.entrySet()) {
                    json.put(entry.getKey(), entry.getValue());
                }
            } else {
                json.put("message", errorMessage != null ? errorMessage : "Unknown error");
            }
        } catch (JSONException e) {
            // Should not happen
        }
        return json;
    }
}
