package com.vibestudio.app.mcp;

import android.content.Context;

import com.vibestudio.app.mcp.model.McpTool;
import com.vibestudio.app.mcp.model.McpToolResult;
import com.vibestudio.app.mcp.model.PropertyType;
import com.vibestudio.app.service.LogViewerService;
import com.vibestudio.app.terminal.TerminalSessionManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TerminalMcpServer implements McpServer {

    private static final String TAG = "TerminalMcpServer";
    private static final String NAME = "terminal";
    private static final String DESCRIPTION = "Allows running shell commands in the active IDE terminal session and inspecting terminal logs.";

    private boolean mIsActive = false;
    private final List<McpTool> mTools;

    public TerminalMcpServer() {
        mTools = new ArrayList<>();

        // Tool 1: execute_command
        mTools.add(McpTool.builder("execute_command", "Execute a bash shell command in the shared active terminal session.")
                .addProperty("command", PropertyType.STRING, "The shell command string to execute.", true)
                .build());

        // Tool 2: read_terminal_output
        mTools.add(McpTool.builder("read_terminal_output", "Read a paginated or filtered output fragment from the active terminal session screen buffer.")
                .addProperty("max_lines", PropertyType.INTEGER, "Maximum number of lines to return (default: 30 to conserve tokens).", false)
                .addProperty("start_line", PropertyType.INTEGER, "Start line index for pagination (default: 0).", false)
                .addProperty("tail_only", PropertyType.BOOLEAN, "If true, returns only the latest trailing lines (default: true).", false)
                .addProperty("grep_pattern", PropertyType.STRING, "Optional search substring to filter matching terminal lines.", false)
                .build());
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public void startServer() {
        mIsActive = true;
        LogViewerService.getInstance().i(TAG, "Terminal MCP Server started and active.");
    }

    @Override
    public void stopServer() {
        mIsActive = false;
        LogViewerService.getInstance().i(TAG, "Terminal MCP Server stopped.");
    }

    @Override
    public boolean isActive() {
        return mIsActive;
    }

    @Override
    public List<McpTool> getTools() {
        return mTools;
    }

    @Override
    public boolean handlesTool(String toolName) {
        if (toolName == null) return false;
        for (McpTool tool : mTools) {
            if (tool.getName().equals(toolName)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public McpToolResult callTool(String toolName, Map<String, Object> arguments, Context context) {
        try {
            if ("execute_command".equals(toolName)) {
                String command = arguments != null ? getStringArg(arguments, "command", "") : "";
                if (command.isEmpty()) {
                    return McpToolResult.error("Missing 'command' parameter.");
                }

                boolean success = TerminalSessionManager.getInstance().executeCommand(command);
                if (success) {
                    return McpToolResult.success("Command dispatched to terminal session.");
                } else {
                    return McpToolResult.error("Failed to dispatch command.");
                }

            } else if ("read_terminal_output".equals(toolName)) {
                int maxLines = arguments != null ? getIntArg(arguments, "max_lines", 30) : 30;
                int startLine = arguments != null ? getIntArg(arguments, "start_line", 0) : 0;
                boolean tailOnly = arguments != null ? getBooleanArg(arguments, "tail_only", true) : true;
                String grepPattern = arguments != null ? getStringArg(arguments, "grep_pattern", null) : null;

                String output = TerminalSessionManager.getInstance().readTerminalOutput(maxLines, startLine, tailOnly, grepPattern);
                return McpToolResult.success(output);

            } else {
                return McpToolResult.error("Unknown tool: " + toolName);
            }
        } catch (Exception e) {
            LogViewerService.getInstance().e(TAG, "Error executing MCP tool: " + toolName, e);
            return McpToolResult.error(e.getMessage());
        }
    }

    private String getStringArg(Map<String, Object> args, String key, String defaultValue) {
        Object val = args.get(key);
        return val != null ? String.valueOf(val) : defaultValue;
    }

    private int getIntArg(Map<String, Object> args, String key, int defaultValue) {
        Object val = args.get(key);
        if (val instanceof Number) {
            return ((Number) val).intValue();
        } else if (val instanceof String) {
            try {
                return Integer.parseInt((String) val);
            } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }

    private boolean getBooleanArg(Map<String, Object> args, String key, boolean defaultValue) {
        Object val = args.get(key);
        if (val instanceof Boolean) {
            return (Boolean) val;
        } else if (val instanceof String) {
            return Boolean.parseBoolean((String) val);
        }
        return defaultValue;
    }
}
