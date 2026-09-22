package com.vibestudio.app.chat.provider;

import android.content.Context;

import com.vibestudio.app.chat.model.AiResponse;
import com.vibestudio.app.chat.model.ChatMessage;
import com.vibestudio.app.service.LogViewerService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class GeminiAiProvider implements AiProvider {

    private static final String TAG = "GeminiAiProvider";

    private static final String[] CANDIDATE_MODELS = new String[] {
            "gemini-flash-latest",
            "gemini-pro-latest",
            "gemini-2.5-flash",
            "gemini-2.5-pro"
    };

    @Override
    public String getName() {
        return "Gemini";
    }

    public static String loadBaseSystemInstruction(Context context) {
        if (context != null) {
            try (InputStream in = context.getAssets().open("ai/system_instruction.md");
                 BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                return sb.toString();
            } catch (Exception e) {
                LogViewerService.getInstance().w(TAG, "Failed to load system_instruction.md from assets/ai", e);
            }
        }
        return "You are VibeStudio Assistant, an intelligent AI coding assistant integrated directly into VibeStudio Android IDE.\n";
    }

    @Override
    public AiResponse generateContent(Context context, String apiKey, String systemInstruction, List<ChatMessage> history) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return AiResponse.error(401, "Error: Gemini API Key is missing or empty.");
        }

        String rawResponse = "";
        int statusCode = 500;

        for (String model : CANDIDATE_MODELS) {
            rawResponse = executeHttpRequest(apiKey.trim(), model, systemInstruction, history);
            statusCode = extractStatusCode(rawResponse);

            if (statusCode != 404) {
                break;
            }
            LogViewerService.getInstance().w(TAG, "Model " + model + " returned 404, attempting next fallback model...");
        }

        if (statusCode >= 200 && statusCode < 300) {
            String content = parseGeminiResponse(rawResponse);
            return AiResponse.success(content);
        } else {
            String errorMessage = "Error (" + statusCode + "): " + parseErrorResponse(rawResponse);
            return AiResponse.error(statusCode, errorMessage);
        }
    }

    private int extractStatusCode(String rawResult) {
        if (rawResult != null && rawResult.startsWith("HTTP_CODE:")) {
            try {
                int index = rawResult.indexOf("\n");
                if (index != -1) {
                    return Integer.parseInt(rawResult.substring(10, index).trim());
                }
            } catch (Exception ignored) {}
        }
        return 500;
    }

    private String executeHttpRequest(String apiKey, String modelName, String systemInstruction, List<ChatMessage> history) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/" + modelName + ":generateContent?key=" + apiKey);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setDoOutput(true);

            JSONArray contentsArray = new JSONArray();
            if (history != null) {
                for (ChatMessage msg : history) {
                    String role = msg.isUser() ? "user" : "model";

                    JSONObject textPart = new JSONObject();
                    textPart.put("text", msg.getText());

                    JSONArray parts = new JSONArray();
                    parts.put(textPart);

                    JSONObject contentObj = new JSONObject();
                    contentObj.put("role", role);
                    contentObj.put("parts", parts);

                    contentsArray.put(contentObj);
                }
            }

            JSONObject payload = new JSONObject();

            if (systemInstruction != null && !systemInstruction.trim().isEmpty()) {
                JSONObject sysTextPart = new JSONObject();
                sysTextPart.put("text", systemInstruction);

                JSONArray sysParts = new JSONArray();
                sysParts.put(sysTextPart);

                JSONObject systemInstructionObj = new JSONObject();
                systemInstructionObj.put("parts", sysParts);

                payload.put("system_instruction", systemInstructionObj);
            }

            payload.put("contents", contentsArray);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = payload.toString().getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            LogViewerService.getInstance().d(TAG, "Gemini Chat API [" + modelName + "] HTTP response code: " + responseCode);

            InputStream is = (responseCode >= 200 && responseCode < 300) ? conn.getInputStream() : conn.getErrorStream();
            StringBuilder responseSb = new StringBuilder();
            if (is != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        responseSb.append(line);
                    }
                }
            }

            return "HTTP_CODE:" + responseCode + "\n" + responseSb.toString();

        } catch (Exception e) {
            LogViewerService.getInstance().e(TAG, "Exception during Gemini Chat request for model " + modelName, e);
            return "HTTP_CODE:500\nNetwork error: " + (e.getMessage() != null ? e.getMessage() : "Unable to reach Gemini API");
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private String parseGeminiResponse(String rawResult) {
        try {
            int bodyIndex = rawResult.indexOf("\n");
            String rawJson = (bodyIndex != -1) ? rawResult.substring(bodyIndex + 1) : rawResult;
            JSONObject json = new JSONObject(rawJson);
            JSONArray candidates = json.getJSONArray("candidates");
            if (candidates.length() > 0) {
                JSONObject firstCand = candidates.getJSONObject(0);
                JSONObject content = firstCand.getJSONObject("content");
                JSONArray parts = content.getJSONArray("parts");
                if (parts.length() > 0) {
                    JSONObject firstPart = parts.getJSONObject(0);
                    return firstPart.getString("text");
                }
            }
        } catch (Exception e) {
            LogViewerService.getInstance().e(TAG, "Error parsing Gemini response JSON", e);
        }
        return "No response text generated.";
    }

    private String parseErrorResponse(String rawResult) {
        try {
            int bodyIndex = rawResult.indexOf("\n");
            String rawJson = (bodyIndex != -1) ? rawResult.substring(bodyIndex + 1) : rawResult;
            JSONObject json = new JSONObject(rawJson);
            if (json.has("error")) {
                JSONObject err = json.getJSONObject("error");
                return err.optString("message", "Unknown error");
            }
        } catch (Exception ignored) {}
        return "Failed to process request.";
    }
}
