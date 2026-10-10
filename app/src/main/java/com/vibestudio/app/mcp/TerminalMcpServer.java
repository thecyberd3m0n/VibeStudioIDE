package com.vibestudio.app.mcp;

import android.content.Context;

import com.termux.terminal.TerminalSession;
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
    private static final String DESCRIPTION = "Allows running shell commands in active or specific IDE terminal sessions, injecting keystrokes (such as 'n\\n', 'y\\n', ENTER, 'CTRL+C'), and inspecting terminal logs. Use 'terminal_open_tab' to explicitly create a new terminal session.";

    private boolean mIsActive = false;
    private final List<McpTool> mTools;

    public TerminalMcpServer() {
        mTools = new ArrayList<>();

        // Tool 0: terminal_open_tab
        mTools.add(McpTool.builder("terminal_open_tab", "Open a new terminal tab for the AI agent to interact with.")
                .build());

        // Tool 1: execute_command
        mTools.add(McpTool.builder("execute_command", "Execute a bash shell command in a terminal session. Useful for non-interactive commands.")
                .addProperty("command", PropertyType.STRING, "The shell command string to execute.", true)
                .addProperty("session_id", PropertyType.INTEGER, "Optional target terminal session ID (integer, e.g. 1).", false)
                .build());

        // Tool 2: send_keystroke
        mTools.add(McpTool.builder("send_keystroke", "Inject raw keystrokes, keycodes, arrow keys, control sequences, or raw characters into a terminal session.")
                .addProperty("keystroke", PropertyType.STRING, "The keystroke string, named key ('UP', 'DOWN', 'LEFT', 'RIGHT', 'TAB', 'ESC', 'BACKSPACE', 'ENTER', 'CTRL+C', 'PAGE_UP', 'HOME', 'END', 'F1'-'F12'), or raw sequence (e.g., '\\u001b[A').", false)
                .addProperty("key_code", PropertyType.INTEGER, "Optional Android KeyEvent keycode integer (e.g. 19 for UP, 20 for DOWN, 21 for LEFT, 22 for RIGHT, 61 for TAB, 66 for ENTER, 111 for ESC).", false)
                .addProperty("session_id", PropertyType.INTEGER, "Optional target terminal session ID (integer, e.g. 1).", false)
                .build());

        // Tool 3: read_terminal_output
        mTools.add(McpTool.builder("read_terminal_output", "Read a paginated or filtered output fragment from a terminal session screen buffer.")
                .addProperty("max_lines", PropertyType.INTEGER, "Maximum number of lines to return (default: 30 to conserve tokens).", false)
                .addProperty("start_line", PropertyType.INTEGER, "Start line index for pagination (default: 0).", false)
                .addProperty("tail_only", PropertyType.BOOLEAN, "If true, returns only the latest trailing lines (default: true).", false)
                .addProperty("grep_pattern", PropertyType.STRING, "Optional search substring to filter matching terminal lines.", false)
                .addProperty("session_id", PropertyType.INTEGER, "Optional target terminal session ID (integer, e.g. 1).", false)
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
            ToolSessionManager sessionManager = ToolSessionManager.getInstance();

            if ("terminal_open_tab".equals(toolName) || "open_tab".equals(toolName)) {
                int newSessionId = sessionManager.getOrCreateSession(ToolType.TERMINAL, context, true);
                return McpToolResult.success("Opened new terminal tab with session_id: " + newSessionId);
            }

            int sessionIdArg = arguments != null ? getIntArg(arguments, "session_id", -1) : -1;
            ToolSessionManager.ToolSession session;

            if (sessionIdArg > 0) {
                // Strict validation when explicit session_id is requested by the Agent
                ToolSessionManager.SessionValidationResult validation = sessionManager.validateSession(sessionIdArg, ToolType.TERMINAL);
                if (!validation.isValid()) {
                    return McpToolResult.error(validation.errorMessage);
                }
                session = validation.session;
            } else {
                // Get or create active default terminal session
                int activeSessionId = sessionManager.getOrCreateSession(ToolType.TERMINAL, context, false);
                session = sessionManager.getSession(activeSessionId);
                if (session == null) {
                    return McpToolResult.error("Failed to initialize active terminal session.");
                }
            }

            TerminalSession termSession = session.getTerminalSession();
            if (termSession == null || !termSession.isRunning()) {
                return McpToolResult.error("Terminal session #" + session.id + " is not running.");
            }

            if ("execute_command".equals(toolName)) {
                String command = arguments != null ? getStringArg(arguments, "command", "") : "";
                if (command.isEmpty()) {
                    return McpToolResult.error("Missing 'command' parameter.");
                }

                boolean success = TerminalSessionManager.getInstance().executeCommand(termSession, command);

                if (success) {
                    return McpToolResult.success("Command dispatched to terminal session #" + session.id + ".");
                } else {
                    return McpToolResult.error("Failed to dispatch command to session #" + session.id + ".");
                }

            } else if ("send_keystroke".equals(toolName)) {
                String keystroke = arguments != null ? getStringArg(arguments, "keystroke", "") : "";
                int keyCode = arguments != null ? getIntArg(arguments, "key_code", -1) : -1;

                if (keystroke.isEmpty() && keyCode <= 0) {
                    return McpToolResult.error("Missing 'keystroke' or 'key_code' parameter.");
                }

                boolean success = TerminalSessionManager.getInstance().sendKeystroke(termSession, keystroke, keyCode);

                if (success) {
                    String info = keyCode > 0 ? "keycode: " + keyCode : keystroke;
                    return McpToolResult.success("Keystroke injected into terminal session #" + session.id + ": " + info);
                } else {
                    return McpToolResult.error("Failed to inject keystroke.");
                }

            } else if ("read_terminal_output".equals(toolName)) {
                int maxLines = arguments != null ? getIntArg(arguments, "max_lines", 30) : 30;
                int startLine = arguments != null ? getIntArg(arguments, "start_line", 0) : 0;
                boolean tailOnly = arguments != null ? getBooleanArg(arguments, "tail_only", true) : true;
                String grepPattern = arguments != null ? getStringArg(arguments, "grep_pattern", null) : null;

                String output = TerminalSessionManager.getInstance().readTerminalOutput(termSession, maxLines, startLine, tailOnly, grepPattern);

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
        return (val != null && !"null".equalsIgnoreCase(val.toString())) ? String.valueOf(val) : defaultValue;
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
