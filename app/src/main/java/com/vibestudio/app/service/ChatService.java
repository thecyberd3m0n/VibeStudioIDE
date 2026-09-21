package com.vibestudio.app.service;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.vibestudio.app.db.DatabaseHelper;
import com.vibestudio.app.mcp.McpClientManager;

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

public class ChatService {

    private static final String TAG = "ChatService";
    private static final String DEFAULT_MODEL = "gemini-flash-latest";
    private static final int MAX_TOOL_LOOP_DEPTH = 200;
    
    private String loadBaseSystemInstruction(Context context) {
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

    private static ChatService sInstance;

    public static class ChatMessage {
        public enum MessageType {
            NORMAL,
            TOOL_CALL,
            TOOL_RESULT
        }

        private final String sender;
        private final String text;
        private final boolean isUser;
        private final MessageType type;

        public ChatMessage(String sender, String text, boolean isUser) {
            this(sender, text, isUser, MessageType.NORMAL);
        }

        public ChatMessage(String sender, String text, boolean isUser, MessageType type) {
            this.sender = sender;
            this.text = text;
            this.isUser = isUser;
            this.type = type != null ? type : MessageType.NORMAL;
        }

        public String getSender() { return sender; }
        public String getText() { return text; }
        public boolean isUser() { return isUser; }
        public MessageType getType() { return type; }
    }

    public interface OnChatMessageListener {
        void onMessageAdded(ChatMessage message);
        void onResponseLoading(boolean isLoading);
    }

    private final List<ChatMessage> mMessages = new ArrayList<>();
    private final List<OnChatMessageListener> mListeners = new ArrayList<>();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final McpClientManager mMcpClientManager;
    private boolean mIsLoading = false;

    private ChatService() {
        mMcpClientManager = new McpClientManager();
        mMessages.add(new ChatMessage("Assistant", "Hello! Welcome to VibeStudio. I am configured with lazy-loaded MCP skills. How can I assist you?", false));
    }

    public static synchronized ChatService getInstance() {
        if (sInstance == null) {
            sInstance = new ChatService();
        }
        return sInstance;
    }

    public synchronized List<ChatMessage> getMessages() {
        return new ArrayList<>(mMessages);
    }

    public synchronized boolean isLoading() {
        return mIsLoading;
    }

    public synchronized void addListener(OnChatMessageListener listener) {
        if (listener != null && !mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    public synchronized void removeListener(OnChatMessageListener listener) {
        mListeners.remove(listener);
    }

    public McpClientManager getMcpClientManager() {
        return mMcpClientManager;
    }

    public void sendMessage(final Context context, final String userText) {
        if (userText == null || userText.trim().isEmpty()) return;

        final String trimmedText = userText.trim();
        final ChatMessage userMsg = new ChatMessage("User", trimmedText, true);

        synchronized (this) {
            mMessages.add(userMsg);
            notifyMessageAdded(userMsg);
            mIsLoading = true;
            notifyLoading(true);
        }

        LogViewerService.getInstance().i(TAG, "User sent message: " + trimmedText);

        new Thread(new Runnable() {
            @Override
            public void run() {
                processUserRequest(context, 0);
            }
        }).start();
    }

    private void processUserRequest(final Context context, final int loopDepth) {
        DatabaseHelper dbHelper = new DatabaseHelper(context.getApplicationContext());
        String apiKey = dbHelper.getApiKey("Gemini");

        if (apiKey == null || apiKey.trim().isEmpty()) {
            LogViewerService.getInstance().w(TAG, "No Gemini API key found in SQLite database");
            postAssistantResponse("Error: Gemini API Key is not configured. Please set your API Key in the Models tab.");
            return;
        }

        String[] candidateModels = new String[] {
            "gemini-flash-latest",
            "gemini-pro-latest",
            "gemini-2.5-flash",
            "gemini-2.5-pro"
        };

        String responseStr = "";
        int statusCode = 500;

        for (String model : candidateModels) {
            responseStr = executeGeminiRequest(context, apiKey.trim(), model);
            statusCode = getHttpStatusCode(responseStr);

            if (statusCode != 404) {
                break;
            }
            LogViewerService.getInstance().w(TAG, "Model " + model + " returned 404, attempting next fallback model...");
        }

        if (statusCode >= 200 && statusCode < 300) {
            String reply = parseGeminiResponse(responseStr);
            LogViewerService.getInstance().i(TAG, "Gemini reply received successfully.");
            
            handlePotentialToolCall(context, reply, loopDepth);

        } else {
            LogViewerService.getInstance().w(TAG, "Gemini Chat API error response: " + responseStr);
            String errorReply = "Error (" + statusCode + "): " + parseErrorResponse(responseStr);
            postAssistantResponse(errorReply);
        }
    }

    private void handlePotentialToolCall(final Context context, String rawReply, final int loopDepth) {
        try {
            if (rawReply.contains("{") && rawReply.contains("\"tool\"")) {
                int jsonStart = rawReply.indexOf("{");
                int jsonEnd = rawReply.lastIndexOf("}");
                if (jsonStart != -1 && jsonEnd > jsonStart) {
                    String jsonBlock = rawReply.substring(jsonStart, jsonEnd + 1).trim();
                    JSONObject toolCallObj = new JSONObject(jsonBlock);
                    String toolName = toolCallObj.optString("tool");
                    JSONObject args = toolCallObj.optJSONObject("args");

                    if (args == null) args = new JSONObject();

                    LogViewerService.getInstance().i(TAG, "Executing tool call requested by AI: " + toolName + " | Args: " + args.toString());

                    // Format compact user-friendly summary message for UI
                    String toolSummary;
                    if ("get_skill_schema".equalsIgnoreCase(toolName)) {
                        String skillName = args.optString("skill_name", "unknown");
                        toolSummary = "Optimizing Tool Selection";
                    } else if ("execute_command".equalsIgnoreCase(toolName)) {
                        String cmd = args.optString("command", "");
                        toolSummary = "⚡ Executing terminal command:\n`" + cmd + "`";
                    } else if ("read_terminal_output".equalsIgnoreCase(toolName)) {
                        toolSummary = "🔍 Inspecting terminal logs buffer";
                    } else {
                        if (toolName.startsWith("browser_")) {
                        toolSummary = "🌐 Executing browser tool: " + toolName;
                    } else if ("read_browser_logs".equalsIgnoreCase(toolName)) {
                        toolSummary = "📋 Inspecting browser logs";
                    } else {
                        toolSummary = "⚙️ Executing tool: " + toolName;
                    }
                    }

                    // Post clean summary message to chat UI
                    postToolStatusMessage(toolSummary, ChatMessage.MessageType.TOOL_CALL);

                    // Execute tool logic
                    JSONObject toolResult = mMcpClientManager.executeToolCall(toolName, args, context);

                    // Step 1: Model assistant call entry (role = model)
                    ChatMessage modelCallMsg = new ChatMessage("Assistant", rawReply, false, ChatMessage.MessageType.TOOL_CALL);
                    
                    // Step 2: System tool execution result (role = user) for LLM evaluation
                    StringBuilder toolResultFormatted = new StringBuilder();
                    toolResultFormatted.append("[MCP Tool Execution Output for ").append(toolName).append("]\n");
                    toolResultFormatted.append(toolResult.toString(2));
                    ChatMessage toolResultMsg = new ChatMessage("System Tool", toolResultFormatted.toString(), true, ChatMessage.MessageType.TOOL_RESULT);

                    synchronized (ChatService.this) {
                        mMessages.add(modelCallMsg);
                        mMessages.add(toolResultMsg);
                    }

                    LogViewerService.getInstance().i(TAG, "Tool execution completed for " + toolName + ". Status: " + toolResult.optString("status", "unknown"));

                    if (loopDepth < MAX_TOOL_LOOP_DEPTH) {
                        LogViewerService.getInstance().i(TAG, "Continuing tool execution loop at depth: " + (loopDepth + 1));
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                processUserRequest(context, loopDepth + 1);
                            }
                        }).start();
                        return;
                    } else {
                        LogViewerService.getInstance().w(TAG, "Reached maximum tool execution loop depth (" + MAX_TOOL_LOOP_DEPTH + ")");
                        postAssistantResponse("Reached maximum automated tool call loop limit.");
                        return;
                    }
                }
            }
        } catch (Exception e) {
            LogViewerService.getInstance().e(TAG, "Error processing tool call from LLM output", e);
        }
        postAssistantResponse(rawReply);
    }

    private void postToolStatusMessage(final String summaryText, final ChatMessage.MessageType type) {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                ChatMessage toolMsg = new ChatMessage("System Tool", summaryText, false, type);
                synchronized (ChatService.this) {
                    notifyMessageAdded(toolMsg);
                }
            }
        });
    }

    private int getHttpStatusCode(String rawResult) {
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

    private String executeGeminiRequest(Context context, String apiKey, String modelName) {
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
            List<ChatMessage> currentHistory;
            synchronized (ChatService.this) {
                currentHistory = new ArrayList<>(mMessages);
            }

            for (ChatMessage msg : currentHistory) {
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

            JSONObject payload = new JSONObject();

            StringBuilder sysPromptBuilder = new StringBuilder(loadBaseSystemInstruction(context));
            sysPromptBuilder.append("\n=== HIGH-LEVEL SKILLS CATALOG (LAZY LOADING) ===\n");
            sysPromptBuilder.append(mMcpClientManager.getHighLevelCatalog().toString(2));

            JSONObject sysTextPart = new JSONObject();
            sysTextPart.put("text", sysPromptBuilder.toString());

            JSONArray sysParts = new JSONArray();
            sysParts.put(sysTextPart);

            JSONObject systemInstructionObj = new JSONObject();
            systemInstructionObj.put("parts", sysParts);

            payload.put("system_instruction", systemInstructionObj);
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

    private void postAssistantResponse(final String reply) {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                ChatMessage assistantMsg = new ChatMessage("Assistant", reply, false);
                synchronized (ChatService.this) {
                    mMessages.add(assistantMsg);
                    mIsLoading = false;
                    notifyMessageAdded(assistantMsg);
                    notifyLoading(false);
                }
            }
        });
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

    private void notifyMessageAdded(ChatMessage message) {
        for (OnChatMessageListener listener : new ArrayList<>(mListeners)) {
            listener.onMessageAdded(message);
        }
    }

    private void notifyLoading(boolean isLoading) {
        for (OnChatMessageListener listener : new ArrayList<>(mListeners)) {
            listener.onResponseLoading(isLoading);
        }
    }
}
