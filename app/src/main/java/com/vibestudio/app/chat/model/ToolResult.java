package com.vibestudio.app.chat.model;

import org.json.JSONObject;

public class ToolResult {

    private final String toolName;
    private final JSONObject output;

    public ToolResult(String toolName, JSONObject output) {
        this.toolName = toolName;
        this.output = output != null ? output : new JSONObject();
    }

    public String getToolName() {
        return toolName;
    }

    public JSONObject getOutput() {
        return output;
    }

    public boolean isError() {
        if (output == null) return false;
        String status = output.optString("status", "");
        return "error".equalsIgnoreCase(status) || output.has("error") || output.has("exception");
    }

    public String getErrorMessage() {
        if (output == null) return null;
        if (output.has("message")) {
            return output.optString("message");
        }
        if (output.has("error")) {
            return output.optString("error");
        }
        if (output.has("details")) {
            return output.optString("details");
        }
        return null;
    }

    public String getFormattedOutput() {
        String header = "[MCP Tool Execution Output for " + toolName + "]\n";
        if (isError()) {
            String msg = getErrorMessage();
            return header + "TOOL CALL FAILED"
                    + (msg != null && !msg.isEmpty() ? ": " + msg : "")
                    + "\nDo not continue as if it succeeded: fix the cause (e.g. wrong session_id, bad arguments) and retry.\n"
                    + output.toString();
        }
        return header + output.toString();
    }
}
