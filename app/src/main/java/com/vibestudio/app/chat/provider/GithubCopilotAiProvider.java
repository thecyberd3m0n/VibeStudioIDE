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
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class GithubCopilotAiProvider extends AbstractAiProvider {

    private static final String TAG = "GithubCopilotAiProvider";
    public static final String CLIENT_ID = "01ab8ac9400c4e429b23";
    private static final String EDITOR_NAME = "VibeStudio";
    private static final String GITHUB_API_VERSION = "2025-10-01";
    // The Copilot API validates Editor-Version against an allow-list bound to the OAuth app that
    // issued the token. CLIENT_ID is GitHub's VS Code app, so only "vscode/<ver>" is accepted here
    // (anything else, including "Zed/" or "VibeStudio/", yields HTTP 400 "unknown Editor-Version").
    // VibeStudio therefore identifies itself through Editor-Plugin-Version and User-Agent instead.
    private static final String REQUIRED_EDITOR_VERSION = "vscode/1.90.0";

    private static volatile String sEditorVersion = EDITOR_NAME + "/unknown";

    private static final List<String> DEFAULT_COPILOT_MODELS = Arrays.asList(
            "gpt-4o",
            "gpt-4o-mini",
            "claude-3.5-sonnet",
            "o3-mini",
            "o1"
    );

    private String mCachedCopilotToken = null;
    private long mCopilotTokenExpiresAt = 0;
    private String mCachedCopilotEndpoint = "https://api.individual.githubcopilot.com";

    public interface DeviceCodeCallback {
        void onSuccess(DeviceCodeResponse response);
        void onError(String errorMessage);
    }

    public interface TokenPollCallback {
        void onSuccess(String accessToken);
        void onError(String errorMessage);
    }

    public static class DeviceCodeResponse {
        public final String deviceCode;
        public final String userCode;
        public final String verificationUri;
        public final int expiresIn;
        public final int interval;

        public DeviceCodeResponse(String deviceCode, String userCode, String verificationUri, int expiresIn, int interval) {
            this.deviceCode = deviceCode;
            this.userCode = userCode;
            this.verificationUri = verificationUri;
            this.expiresIn = expiresIn;
            this.interval = interval;
        }
    }

    @Override
    public String getName() {
        return "GitHub Copilot";
    }

    /** Resolves the app version once so every Copilot request identifies itself as VibeStudio. */
    public static void initEditorVersion(Context context) {
        if (context == null) return;
        try {
            String v = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
            if (v != null && !v.trim().isEmpty()) {
                sEditorVersion = EDITOR_NAME + "/" + v.trim();
            }
        } catch (Exception e) {
            LogViewerService.getInstance().w(TAG, "Unable to resolve app version for Editor-Version header", e);
        }
    }

    public static String getEditorVersion() {
        return sEditorVersion;
    }

    private static void applyEditorHeaders(HttpURLConnection conn) {
        conn.setRequestProperty("User-Agent", sEditorVersion);
        conn.setRequestProperty("Editor-Version", REQUIRED_EDITOR_VERSION);
        conn.setRequestProperty("Editor-Plugin-Version", sEditorVersion);
        conn.setRequestProperty("X-GitHub-Api-Version", GITHUB_API_VERSION);
    }

    @Override
    public String getDefaultModel() {
        return "gpt-4o";
    }

    @Override
    public void setAvailableModels(List<String> models) {
        if (models != null) {
            List<String> filtered = new ArrayList<>();
            for (String m : models) {
                if (m != null && !m.trim().isEmpty()) {
                    String id = m.trim();
                    if (!id.contains("-sol") && !id.contains("embedding") && !id.contains("codex")) {
                        filtered.add(id);
                    }
                }
            }
            super.setAvailableModels(filtered);
            return;
        }
        super.setAvailableModels(models);
    }

    public void requestDeviceCode(final DeviceCodeCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    URL url = new URL("https://github.com/login/device/code");
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Accept", "application/json");
                    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);
                    conn.setDoOutput(true);

                    String body = "client_id=" + CLIENT_ID + "&scope=read:user";
                    try (OutputStream os = conn.getOutputStream()) {
                        os.write(body.getBytes(StandardCharsets.UTF_8));
                    }

                    int code = conn.getResponseCode();
                    InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
                    String resStr = readStream(is);

                    if (code >= 200 && code < 300) {
                        JSONObject json = new JSONObject(resStr);
                        String deviceCode = json.getString("device_code");
                        String userCode = json.getString("user_code");
                        String uri = json.optString("verification_uri", "https://github.com/login/device");
                        int expiresIn = json.optInt("expires_in", 900);
                        int interval = json.optInt("interval", 5);

                        final DeviceCodeResponse resp = new DeviceCodeResponse(deviceCode, userCode, uri, expiresIn, interval);
                        mMainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onSuccess(resp);
                            }
                        });
                    } else {
                        final String err = "Failed to get device code (HTTP " + code + "): " + resStr;
                        mMainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onError(err);
                            }
                        });
                    }
                } catch (final Exception e) {
                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            callback.onError("Device code error: " + e.getMessage());
                        }
                    });
                }
            }
        }).start();
    }

    public void pollAccessToken(final String deviceCode, final int intervalSeconds, final TokenPollCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                long startTime = System.currentTimeMillis();
                long maxDuration = 15 * 60 * 1000;
                int pollInterval = Math.max(2, intervalSeconds) * 1000;

                while (System.currentTimeMillis() - startTime < maxDuration) {
                    try {
                        Thread.sleep(pollInterval);

                        URL url = new URL("https://github.com/login/oauth/access_token");
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("POST");
                        conn.setRequestProperty("Accept", "application/json");
                        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                        conn.setConnectTimeout(10000);
                        conn.setReadTimeout(10000);
                        conn.setDoOutput(true);

                        String body = "client_id=" + CLIENT_ID
                                + "&device_code=" + deviceCode
                                + "&grant_type=urn:ietf:params:oauth:grant-type:device_code";

                        try (OutputStream os = conn.getOutputStream()) {
                            os.write(body.getBytes(StandardCharsets.UTF_8));
                        }

                        int code = conn.getResponseCode();
                        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
                        String resStr = readStream(is);

                        if (code >= 200 && code < 300) {
                            JSONObject json = new JSONObject(resStr);
                            if (json.has("access_token")) {
                                final String accessToken = json.getString("access_token");
                                mMainHandler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        callback.onSuccess(accessToken);
                                    }
                                });
                                return;
                            } else if (json.has("error")) {
                                String err = json.getString("error");
                                if ("authorization_pending".equals(err)) {
                                    continue;
                                } else if ("slow_down".equals(err)) {
                                    pollInterval += 5000;
                                    continue;
                                } else {
                                    final String errDesc = json.optString("error_description", err);
                                    mMainHandler.post(new Runnable() {
                                        @Override
                                        public void run() {
                                            callback.onError("Auth error: " + errDesc);
                                        }
                                    });
                                    return;
                                }
                            }
                        }
                    } catch (InterruptedException ie) {
                        mMainHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                callback.onError("Polling cancelled");
                            }
                        });
                        return;
                    } catch (Exception e) {
                        LogViewerService.getInstance().w(TAG, "Error polling access token", e);
                    }
                }
                mMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onError("Authentication timed out. Please try again.");
                    }
                });
            }
        }).start();
    }

    private synchronized String getOrFetchCopilotToken(String githubOAuthToken) throws Exception {
        long nowSeconds = System.currentTimeMillis() / 1000;
        if (mCachedCopilotToken != null && nowSeconds < (mCopilotTokenExpiresAt - 60)) {
            return mCachedCopilotToken;
        }

        URL url = new URL("https://api.github.com/copilot_internal/v2/token");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "token " + githubOAuthToken);
        applyEditorHeaders(conn);
        conn.setRequestProperty("Accept", "application/json");
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
        String resStr = readStream(is);

        if (code >= 200 && code < 300) {
            JSONObject json = new JSONObject(resStr);
            mCachedCopilotToken = json.getString("token");
            mCopilotTokenExpiresAt = json.optLong("expires_at", nowSeconds + 1500);

            if (json.has("endpoints")) {
                JSONObject ep = json.getJSONObject("endpoints");
                mCachedCopilotEndpoint = ep.optString("api", "https://api.individual.githubcopilot.com");
            }
            return mCachedCopilotToken;
        } else {
            throw new Exception("GitHub Copilot token exchange failed (HTTP " + code + "): " + resStr);
        }
    }

    @Override
    public List<String> fetchAvailableModelsSync(String apiKey) throws Exception {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new Exception("GitHub OAuth Token is missing.");
        }

        String copilotToken = getOrFetchCopilotToken(apiKey);
        try {
            URL url = new URL(mCachedCopilotEndpoint + "/models");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Authorization", "Bearer " + copilotToken);
            conn.setRequestProperty("Accept", "application/json");
            applyEditorHeaders(conn);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);

            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            String resStr = readStream(is);

            if (code >= 200 && code < 300) {
                List<String> modelsList = parseChatModels(resStr);
                if (!modelsList.isEmpty()) {
                    setAvailableModels(modelsList);
                    return modelsList;
                }
                LogViewerService.getInstance().w(TAG, "Copilot /models returned no selectable chat models, using defaults");
            } else {
                LogViewerService.getInstance().w(TAG, "Copilot /models failed (HTTP " + code + "): " + resStr);
            }
        } catch (Exception e) {
            LogViewerService.getInstance().w(TAG, "Fetching models from copilot models API failed, using defaults", e);
        }

        setAvailableModels(DEFAULT_COPILOT_MODELS);
        return DEFAULT_COPILOT_MODELS;
    }

    /**
     * Keeps only models a user can actually pick for chat: chat-capable, exposed in the model
     * picker, and not gated behind a policy the user has not yet accepted. The chat default
     * is moved to the front of the list.
     */
    static List<String> parseChatModels(String responseBody) throws Exception {
        List<String> modelsList = new ArrayList<>();
        JSONObject json = new JSONObject(responseBody);
        if (!json.has("data")) return modelsList;

        String defaultModel = null;
        JSONArray dataArr = json.getJSONArray("data");
        for (int i = 0; i < dataArr.length(); i++) {
            JSONObject mObj = dataArr.optJSONObject(i);
            if (mObj == null) continue;
            String id = mObj.optString("id", "");
            if (id.isEmpty()) continue;

            if (!mObj.optBoolean("model_picker_enabled", true)) continue;

            JSONObject caps = mObj.optJSONObject("capabilities");
            if (caps != null) {
                String type = caps.optString("type", "");
                if (!type.isEmpty() && !"chat".equalsIgnoreCase(type)) {
                    continue;
                }
            }

            if (id.contains("-sol") || id.contains("embedding") || id.contains("codex")) {
                continue;
            }

            JSONObject policy = mObj.optJSONObject("policy");
            if (policy != null && !"enabled".equals(policy.optString("state", "enabled"))) continue;

            if (modelsList.contains(id)) continue;
            if (mObj.optBoolean("is_chat_default", false) && defaultModel == null) {
                defaultModel = id;
            }
            modelsList.add(id);
        }

        if (defaultModel != null) {
            modelsList.remove(defaultModel);
            modelsList.add(0, defaultModel);
        }
        return modelsList;
    }

    @Override
    public String getBillingInfo(String apiKey) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return "No GitHub Token configured.";
        }
        return "GitHub Copilot Subscription Active";
    }

    @Override
    public AiResponse generateContent(Context context, String apiKey, String selectedModel, String systemInstruction, List<ChatMessage> history) {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return AiResponse.error(401, "Error: GitHub OAuth Token is missing or empty.");
        }

        String targetModel = (selectedModel != null && !selectedModel.trim().isEmpty())
                ? selectedModel.trim()
                : getDefaultModel();

        try {
            String copilotToken = getOrFetchCopilotToken(apiKey);
            String chatEndpoint = mCachedCopilotEndpoint + "/chat/completions";

            URL url = new URL(chatEndpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + copilotToken);
            applyEditorHeaders(conn);
            conn.setRequestProperty("X-Initiator", "user");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setDoOutput(true);

            JSONArray messagesArray = new JSONArray();
            boolean isReasoningModel = targetModel.startsWith("o1") || targetModel.startsWith("o3");

            if (systemInstruction != null && !systemInstruction.trim().isEmpty()) {
                JSONObject sysMsg = new JSONObject();
                sysMsg.put("role", isReasoningModel ? "developer" : "system");
                sysMsg.put("content", systemInstruction);
                messagesArray.put(sysMsg);
            }

            if (history != null) {
                for (ChatMessage msg : history) {
                    if (msg != null && msg.getType() == ChatMessage.MessageType.REASONING) {
                        continue;
                    }
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

            logRequestDetails(TAG, "POST", url.toString(), conn.getRequestProperties(), requestBody);

            try (OutputStream os = conn.getOutputStream()) {
                os.write(requestBody.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            Map<String, List<String>> responseHeaders = conn.getHeaderFields();
            InputStream is = (responseCode >= 200 && responseCode < 300) ? conn.getInputStream() : conn.getErrorStream();
            String responseBody = readStream(is);

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
                    String resContent = msg.optString("content", "");
                    String reasoningContent = msg.optString("reasoning_content", null);
                    if (reasoningContent == null || reasoningContent.trim().isEmpty()) {
                        reasoningContent = msg.optString("thinking", null);
                    }
                    if (reasoningContent == null || reasoningContent.trim().isEmpty()) {
                        reasoningContent = msg.optString("reasoning", null);
                    }

                    if (resContent.trim().isEmpty() && reasoningContent != null && !reasoningContent.trim().isEmpty()) {
                        resContent = reasoningContent;
                        reasoningContent = null;
                    }

                    if (tokensUsed <= 0) {
                        tokensUsed = calculateTokenUsage(systemInstruction, history, resContent);
                    }
                    return AiResponse.success(resContent, reasoningContent, tokensUsed);
                }
                return AiResponse.success("No content returned.", tokensUsed);
            } else {
                return AiResponse.error(responseCode, "GitHub Copilot API error (HTTP " + responseCode + "): " + responseBody);
            }
        } catch (Exception e) {
            LogViewerService.getInstance().e(TAG, "Exception during GitHub Copilot request", e);
            return AiResponse.error(500, "Network error: " + e.getMessage());
        }
    }

    private String readStream(InputStream is) {
        if (is == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
