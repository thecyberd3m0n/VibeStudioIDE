package com.vibestudio.app.mcp;

import com.vibestudio.app.service.LogViewerService;
import com.vibestudio.app.terminal.TerminalSessionManager;

import org.json.JSONArray;
import org.json.JSONObject;

public class TerminalMcpServer {

    private static final String TAG = "TerminalMcpServer";
    private boolean mIsActive = false;

    public TerminalMcpServer() {}

    public void startServer() {
        mIsActive = true;
        LogViewerService.getInstance().i(TAG, "Terminal MCP Server started and active.");
    }

    public void stopServer() {
        mIsActive = false;
        LogViewerService.getInstance().i(TAG, "Terminal MCP Server stopped.");
    }

    public boolean isActive() {
        return mIsActive;
    }

    public JSONArray getToolsListSchema() {
        JSONArray tools = new JSONArray();
        try {
            // Tool 1: execute_command
            JSONObject executeTool = new JSONObject();
            executeTool.put("name", "execute_command");
            executeTool.put("description", "Execute a bash shell command in the shared active terminal session.");

            JSONObject execParams = new JSONObject();
            execParams.put("type", "object");

            JSONObject execProps = new JSONObject();

            JSONObject cmdProp = new JSONObject();
            cmdProp.put("type", "string");
            cmdProp.put("description", "The shell command string to execute.");
            execProps.put("command", cmdProp);

            execParams.put("properties", execProps);
            JSONArray execRequired = new JSONArray();
            execRequired.put("command");
            execParams.put("required", execRequired);

            executeTool.put("inputSchema", execParams);
            tools.put(executeTool);

            // Tool 2: read_terminal_output
            JSONObject readTool = new JSONObject();
            readTool.put("name", "read_terminal_output");
            readTool.put("description", "Read a paginated or filtered output fragment from the active terminal session screen buffer.");

            JSONObject readParams = new JSONObject();
            readParams.put("type", "object");

            JSONObject readProps = new JSONObject();

            JSONObject maxLinesProp = new JSONObject();
            maxLinesProp.put("type", "integer");
            maxLinesProp.put("description", "Maximum number of lines to return (default: 30 to conserve tokens).");
            readProps.put("max_lines", maxLinesProp);

            JSONObject startLineProp = new JSONObject();
            startLineProp.put("type", "integer");
            startLineProp.put("description", "Start line index for pagination (default: 0).");
            readProps.put("start_line", startLineProp);

            JSONObject tailOnlyProp = new JSONObject();
            tailOnlyProp.put("type", "boolean");
            tailOnlyProp.put("description", "If true, returns only the latest trailing lines (default: true).");
            readProps.put("tail_only", tailOnlyProp);

            JSONObject grepProp = new JSONObject();
            grepProp.put("type", "string");
            grepProp.put("description", "Optional search substring to filter matching terminal lines.");
            readProps.put("grep_pattern", grepProp);

            readParams.put("properties", readProps);
            readTool.put("inputSchema", readParams);
            tools.put(readTool);

        } catch (Exception e) {
            LogViewerService.getInstance().e(TAG, "Error building Terminal MCP tools schema", e);
        }
        return tools;
    }

    public JSONObject callTool(String toolName, JSONObject args) {
        JSONObject result = new JSONObject();
        try {
            if ("execute_command".equals(toolName)) {
                String command = args.optString("command", "");
                if (command.isEmpty()) {
                    result.put("status", "error");
                    result.put("message", "Missing 'command' parameter.");
                    return result;
                }

                boolean success = TerminalSessionManager.getInstance().executeCommand(command);
                result.put("status", success ? "success" : "failed");
                result.put("message", success ? "Command dispatched to terminal session." : "Failed to dispatch command.");
                return result;

            } else if ("read_terminal_output".equals(toolName)) {
                int maxLines = args.optInt("max_lines", 30);
                int startLine = args.optInt("start_line", 0);
                boolean tailOnly = args.optBoolean("tail_only", true);
                String grepPattern = args.optString("grep_pattern", null);

                String output = TerminalSessionManager.getInstance().readTerminalOutput(maxLines, startLine, tailOnly, grepPattern);
                result.put("status", "success");
                result.put("output", output);
                return result;

            } else {
                result.put("status", "error");
                result.put("message", "Unknown tool: " + toolName);
                return result;
            }
        } catch (Exception e) {
            LogViewerService.getInstance().e(TAG, "Error executing MCP tool: " + toolName, e);
            try {
                result.put("status", "error");
                result.put("message", e.getMessage());
            } catch (Exception ignored) {}
            return result;
        }
    }
}
