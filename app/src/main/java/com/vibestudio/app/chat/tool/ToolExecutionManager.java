package com.vibestudio.app.chat.tool;

import android.content.Context;

import com.vibestudio.app.chat.model.ToolResult;
import com.vibestudio.app.chat.safety.WorkspaceBoundaryGuard;
import com.vibestudio.app.chat.safety.WorkspaceChangesetTracker;
import com.vibestudio.app.chat.truncator.OutputTruncator;
import com.vibestudio.app.mcp.McpClientManager;
import com.vibestudio.app.mcp.McpServer;
import com.vibestudio.app.service.ChatService;
import com.vibestudio.app.service.LogViewerService;

import org.json.JSONObject;

public class ToolExecutionManager {

    private static final String TAG = "ToolExecutionManager";

    public static class PreparedToolCall {
        private final JSONObject toolCallObj;
        private final String toolName;
        private final JSONObject args;
        private final CharSequence formattedText;

        public PreparedToolCall(JSONObject toolCallObj, String toolName, JSONObject args, CharSequence formattedText) {
            this.toolCallObj = toolCallObj;
            this.toolName = toolName;
            this.args = args;
            this.formattedText = formattedText;
        }

        public JSONObject getToolCallObj() {
            return toolCallObj;
        }

        public String getToolName() {
            return toolName;
        }

        public JSONObject getArgs() {
            return args;
        }

        public CharSequence getFormattedText() {
            return formattedText;
        }
    }

    private final McpClientManager mMcpClientManager;

    public ToolExecutionManager(McpClientManager mcpClientManager) {
        this.mMcpClientManager = mcpClientManager;
    }

    private static JSONObject parseToolCallInternal(String rawText) {
        if (rawText == null || rawText.trim().isEmpty()) return null;

        // 1. Standard JSON format: {"planned_delay_ms": 0, "tool": "tool_name", "args": {...}}
        if (rawText.contains("{") && rawText.contains("\"tool\"")) {
            try {
                int jsonStart = rawText.indexOf("{");
                int jsonEnd = rawText.lastIndexOf("}");
                if (jsonStart != -1 && jsonEnd > jsonStart) {
                    String jsonBlock = rawText.substring(jsonStart, jsonEnd + 1).trim();
                    JSONObject toolCallObj = new JSONObject(jsonBlock);
                    String toolName = toolCallObj.optString("tool", "");
                    if (!toolName.isEmpty()) {
                        return toolCallObj;
                    }
                }
            } catch (Exception e) {
                LogViewerService.getInstance().w(TAG, "Failed parsing standard JSON tool call: " + e.getMessage());
            }
        }

        // 2. Malformed function call fallback: e.g. "call:editor:editor_read_file{"path":"..."}" or "call:file_list_directory{"path":"..."}"
        if (rawText.contains("call:") && rawText.contains("{")) {
            try {
                int callIndex = rawText.indexOf("call:");
                int jsonStart = rawText.indexOf("{", callIndex);
                int jsonEnd = rawText.lastIndexOf("}");
                if (jsonStart != -1 && jsonEnd > jsonStart && jsonStart > callIndex) {
                    String callPrefix = rawText.substring(callIndex + 5, jsonStart).trim();
                    String toolName = callPrefix;
                    if (toolName.contains(":")) {
                        toolName = toolName.substring(toolName.lastIndexOf(":") + 1).trim();
                    }

                    if (!toolName.isEmpty()) {
                        String argsJsonStr = rawText.substring(jsonStart, jsonEnd + 1).trim();
                        JSONObject argsObj = new JSONObject(argsJsonStr);

                        JSONObject result = new JSONObject();
                        result.put("planned_delay_ms", 0);
                        result.put("tool", toolName);
                        result.put("args", argsObj);

                        LogViewerService.getInstance().i(TAG, "Successfully recovered malformed function call for tool: " + toolName);
                        return result;
                    }
                }
            } catch (Exception e) {
                LogViewerService.getInstance().w(TAG, "Failed parsing malformed call syntax: " + e.getMessage());
            }
        }

        return null;
    }

    public PreparedToolCall prepareToolCall(Context context, String rawReply, String workspacePath, WorkspaceChangesetTracker tracker) throws Exception {
        JSONObject toolCallObj = parseToolCallInternal(rawReply);
        if (toolCallObj == null) {
            return null;
        }

        String toolName = toolCallObj.optString("tool", "unknown");
        JSONObject args = toolCallObj.optJSONObject("args");
        if (args == null) {
            args = new JSONObject();
            toolCallObj.put("args", args);
        }

        if (workspacePath != null && !workspacePath.isEmpty()) {
            WorkspaceBoundaryGuard.canonicalizeToolArgs(toolName, args, workspacePath);
        }

        CharSequence formattedText = null;
        if (mMcpClientManager != null) {
            McpServer server = mMcpClientManager.findServerForTool(toolName);
            if (server != null) {
                formattedText = server.prepareToolCall(toolName, args, context, tracker);
            }
        }

        return new PreparedToolCall(toolCallObj, toolName, args, formattedText);
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
        if (plannedDelayMs > 0 && !mMcpClientManager.supportsPlannedDelay(toolName)) {
            LogViewerService.getInstance().i(TAG, "Reducing planned delay to 0ms for simple/synchronous tool: " + toolName);
            plannedDelayMs = 0L;
        }

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

        // Perform planned delay AFTER executing tool call if supported & requested
        if (plannedDelayMs > 0) {
            LogViewerService.getInstance().i(TAG, "Executing planned delay of " + plannedDelayMs + " ms after dispatching tool: " + toolName);
            performDelay(plannedDelayMs);
        }

        try {
            resultObj = mMcpClientManager.onToolCallCompleted(toolName, args, context, resultObj);
        } catch (Throwable t) {
            LogViewerService.getInstance().e(TAG, "Exception completing tool '" + toolName + "': " + t.getMessage(), t);
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
