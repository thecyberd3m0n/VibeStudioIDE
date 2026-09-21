package com.vibestudio.app.mcp;

import android.content.Context;
import com.vibestudio.app.mcp.model.McpTool;
import com.vibestudio.app.mcp.model.McpToolResult;

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
}
