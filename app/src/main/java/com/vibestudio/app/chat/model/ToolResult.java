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

    public String getFormattedOutput() {
        return "[MCP Tool Execution Output for " + toolName + "]\n" + output.toString();
    }
}
