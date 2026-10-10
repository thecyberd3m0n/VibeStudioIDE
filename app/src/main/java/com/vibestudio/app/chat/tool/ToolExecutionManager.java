package com.vibestudio.app.chat.tool;

import android.content.Context;

import com.vibestudio.app.chat.model.ToolCall;
import com.vibestudio.app.chat.model.ToolResult;
import com.vibestudio.app.chat.truncator.OutputTruncator;
import com.vibestudio.app.mcp.McpClientManager;
import com.vibestudio.app.mcp.ToolSessionManager;
import com.vibestudio.app.mcp.ToolType;
import com.vibestudio.app.service.ChatService;
import com.vibestudio.app.service.LogViewerService;
import com.vibestudio.app.terminal.TerminalSessionManager;

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

        // 1. Dispatch/execute command or tool call FIRST before delay
        JSONObject resultObj = mMcpClientManager.executeToolCall(toolName, toolCall.getArguments(), context);

        // 2. Perform planned delay AFTER executing tool call if requested by Agent
        if (plannedDelayMs > 0) {
            LogViewerService.getInstance().i(TAG, "Executing planned delay of " + plannedDelayMs + " ms after dispatching tool: " + toolName);
            performDelay(plannedDelayMs);
        }

        // 3. Snapshot state after execution/delay for terminal operations to send back in loop
        if ("execute_command".equals(toolName) || "send_keystroke".equals(toolName)) {
            try {
                int sessionId = toolCall.getArguments() != null ? toolCall.getArguments().optInt("session_id", -1) : -1;
                ToolSessionManager sessionManager = ToolSessionManager.getInstance();
                ToolSessionManager.ToolSession session = null;
                if (sessionId > 0) {
                    session = sessionManager.getSession(sessionId);
                } else {
                    int activeSessionId = sessionManager.getOrCreateSession(ToolType.TERMINAL, context, false);
                    session = sessionManager.getSession(activeSessionId);
                }

                if (session != null && session.getTerminalSession() != null && session.getTerminalSession().isRunning()) {
                    String snapshot = TerminalSessionManager.getInstance().readTerminalOutput(session.getTerminalSession(), 30, 0, true, null);
                    if (snapshot != null && !snapshot.isEmpty()) {
                        String currentResult = resultObj.optString("result", "");
                        if (!currentResult.isEmpty()) {
                            resultObj.put("result", currentResult + "\n\nTerminal Snapshot:\n" + snapshot);
                        } else {
                            resultObj.put("result", "Terminal Snapshot:\n" + snapshot);
                        }
                    }
                }
            } catch (Exception e) {
                LogViewerService.getInstance().w(TAG, "Failed to capture terminal snapshot after delay: " + e.getMessage());
            }
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
