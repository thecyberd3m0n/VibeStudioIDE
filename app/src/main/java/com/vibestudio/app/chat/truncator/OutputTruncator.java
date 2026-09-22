package com.vibestudio.app.chat.truncator;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;

public class OutputTruncator {

    public static final int MAX_OUTPUT_BYTES = 25000; // 25KB threshold from gemini.sh

    public static JSONObject truncateOutput(JSONObject toolResultObj) {
        if (toolResultObj == null) return new JSONObject();

        try {
            String jsonString = toolResultObj.toString(2);
            byte[] bytes = jsonString.getBytes(StandardCharsets.UTF_8);

            if (bytes.length > MAX_OUTPUT_BYTES) {
                int keepLength = MAX_OUTPUT_BYTES / 2;
                String startPart = new String(bytes, 0, keepLength, StandardCharsets.UTF_8);
                String endPart = new String(bytes, bytes.length - keepLength, keepLength, StandardCharsets.UTF_8);

                JSONObject truncated = new JSONObject();
                truncated.put("status", toolResultObj.optString("status", "truncated"));
                truncated.put("truncated", true);
                truncated.put("original_size_bytes", bytes.length);
                truncated.put("output", startPart + "\n\n[... Output Truncated (" + (bytes.length - MAX_OUTPUT_BYTES) + " bytes removed) ...]\n\n" + endPart);
                return truncated;
            }
        } catch (Exception ignored) {}

        return toolResultObj;
    }
}
