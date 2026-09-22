package com.vibestudio.app.chat.tool;

import android.content.Context;

import com.vibestudio.app.chat.model.ToolCall;
import com.vibestudio.app.chat.model.ToolResult;
import com.vibestudio.app.chat.truncator.OutputTruncator;
import com.vibestudio.app.mcp.McpClientManager;

import org.json.JSONObject;

public class ToolExecutionManager {

    private final McpClientManager mMcpClientManager;

    public ToolExecutionManager(McpClientManager mcpClientManager) {
        this.mMcpClientManager = mcpClientManager;
    }

    public ToolResult executeTool(Context context, ToolCall toolCall) {
        if (toolCall == null) {
            JSONObject err = new JSONObject();
            try {
                err.put("status", "error");
                err.put("message", "Null tool call provided.");
            } catch (Exception ignored) {}
            return new ToolResult("unknown", err);
        }

        JSONObject resultObj = mMcpClientManager.executeToolCall(toolCall.getName(), toolCall.getArguments(), context);

        // Apply 25KB output truncation rule
        JSONObject truncatedObj = OutputTruncator.truncateOutput(resultObj);

        return new ToolResult(toolCall.getName(), truncatedObj);
    }
}
