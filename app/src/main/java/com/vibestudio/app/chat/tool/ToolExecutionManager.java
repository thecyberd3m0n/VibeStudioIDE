package com.vibestudio.app.chat.tool;

import android.content.Context;

import com.vibestudio.app.chat.model.ToolCall;
import com.vibestudio.app.chat.model.ToolResult;
import com.vibestudio.app.chat.truncator.OutputTruncator;
import com.vibestudio.app.mcp.McpClientManager;
import com.vibestudio.app.service.ChatService;
import com.vibestudio.app.service.LogViewerService;

import org.json.JSONObject;

public class ToolExecutionManager {

    private static final String TAG = "ToolExecutionManager";

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

        // Perform planned delay before executing tool call if requested by Agent
        if (plannedDelayMs > 0) {
            LogViewerService.getInstance().i(TAG, "Executing planned delay of " + plannedDelayMs + " ms before tool: " + toolName);
            performDelay(plannedDelayMs);
        }

        JSONObject resultObj = mMcpClientManager.executeToolCall(toolName, toolCall.getArguments(), context);

        // Apply 25KB output truncation rule
        JSONObject truncatedObj = OutputTruncator.truncateOutput(resultObj);

        return new ToolResult(toolName, truncatedObj);
    }

    private void performDelay(long delayMs) {
        if (delayMs <= 0) return;
        long elapsed = 0;
        long step = 100L;
        while (elapsed < delayMs) {
            if (ChatService.getInstance().isStopRequested() || Thread.currentThread().isInterrupted()) {
                LogViewerService.getInstance().i(TAG, "Planned delay interrupted by user stop request.");
                break;
            }
            long chunk = Math.min(step, delayMs - elapsed);
            try {
                Thread.sleep(chunk);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            elapsed += chunk;
        }
    }
}
