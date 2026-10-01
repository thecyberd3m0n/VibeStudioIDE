package com.vibestudio.app.chat.tool;

import android.content.Context;

import com.vibestudio.app.chat.model.ToolCall;
import com.vibestudio.app.chat.model.ToolResult;
import com.vibestudio.app.chat.truncator.OutputTruncator;
import com.vibestudio.app.mcp.McpClientManager;
import com.vibestudio.app.service.LogViewerService;

import org.json.JSONObject;

public class ToolExecutionManager {

    private static final String TAG = "ToolExecutionManager";
    private static final long DEFAULT_TOOL_WAIT_MS = 10L;

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

        String toolName = toolCall.getName();
        long plannedDelayMs = toolCall.getPlannedDelayMs();

        if (plannedDelayMs > 0) {
            LogViewerService.getInstance().i(TAG, "Agent requested planned delay of " + plannedDelayMs + " ms for tool: " + toolName);
        }

        JSONObject resultObj = mMcpClientManager.executeToolCall(toolName, toolCall.getArguments(), context);

        // Delay execution response by 10ms to allow tool state update
        try {
            Thread.sleep(DEFAULT_TOOL_WAIT_MS);
        } catch (InterruptedException ignored) {}

        // Apply 25KB output truncation rule
        JSONObject truncatedObj = OutputTruncator.truncateOutput(resultObj);

        return new ToolResult(toolName, truncatedObj);
    }
}
