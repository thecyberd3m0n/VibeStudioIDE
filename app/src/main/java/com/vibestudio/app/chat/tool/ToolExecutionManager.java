package com.vibestudio.app.chat.tool;

import android.content.Context;

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

    public ToolResult executeTool(Context context, JSONObject toolCallObj) {
        if (toolCallObj == null) {
            JSONObject err = new JSONObject();
            try {
                err.put("status", "error");
                err.put("message", "Null tool call provided.");
            } catch (Exception e) {
                LogViewerService.getInstance().e(TAG, "Error constructing JSON error result", e);
            }
            return new ToolResult("unknown", err);
        }

        String toolName = toolCallObj.optString("tool", "unknown");
        JSONObject args = toolCallObj.optJSONObject("args");
        if (args == null) args = new JSONObject();
        long plannedDelayMs = Math.max(0L, toolCallObj.optLong("planned_delay_ms", 0L));

        JSONObject resultObj;
        try {
            // Dispatch/execute command or tool call FIRST before delay
            resultObj = mMcpClientManager.executeToolCall(toolName, args, context);
        } catch (Throwable t) {
            LogViewerService.getInstance().e(TAG, "Exception during execution of tool '" + toolName + "': " + t.getMessage(), t);
            resultObj = new JSONObject();
            try {
                resultObj.put("status", "error");
                resultObj.put("message", "Tool execution threw exception: " + t.getMessage());
                resultObj.put("exception", t.getClass().getName());
                resultObj.put("details", t.toString());
            } catch (Exception e) {
                LogViewerService.getInstance().e(TAG, "Error building exception JSON result", e);
            }
        }

        // Perform planned delay AFTER executing tool call if requested by Agent
        if (plannedDelayMs > 0) {
            LogViewerService.getInstance().i(TAG, "Executing planned delay of " + plannedDelayMs + " ms after dispatching tool: " + toolName);
            performDelay(plannedDelayMs);
        }

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
