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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class GeminiAiProvider extends AbstractAiProvider {

    private static final String TAG = "GeminiAiProvider";

    private static final List<String> FALLBACK_MODELS = Arrays.asList(
            "gemini-1.5-flash",
            "gemini-1.5-pro",
            "gemini-2.0-flash",
            "gemini-flash-latest",
            "gemini-pro-latest"
    );

    @Override
    public String getName() {
        return "Gemini";
    }

    @Override
    public List<String> getAvailableModels() {
        synchronized (mAvailableModels) {
            if (mAvailableModels.isEmpty()) {
                return new ArrayList<>(FALLBACK_MODELS);
            }
            return new ArrayList<>(mAvailableModels);
        }
    }

    @Override
    public String getDefaultModel() {
        return "gemini-1.5-flash";
    }

    public static String loadBaseSystemInstruction(Context context) {
        return new GeminiAiProvider().getSystemInstruction(context);
    }

    @Override
    public List<String> fetchAvailableModelsSync(String apiKey) throws Exception {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new Exception("Gemini API Key is missing or empty.");
        }

        String encodedKey = URLEncoder.encode(apiKey, "UTF-8");
        String rawResponse = executeHttpGet("https://generativelanguage.googleapis.com/v1beta/models?key=" + encodedKey, apiKey);
        int statusCode = extractStatusCode(rawResponse);
        String body = extractBody(rawResponse);

        if (statusCode >= 200 && statusCode < 300) {
            List<String> modelsList = new ArrayList<>();
            JSONObject json = new JSONObject(body);
            if (json.has("models")) {
                JSONArray modelsArr = json.getJSONArray("models");
                for (int i = 0; i < modelsArr.length(); i++) {
                    JSONObject mObj = modelsArr.getJSONObject(i);
                    String rawName = mObj.optString("name", "");
                    if (rawName.startsWith("models/")) {
                        rawName = rawName.substring(7);
                    }
                    boolean supportsGen = false;
                    if (mObj.has("supportedGenerationMethods")) {
                        JSONArray methods = mObj.getJSONArray("supportedGenerationMethods");
                        for (int j = 0; j < methods.length(); j++) {
                            if ("generateContent".equalsIgnoreCase(methods.getString(j))) {
                                supportsGen = true;
                                break;
                            }
                        }
                    } else {
                        supportsGen = true;
                    }

                    if (supportsGen && !rawName.isEmpty()) {
                        modelsList.add(rawName);
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
    public String getBillingInfo(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            return "No API Key configured.";
        }
        return "Google AI Studio Key Active";
    }

    @Override
    public AiResponse generateContent(Context context, String apiKey, String selectedModel, String systemInstruction, List<ChatMessage> history) {
        if (apiKey == null || apiKey.isEmpty()) {
            return AiResponse.error(401, "Error: Gemini API Key is missing or empty.");
        }

        String targetModel = (selectedModel != null && !selectedModel.trim().isEmpty())
                ? selectedModel.trim()
                : getDefaultModel();

        if (targetModel.startsWith("models/")) {
            targetModel = targetModel.substring(7);
        }

        String rawResponse = executeHttpRequest(apiKey, targetModel, systemInstruction, history);
        int statusCode = extractStatusCode(rawResponse);

        // Fallback try if target model returns 404 or 400 model-not-found
        if (statusCode == 404 || statusCode == 400) {
            String body = extractBody(rawResponse);
            if (body.contains("not found") || body.contains("not supported") || statusCode == 404) {
                for (String fallbackModel : FALLBACK_MODELS) {
                    if (!fallbackModel.equalsIgnoreCase(targetModel)) {
                        LogViewerService.getInstance().w(TAG, "Model " + targetModel + " failed (" + statusCode + "), attempting fallback model " + fallbackModel);
                        rawResponse = executeHttpRequest(apiKey, fallbackModel, systemInstruction, history);
                        statusCode = extractStatusCode(rawResponse);
                        if (statusCode >= 200 && statusCode < 300) {
                            break;
                        }
                    }
                }
            }
        }

        if (statusCode >= 200 && statusCode < 300) {
            String rawJson = extractBody(rawResponse);
            int tokensUsed = 0;
            try {
                JSONObject json = new JSONObject(rawJson);
                if (json.has("usageMetadata")) {
                    JSONObject usageObj = json.getJSONObject("usageMetadata");
                    tokensUsed = usageObj.optInt("totalTokenCount", 0);
                }
            } catch (Exception ignored) {}

            String content = parseGeminiResponse(rawResponse);
            if (tokensUsed <= 0) {
                tokensUsed = calculateTokenUsage(systemInstruction, history, content);
            }
            return AiResponse.success(content, tokensUsed);
        } else {
            String errorMessage = "Error (" + statusCode + "): " + parseErrorResponse(extractBody(rawResponse), statusCode);
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
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            if (apiKey != null && !apiKey.isEmpty()) {
                conn.setRequestProperty("x-goog-api-key", apiKey);
            }

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
            return "HTTP_CODE:500\nNetwork error: " + (e.getMessage() != null ? e.getMessage() : "Unable to reach Gemini API");
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private String executeHttpRequest(String apiKey, String modelName, String systemInstruction, List<ChatMessage> history) {
        HttpURLConnection conn = null;
        try {
            String cleanModel = (modelName != null && modelName.startsWith("models/")) ? modelName.substring(7) : modelName;
            String encodedKey = URLEncoder.encode(apiKey, "UTF-8");
            URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/" + cleanModel + ":generateContent?key=" + encodedKey);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("x-goog-api-key", apiKey);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setDoOutput(true);

            JSONArray contentsArray = new JSONArray();
            if (history != null && !history.isEmpty()) {
                String lastRole = null;
                JSONObject currentContentObj = null;
                JSONArray currentParts = null;
                StringBuilder currentText = null;

                for (ChatMessage msg : history) {
                    if (msg == null || msg.getText() == null || msg.getText().trim().isEmpty()) {
                        continue;
                    }
                    String role = msg.isUser() ? "user" : "model";
                    String text = msg.getText();

                    if (role.equals(lastRole) && currentParts != null) {
                        currentText.append("\n\n").append(text);
                        currentParts.getJSONObject(0).put("text", currentText.toString());
                    } else {
                        lastRole = role;
                        currentText = new StringBuilder(text);

                        JSONObject textPart = new JSONObject();
                        textPart.put("text", text);

                        currentParts = new JSONArray();
                        currentParts.put(textPart);

                        currentContentObj = new JSONObject();
                        currentContentObj.put("role", role);
                        currentContentObj.put("parts", currentParts);

                        contentsArray.put(currentContentObj);
                    }
                }

                // Gemini API requires first content turn to be "user"
                if (contentsArray.length() > 0) {
                    JSONObject first = contentsArray.getJSONObject(0);
                    if (!"user".equals(first.optString("role"))) {
                        first.put("role", "user");
                    }
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

            String requestBody = payload.toString();

            // Log Request Details
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

            return "HTTP_CODE:" + responseCode + "\n" + responseBody;

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
            String rawJson = extractBody(rawResult);
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

    private String parseErrorResponse(String rawJson, int statusCode) {
        if (rawJson != null && !rawJson.trim().isEmpty()) {
            try {
                JSONObject json = new JSONObject(rawJson);
                if (json.has("error")) {
                    JSONObject errorObj = json.getJSONObject("error");
                    String message = errorObj.optString("message", null);
                    String status = errorObj.optString("status", null);

                    if ("API_KEY_INVALID".equalsIgnoreCase(status) || (message != null && message.toLowerCase().contains("api key not valid"))) {
                        return "Invalid Gemini API Key. Please check your token.";
                    }
                    if ("RESOURCE_EXHAUSTED".equalsIgnoreCase(status) || (message != null && message.toLowerCase().contains("quota"))) {
                        return "Quota exceeded or API not billed. Please check your Google AI account quota.";
                    }
                    if (message != null && !message.trim().isEmpty()) {
                        return message.trim();
                    }
                }
            } catch (Exception ignored) {}
        }
        switch (statusCode) {
            case 400: return "Bad request or invalid parameters (HTTP 400).";
            case 401:
            case 403: return "Gemini API Key unauthorized or permission denied (HTTP " + statusCode + ").";
            case 404: return "Requested Gemini model not found (HTTP 404).";
            case 429: return "Rate limit or quota exceeded (HTTP 429).";
            default: return "Gemini request failed (HTTP " + statusCode + "). Check network or API Key.";
        }
    }
}
