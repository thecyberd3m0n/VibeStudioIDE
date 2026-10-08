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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class OpenAiProvider extends AbstractAiProvider {

    private static final String TAG = "OpenAiProvider";

    @Override
    public String getName() {
        return "OpenAI";
    }

    @Override
    public String getDefaultModel() {
        return "gpt-4o-mini";
    }

    @Override
    public List<String> fetchAvailableModelsSync(String apiKey) throws Exception {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new Exception("OpenAI API Key is missing or empty.");
        }

        String rawResponse = executeHttpGet("https://api.openai.com/v1/models", apiKey);
        int statusCode = extractStatusCode(rawResponse);
        String body = extractBody(rawResponse);

        if (statusCode >= 200 && statusCode < 300) {
            List<String> modelsList = new ArrayList<>();
            JSONObject json = new JSONObject(body);
            if (json.has("data")) {
                JSONArray dataArr = json.getJSONArray("data");
                for (int i = 0; i < dataArr.length(); i++) {
                    JSONObject mObj = dataArr.getJSONObject(i);
                    String id = mObj.optString("id", "");
                    if (id.startsWith("gpt-") || id.startsWith("o1") || id.startsWith("o3") || id.contains("chatgpt")) {
                        modelsList.add(id);
                    }
                }
            }
            if (!modelsList.isEmpty()) {
                setAvailableModels(modelsList);
                return modelsList;
            } else {
                return getAvailableModels();
            }
        } else {
            String errorMsg = parseErrorResponse(body, statusCode);
            throw new Exception(errorMsg);
        }
    }

    @Override
    public BudgetInfo fetchBudgetInfoSync(String apiKey) throws Exception {
        if (apiKey == null || apiKey.isEmpty()) {
            return new BudgetInfo(0, 1000000);
        }

        String rawResponse = executeHttpGet("https://api.openai.com/v1/usage", apiKey);
        int statusCode = extractStatusCode(rawResponse);
        String body = extractBody(rawResponse);

        if (statusCode >= 200 && statusCode < 300) {
            JSONObject json = new JSONObject(body);
            long totalUsage = json.optLong("total_usage", -1);
            if (totalUsage >= 0) {
                return new BudgetInfo(totalUsage, 1000000);
            }
        }
        return new BudgetInfo(0, 1000000);
    }

    @Override
    public String getBillingInfo(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            return "No API Key configured.";
        }
        return "OpenAI Platform Key Active";
    }

    @Override
    public AiResponse generateContent(Context context, String apiKey, String selectedModel, String systemInstruction, List<ChatMessage> history) {
        if (apiKey == null || apiKey.isEmpty()) {
            return AiResponse.error(401, "Error: OpenAI API Key is missing or empty.");
        }

        String targetModel = (selectedModel != null && !selectedModel.trim().isEmpty())
                ? selectedModel.trim()
                : getDefaultModel();

        try {
            URL url = new URL("https://api.openai.com/v1/chat/completions");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setDoOutput(true);

            JSONArray messagesArray = new JSONArray();
            if (systemInstruction != null && !systemInstruction.trim().isEmpty()) {
                JSONObject sysMsg = new JSONObject();
                sysMsg.put("role", "system");
                sysMsg.put("content", systemInstruction);
                messagesArray.put(sysMsg);
            }

            if (history != null) {
                for (ChatMessage msg : history) {
                    JSONObject chatMsg = new JSONObject();
                    chatMsg.put("role", msg.isUser() ? "user" : "assistant");
                    chatMsg.put("content", msg.getText());
                    messagesArray.put(chatMsg);
                }
            }

            JSONObject payload = new JSONObject();
            payload.put("model", targetModel);
            payload.put("messages", messagesArray);

            String requestBody = payload.toString();

            // Log Request Headers and Body
            logRequestDetails(TAG, "POST", url.toString(), conn.getRequestProperties(), requestBody);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = requestBody.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            Map<String, List<String>> responseHeaders = conn.getHeaderFields();
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

            String responseBody = responseSb.toString();
            logResponseDetails(TAG, responseCode, responseHeaders, responseBody);

            if (responseCode >= 200 && responseCode < 300) {
                JSONObject resJson = new JSONObject(responseBody);
                int tokensUsed = 0;
                if (resJson.has("usage")) {
                    JSONObject usageObj = resJson.getJSONObject("usage");
                    tokensUsed = usageObj.optInt("total_tokens", 0);
                }

                JSONArray choices = resJson.getJSONArray("choices");
                if (choices.length() > 0) {
                    JSONObject choice = choices.getJSONObject(0);
                    JSONObject msg = choice.getJSONObject("message");
                    String resContent = msg.getString("content");
                    if (tokensUsed <= 0) {
                        tokensUsed = calculateTokenUsage(systemInstruction, history, resContent);
                    }
                    return AiResponse.success(resContent, tokensUsed);
                }
                return AiResponse.success("No content returned.", tokensUsed);
            } else {
                String errorMsg = parseErrorResponse(responseBody, responseCode);
                return AiResponse.error(responseCode, errorMsg);
            }
        } catch (Exception e) {
            LogViewerService.getInstance().e(TAG, "Exception during OpenAI Chat request for model " + targetModel, e);
            return AiResponse.error(500, "Network error: " + e.getMessage());
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

    private String extractBody(String rawResult) {
        if (rawResult != null) {
            int bodyIndex = rawResult.indexOf("\n");
            return (bodyIndex != -1) ? rawResult.substring(bodyIndex + 1) : rawResult;
        }
        return "";
    }

    private String executeHttpGet(String urlStr, String apiKey) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            logRequestDetails(TAG, "GET", urlStr, conn.getRequestProperties(), null);

            int responseCode = conn.getResponseCode();
            Map<String, List<String>> responseHeaders = conn.getHeaderFields();
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

            String responseBody = responseSb.toString();
            logResponseDetails(TAG, responseCode, responseHeaders, responseBody);
            return "HTTP_CODE:" + responseCode + "\n" + responseBody;
        } catch (Exception e) {
            return "HTTP_CODE:500\nNetwork error: " + (e.getMessage() != null ? e.getMessage() : "Unable to reach OpenAI API");
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private String parseErrorResponse(String rawJson, int statusCode) {
        if (rawJson != null && !rawJson.trim().isEmpty()) {
            try {
                JSONObject json = new JSONObject(rawJson);
                if (json.has("error")) {
                    JSONObject errorObj = json.getJSONObject("error");
                    String message = errorObj.optString("message", null);
                    if (message != null && !message.isEmpty()) {
                        return message;
                    }
                }
            } catch (Exception ignored) {}
        }
        switch (statusCode) {
            case 401: return "Invalid OpenAI API Key. Please check your key.";
            case 429: return "OpenAI quota exceeded or rate limit reached.";
            default: return "OpenAI validation failed (HTTP " + statusCode + ").";
        }
    }
}
