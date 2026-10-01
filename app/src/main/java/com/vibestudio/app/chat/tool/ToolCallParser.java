package com.vibestudio.app.chat.tool;

import com.vibestudio.app.chat.model.ToolCall;

import org.json.JSONObject;

public class ToolCallParser {

    public static ToolCall parse(String rawText) {
        if (rawText == null) return null;

        if (rawText.contains("{") && rawText.contains("\"tool\"")) {
            try {
                int jsonStart = rawText.indexOf("{");
                int jsonEnd = rawText.lastIndexOf("}");
                if (jsonStart != -1 && jsonEnd > jsonStart) {
                    String jsonBlock = rawText.substring(jsonStart, jsonEnd + 1).trim();
                    JSONObject toolCallObj = new JSONObject(jsonBlock);
                    String toolName = toolCallObj.optString("tool", "");
                    long plannedDelay = toolCallObj.optLong("planned_delay_ms", 0L);
                    JSONObject args = toolCallObj.optJSONObject("args");
                    if (args == null) {
                        args = new JSONObject();
                    }
                    if (!toolName.isEmpty()) {
                        return new ToolCall(toolName, args, jsonBlock, plannedDelay);
                    }
                }
            } catch (Exception ignored) {}
        }
        return null;
    }
}
