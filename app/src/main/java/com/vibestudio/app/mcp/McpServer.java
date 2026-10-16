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
