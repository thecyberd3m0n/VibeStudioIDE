package com.vibestudio.app.chat.tool;

import org.json.JSONObject;

public class ToolCallParser {

    public static JSONObject parse(String rawText) {
        if (rawText == null) return null;

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
            } catch (Exception ignored) {}
        }
        return null;
    }
}
