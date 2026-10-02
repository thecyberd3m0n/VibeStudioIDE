package com.vibestudio.app.chat.tool;

import android.content.Context;

import com.vibestudio.app.chat.model.ToolCall;
import com.vibestudio.app.chat.model.ToolResult;
import com.vibestudio.app.chat.truncator.OutputTruncator;
import com.vibestudio.app.fragments.BrowserFragment;
import com.vibestudio.app.fragments.TerminalFragment;
import com.vibestudio.app.mcp.McpClientManager;
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

        if (plannedDelayMs > 0) {
            LogViewerService.getInstance().i(TAG, "Agent requested planned delay of " + plannedDelayMs + " ms for tool: " + toolName);
        }

        // Auto-open tab in background when Agent uses something, without stealing focus from User
        ensureToolTabOpened(toolName);

        JSONObject resultObj = mMcpClientManager.executeToolCall(toolName, toolCall.getArguments(), context);

        // Delay execution response by 10ms to allow tool state update
        try {
            Thread.sleep(DEFAULT_TOOL_WAIT_MS);
        } catch (InterruptedException ignored) {}

        // Apply 25KB output truncation rule
        JSONObject truncatedObj = OutputTruncator.truncateOutput(resultObj);

        return new ToolResult(toolName, truncatedObj);
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
