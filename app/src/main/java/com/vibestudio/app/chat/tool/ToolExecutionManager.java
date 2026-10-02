package com.vibestudio.app.chat.tool;

import android.content.Context;

import com.vibestudio.app.chat.model.ToolCall;
import com.vibestudio.app.chat.model.ToolResult;
import com.vibestudio.app.chat.truncator.OutputTruncator;
import com.vibestudio.app.fragments.BrowserFragment;
import com.vibestudio.app.fragments.TerminalFragment;
import com.vibestudio.app.mcp.McpClientManager;
import com.vibestudio.app.service.ChatService;
import com.vibestudio.app.service.LogViewerService;
import com.vibestudio.app.tab.TabItem;
import com.vibestudio.app.tab.TabManager;
import com.vibestudio.app.tab.TabType;

import org.json.JSONObject;

import java.util.UUID;

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

        // Auto-open tab in background when Agent uses something, without stealing focus from User
        ensureToolTabOpened(toolName);

        boolean isReadout = isReadoutTool(toolName);

        // For readout tools (e.g. read_terminal_output, read_browser_logs), perform planned delay BEFORE executing readout
        if (isReadout && plannedDelayMs > 0) {
            LogViewerService.getInstance().i(TAG, "Executing pre-delay of " + plannedDelayMs + " ms for readout tool: " + toolName);
            performDelay(plannedDelayMs);
        }

        JSONObject resultObj = mMcpClientManager.executeToolCall(toolName, toolCall.getArguments(), context);

        // For action tools (e.g. execute_command, send_keystroke), perform planned delay AFTER executing action
        if (!isReadout && plannedDelayMs > 0) {
            LogViewerService.getInstance().i(TAG, "Executing post-delay of " + plannedDelayMs + " ms for action tool: " + toolName);
            performDelay(plannedDelayMs);
        } else if (plannedDelayMs <= 0) {
            performDelay(DEFAULT_TOOL_WAIT_MS);
        }

        // Apply 25KB output truncation rule
        JSONObject truncatedObj = OutputTruncator.truncateOutput(resultObj);

        return new ToolResult(toolName, truncatedObj);
    }

    private boolean isReadoutTool(String toolName) {
        if (toolName == null) return false;
        String name = toolName.toLowerCase();
        return name.contains("read") || name.contains("get_") || name.contains("logs") || name.contains("screenshot") || name.contains("status");
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

    private void ensureToolTabOpened(String toolName) {
        if (toolName == null) return;

        TabManager tabManager = TabManager.getInstance();

        if ("execute_command".equals(toolName) || "send_keystroke".equals(toolName) || "read_terminal_output".equals(toolName)) {
            TabItem terminalTab = tabManager.findTabByType(TabType.TERMINAL);
            if (terminalTab == null) {
                String sessionId = "term_" + UUID.randomUUID().toString().substring(0, 8);
                tabManager.openTab(TabType.TERMINAL, null, null, TerminalFragment.newInstance(sessionId), false);
            }
        } else if (toolName.startsWith("browser_") || "read_browser_logs".equals(toolName)) {
            TabItem browserTab = tabManager.findTabByType(TabType.BROWSER);
            if (browserTab == null) {
                String sessionId = "browser_" + UUID.randomUUID().toString().substring(0, 8);
                tabManager.openTab(TabType.BROWSER, null, null, BrowserFragment.newInstance(sessionId), false);
            }
        }
    }
}
