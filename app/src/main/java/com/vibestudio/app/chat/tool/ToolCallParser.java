package com.vibestudio.app.chat.tool;

import org.json.JSONObject;
import com.vibestudio.app.service.LogViewerService;

public class ToolCallParser {

    private static final String TAG = "ToolCallParser";

    public static JSONObject parse(String rawText) {
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
}
