package com.vibestudio.app.mcp;

import android.content.Context;
import com.vibestudio.app.mcp.model.McpTool;
import com.vibestudio.app.mcp.model.McpToolResult;
import com.vibestudio.app.view.BaseToolMessageView;

import org.json.JSONObject;

import java.util.List;
import java.util.Map;

public interface McpServer {
    String getName();
    String getDescription();
    void startServer();
    void stopServer();
    boolean isActive();
    List<McpTool> getTools();
    boolean handlesTool(String toolName);
    McpToolResult callTool(String toolName, Map<String, Object> arguments, Context context);

    default JSONObject onToolCallCompleted(String toolName, Map<String, Object> arguments, Context context, JSONObject result) {
        return result;
    }

    /**
     * Skill priority tier for tool selection:
     * Lower integer = higher priority / simpler tool (e.g. 1 for file/editor, 2 for terminal/browser).
     */
    default int getPriority() {
        return 1;
    }

    /**
     * Indicates whether tools under this server support/require async planned delays.
     * Synchronous Tier 1 servers default to false (0 delay overhead).
     */
    default boolean supportsPlannedDelay() {
        return getPriority() > 1;
    }

    /**
     * Creates a tool message view for chat UI for tools handled by this McpServer.
     */
    BaseToolMessageView createToolMessageView(Context context, String toolName);

    /**
     * Optional custom formatting of tool call messages for chat UI display.
     * Return null to use default fallback formatting.
     */
    default CharSequence formatToolCall(String toolName, JSONObject args) {
        return null;
    }
}
