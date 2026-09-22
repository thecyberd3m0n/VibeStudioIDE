package com.vibestudio.app.service;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.vibestudio.app.chat.compressor.HistoryCompressor;
import com.vibestudio.app.chat.connection.ModelConnectionManager;
import com.vibestudio.app.chat.model.AiResponse;
import com.vibestudio.app.chat.model.ChatMessage;
import com.vibestudio.app.chat.model.ToolCall;
import com.vibestudio.app.chat.model.ToolResult;
import com.vibestudio.app.chat.provider.AiProvider;
import com.vibestudio.app.chat.provider.GeminiAiProvider;
import com.vibestudio.app.chat.tool.ToolCallParser;
import com.vibestudio.app.chat.tool.ToolExecutionManager;
import com.vibestudio.app.mcp.McpClientManager;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ChatService {

    private static final String TAG = "ChatService";
    private static ChatService sInstance;

    public interface OnChatMessageListener {
        void onMessageAdded(ChatMessage message);
        void onResponseLoading(boolean isLoading);
    }

    private final List<ChatMessage> mMessages = new ArrayList<>();
    private final List<OnChatMessageListener> mListeners = new ArrayList<>();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    private final McpClientManager mMcpClientManager;
    private final ToolExecutionManager mToolExecutionManager;
    private final AiProvider mAiProvider;
    private boolean mIsLoading = false;

    private ChatService() {
        mMcpClientManager = new McpClientManager();
        mToolExecutionManager = new ToolExecutionManager(mMcpClientManager);
        mAiProvider = new GeminiAiProvider();
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
                processUserRequest(context);
            }
        }).start();
    }

    private void processUserRequest(final Context context) {
        String apiKey = ModelConnectionManager.getInstance().getApiKey(context);

        if (apiKey == null || apiKey.isEmpty()) {
            LogViewerService.getInstance().w(TAG, "No API key found in connection manager");
            postAssistantResponse("Error: Gemini API Key is not configured. Please set your API Key in the Models tab.");
            return;
        }

        List<ChatMessage> historyCopy;
        synchronized (this) {
            historyCopy = new ArrayList<>(mMessages);
        }

        // Compress history if token limits are exceeded (100k token threshold from gemini.sh)
        List<ChatMessage> processedHistory = HistoryCompressor.compressIfNeeded(historyCopy);

        // Build dynamic system instruction with base instructions + high level skills catalog
        String baseInstruction = GeminiAiProvider.loadBaseSystemInstruction(context);
        String catalogString = mMcpClientManager.getHighLevelCatalog().toString();
        String fullSystemInstruction = baseInstruction + "\n=== HIGH-LEVEL SKILLS CATALOG (LAZY LOADING) ===\n" + catalogString;

        AiResponse response = mAiProvider.generateContent(context, apiKey, fullSystemInstruction, processedHistory);

        if (response.isSuccess()) {
            String rawReply = response.getContent();
            LogViewerService.getInstance().i(TAG, "AI response received successfully.");
            handlePotentialToolCall(context, rawReply);
        } else {
            LogViewerService.getInstance().w(TAG, "AI Provider error: " + response.getErrorMessage());
            postAssistantResponse(response.getErrorMessage());
        }
    }

    private void handlePotentialToolCall(final Context context, String rawReply) {
        ToolCall toolCall = ToolCallParser.parse(rawReply);

        if (toolCall != null) {
            String toolName = toolCall.getName();
            JSONObject args = toolCall.getArguments();

            LogViewerService.getInstance().i(TAG, "Executing tool call requested by AI: " + toolName + " | Args: " + args.toString());

            // Format UI summary message
            String toolSummary;
            if ("get_skill_schema".equalsIgnoreCase(toolName)) {
                toolSummary = "Optimizing Tool Selection";
            } else if ("execute_command".equalsIgnoreCase(toolName)) {
                String cmd = args.optString("command", "");
                toolSummary = "⚡ Executing terminal command:\n`" + cmd + "`";
            } else if ("read_terminal_output".equalsIgnoreCase(toolName)) {
                toolSummary = "🔍 Inspecting terminal logs buffer";
            } else if (toolName.startsWith("browser_")) {
                toolSummary = "🌐 Executing browser tool: " + toolName;
            } else if ("read_browser_logs".equalsIgnoreCase(toolName)) {
                toolSummary = "📋 Inspecting browser logs";
            } else {
                toolSummary = "⚙️ Executing tool: " + toolName;
            }

            postToolStatusMessage(toolSummary, ChatMessage.MessageType.TOOL_CALL);

            // Execute tool with context pass-through
            ToolResult toolResult = mToolExecutionManager.executeTool(context, toolCall);

            // Record assistant call and system tool result in conversation history
            ChatMessage modelCallMsg = new ChatMessage("Assistant", rawReply, false, ChatMessage.MessageType.TOOL_CALL);
            ChatMessage toolResultMsg = new ChatMessage("System Tool", toolResult.getFormattedOutput(), true, ChatMessage.MessageType.TOOL_RESULT);

            synchronized (ChatService.this) {
                mMessages.add(modelCallMsg);
                mMessages.add(toolResultMsg);
            }

            LogViewerService.getInstance().i(TAG, "Tool execution completed for " + toolName + ". Continuing agent execution loop...");

            // Continuous execution loop without arbitrary depth limit
            new Thread(new Runnable() {
                @Override
                public void run() {
                    processUserRequest(context);
                }
            }).start();
            return;
        }

        // Final normal assistant text response
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
