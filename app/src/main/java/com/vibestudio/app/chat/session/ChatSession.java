package com.vibestudio.app.chat.session;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.vibestudio.app.chat.compressor.HistoryCompressor;
import com.vibestudio.app.chat.model.AiResponse;
import com.vibestudio.app.chat.model.ChatMessage;
import org.json.JSONObject;
import com.vibestudio.app.chat.model.ToolResult;
import com.vibestudio.app.chat.router.AiRouter;
import com.vibestudio.app.chat.tool.ToolCallParser;
import com.vibestudio.app.chat.tool.ToolExecutionManager;
import com.vibestudio.app.mcp.McpClientManager;
import com.vibestudio.app.service.LogViewerService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ChatSession {

    private static final String TAG = "ChatSession";

    public interface OnChatMessageListener {
        void onMessageAdded(ChatMessage message);
        void onResponseLoading(boolean isLoading);
    }

    private final String mId;
    private final List<ChatMessage> mMessages = new ArrayList<>();
    private final List<OnChatMessageListener> mListeners = new ArrayList<>();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    private final McpClientManager mMcpClientManager;
    private final ToolExecutionManager mToolExecutionManager;
    private final AiRouter mAiRouter;
    private boolean mIsLoading = false;
    private volatile boolean mStopRequested = false;

    public ChatSession() {
        this.mId = UUID.randomUUID().toString();
        this.mMcpClientManager = new McpClientManager();
        this.mToolExecutionManager = new ToolExecutionManager(this.mMcpClientManager);
        this.mAiRouter = AiRouter.getInstance();
        this.mMessages.add(new ChatMessage("Assistant", "Hello! Welcome to VibeStudio. I am configured with lazy-loaded MCP skills. How can I assist you?", false));
    }

    public String getId() {
        return mId;
    }

    public synchronized List<ChatMessage> getMessages() {
        return new ArrayList<>(mMessages);
    }

    public synchronized boolean isLoading() {
        return mIsLoading;
    }

    public McpClientManager getMcpClientManager() {
        return mMcpClientManager;
    }

    public boolean isStopRequested() {
        return mStopRequested;
    }

    public void stopAgentExecution() {
        LogViewerService.getInstance().i(TAG, "Agent execution stop requested by user.");
        mStopRequested = true;
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                synchronized (ChatSession.this) {
                    mIsLoading = false;
                    notifyLoading(false);
                }
            }
        });
    }

    public void stopGeneration() {
        stopAgentExecution();
    }

    public synchronized void addListener(OnChatMessageListener listener) {
        if (listener != null && !mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    public synchronized void removeListener(OnChatMessageListener listener) {
        mListeners.remove(listener);
    }

    public void sendMessage(final Context context, final String userText) {
        addUserMessage(context, userText);
    }

    public void retry(final Context context) {
        if (mIsLoading) return;
        synchronized (this) {
            if (!mMessages.isEmpty()) {
                ChatMessage last = mMessages.get(mMessages.size() - 1);
                if (last.isError() || last.getType() == ChatMessage.MessageType.ERROR) {
                    mMessages.remove(mMessages.size() - 1);
                }
            }
        }
        mIsLoading = true;
        mStopRequested = false;
        new Thread(new Runnable() {
            @Override
            public void run() {
                processUserRequest(context);
            }
        }).start();
    }

    public void addUserMessage(final Context context, final String userText) {
        if (userText == null || userText.trim().isEmpty()) return;

        mStopRequested = false;
        ChatMessage userMsg = new ChatMessage("User", userText, true);

        synchronized (this) {
            mMessages.add(userMsg);
            mIsLoading = true;
            notifyMessageAdded(userMsg);
            notifyLoading(true);
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                processUserRequest(context);
            }
        }).start();
    }

    private void processUserRequest(final Context context) {
        if (mStopRequested) {
            postAssistantResponse("⏹️ Operation stopped by user.");
            return;
        }

        mAiRouter.loadActiveProvider(context);
        String apiKey = mAiRouter.getActiveApiKey(context);
        String activeProviderName = mAiRouter.getActiveProviderName();
        String activeModelName = mAiRouter.getActiveModelName(activeProviderName);

        if (apiKey == null || apiKey.isEmpty()) {
            LogViewerService.getInstance().w(TAG, "No API key found in connection manager for provider: " + activeProviderName);
            postErrorResponse("Error: " + activeProviderName + " API Key is not configured. Please set your API Key in the Models tab.");
            return;
        }

        List<ChatMessage> snapshot;
        synchronized (this) {
            snapshot = new ArrayList<>(mMessages);
        }

        // Apply automatic token history compression rule before sending payload
        snapshot = HistoryCompressor.compressIfNeeded(snapshot);

        // Attach system instructions with high level skills catalog + loaded skills schemas
        String baseInstruction = mAiRouter.getSystemInstruction(context);
        String catalogString = mMcpClientManager.getHighLevelCatalog().toString();
        String loadedSkillsString = mMcpClientManager.getLoadedSkillsInstructions(context);
        String fullSystemInstruction = baseInstruction 
                + "\n=== HIGH-LEVEL SKILLS CATALOG (LAZY LOADING) ===\n" + catalogString
                + (loadedSkillsString != null && !loadedSkillsString.isEmpty() ? "\n" + loadedSkillsString : "");

        LogViewerService.getInstance().i(TAG, "Generating content via " + activeProviderName + " using selected model: " + activeModelName);
        AiResponse response = mAiRouter.generateContent(context, apiKey, fullSystemInstruction, snapshot);

        if (mStopRequested) {
            postAssistantResponse("⏹️ Operation stopped by user.");
            return;
        }

        if (!response.isSuccess()) {
            LogViewerService.getInstance().w(TAG, "AI Provider (" + activeProviderName + " / " + activeModelName + ") error: " + response.getErrorMessage());
            postErrorResponse(response.getErrorMessage());
            return;
        }

        String rawReply = response.getContent();

        // Check if response contains a tool call request
        JSONObject toolCallObj = ToolCallParser.parse(rawReply);

        if (toolCallObj != null) {
            String toolName = toolCallObj.optString("tool", "unknown");
            JSONObject args = toolCallObj.optJSONObject("args");
            if (args == null) args = new JSONObject();

            String requestMsg = rawReply;

            LogViewerService.getInstance().i(TAG, "Executing tool call requested by AI: " + toolName + " | Args: " + args.toString());

            postToolStatusMessage(requestMsg, ChatMessage.MessageType.TOOL_CALL);

            // Execute tool with context pass-through
            ToolResult toolResult = mToolExecutionManager.executeTool(context, toolCallObj);
            String responseMsg = toolResult.getFormattedOutput();

            // Record assistant call and system tool result in conversation history
            ChatMessage modelCallMsg = new ChatMessage("Assistant", rawReply, false, ChatMessage.MessageType.TOOL_CALL);
            ChatMessage toolResultMsg = new ChatMessage("System Tool", toolResult.getFormattedOutput(), true,
                    toolResult.isError() ? ChatMessage.MessageType.ERROR : ChatMessage.MessageType.TOOL_RESULT);

            synchronized (ChatSession.this) {
                mMessages.add(modelCallMsg);
                mMessages.add(toolResultMsg);
            }

            postToolStatusMessage(responseMsg,
                    toolResult.isError() ? ChatMessage.MessageType.ERROR : ChatMessage.MessageType.TOOL_RESULT);

            if (mStopRequested) {
                postAssistantResponse("⏹️ Operation stopped by user.");
                return;
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
                synchronized (ChatSession.this) {
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
                synchronized (ChatSession.this) {
                    mMessages.add(assistantMsg);
                    mIsLoading = false;
                    notifyMessageAdded(assistantMsg);
                    notifyLoading(false);
                }
            }
        });
    }

    private void postErrorResponse(final String errorMessage) {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                ChatMessage errorMsg = new ChatMessage("Error", errorMessage, false, ChatMessage.MessageType.ERROR);
                synchronized (ChatSession.this) {
                    mMessages.add(errorMsg);
                    mIsLoading = false;
                    notifyMessageAdded(errorMsg);
                    notifyLoading(false);
                }
            }
        });
    }

    private void notifyMessageAdded(final ChatMessage message) {
        List<OnChatMessageListener> listeners;
        synchronized (mListeners) {
            listeners = new ArrayList<>(mListeners);
        }
        for (OnChatMessageListener l : listeners) {
            try {
                l.onMessageAdded(message);
            } catch (Exception e) {
                LogViewerService.getInstance().e(TAG, "Error notifying message listener", e);
            }
        }
    }

    private void notifyLoading(final boolean isLoading) {
        List<OnChatMessageListener> listeners;
        synchronized (mListeners) {
            listeners = new ArrayList<>(mListeners);
        }
        for (OnChatMessageListener l : listeners) {
            try {
                l.onResponseLoading(isLoading);
            } catch (Exception e) {
                LogViewerService.getInstance().e(TAG, "Error notifying loading listener", e);
            }
        }
    }
}
