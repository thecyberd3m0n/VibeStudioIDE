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
import com.vibestudio.app.chat.safety.PermissionPromptManager;
import com.vibestudio.app.chat.safety.WorkspaceBoundaryGuard;
import com.vibestudio.app.chat.safety.WorkspaceChangesetTracker;
import com.vibestudio.app.chat.tool.ToolCallParser;
import com.vibestudio.app.chat.tool.ToolExecutionManager;
import com.vibestudio.app.mcp.McpClientManager;
import com.vibestudio.app.service.LogViewerService;

import java.io.File;
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
    private String mWorkspacePath;
    private final List<ChatMessage> mMessages = new ArrayList<>();
    private final List<OnChatMessageListener> mListeners = new ArrayList<>();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    private final McpClientManager mMcpClientManager;
    private final ToolExecutionManager mToolExecutionManager;
    private final AiRouter mAiRouter;
    private WorkspaceChangesetTracker mChangesetTracker;

    private boolean mIsLoading = false;
    private volatile boolean mStopRequested = false;

    public ChatSession() {
        this(null);
    }

    public ChatSession(String workspacePath) {
        this.mId = UUID.randomUUID().toString();
        this.mWorkspacePath = workspacePath;
        this.mMcpClientManager = new McpClientManager();
        this.mToolExecutionManager = new ToolExecutionManager(this.mMcpClientManager);
        this.mAiRouter = AiRouter.getInstance();
        
        String welcomeMsg = "Hello! Welcome to VibeStudio. How can I assist you?";
        this.mMessages.add(new ChatMessage("Assistant", welcomeMsg, false));
    }

    public String getId() {
        return mId;
    }

    public String getWorkspacePath() {
        return mWorkspacePath;
    }

    public void setWorkspacePath(String workspacePath) {
        this.mWorkspacePath = workspacePath;
    }

    public synchronized WorkspaceChangesetTracker getChangesetTracker(Context context) {
        if (mChangesetTracker == null && context != null) {
            mChangesetTracker = new WorkspaceChangesetTracker(context.getCacheDir());
        }
        return mChangesetTracker;
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
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                notifyLoading(true);
            }
        });
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

        // Attach system instructions with workspace scope + high level skills catalog + loaded skills schemas
        String baseInstruction = mAiRouter.getSystemInstruction(context);
        String workspaceInstruction = (mWorkspacePath != null && !mWorkspacePath.isEmpty())
                ? "\n=== ACTIVE WORKSPACE SCOPE ===\nTarget Workspace Path: " + mWorkspacePath
                + "\nAll file tool paths and terminal working directories default to this workspace scope. Do not read/modify files outside this path without need.\n"
                : "";
        String catalogString = mMcpClientManager.getHighLevelCatalog().toString();
        String loadedSkillsString = mMcpClientManager.getLoadedSkillsInstructions(context);
        String fullSystemInstruction = baseInstruction 
                + workspaceInstruction
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

        if (rawReply == null || rawReply.trim().isEmpty()) {
            LogViewerService.getInstance().w(TAG, "Received empty response content from " + activeProviderName);
            postErrorResponse("Error: Received empty response from " + activeProviderName + ". Please tap Retry below.");
            return;
        }

        if (rawReply.startsWith("Error:")) {
            postErrorResponse(rawReply);
            return;
        }

        // Check if response contains a tool call request
        JSONObject toolCallObj = ToolCallParser.parse(rawReply);

        if (toolCallObj != null) {
            String toolName = toolCallObj.optString("tool", "unknown");
            JSONObject args = toolCallObj.optJSONObject("args");
            if (args == null) args = new JSONObject();

            // 1. Path Canonicalization relative to Workspace Path
            if (mWorkspacePath != null && !mWorkspacePath.isEmpty()) {
                WorkspaceBoundaryGuard.canonicalizeToolArgs(toolName, args, mWorkspacePath);
            }

            // 2. Boundary Guard Check & Permission Escalation
            String targetPath = WorkspaceBoundaryGuard.getToolTargetPath(toolName, args);
            if (targetPath != null && mWorkspacePath != null && !mWorkspacePath.isEmpty()) {
                boolean insideWorkspace = WorkspaceBoundaryGuard.isWithinWorkspace(targetPath, mWorkspacePath);
                if (!insideWorkspace) {
                    boolean granted = PermissionPromptManager.getInstance().requestPermissionSync(context, toolName, targetPath);
                    if (!granted) {
                        String blockedMsg = "Error: Access denied to path outside workspace boundary (" + targetPath + ").";
                        postToolStatusMessage(blockedMsg, ChatMessage.MessageType.ERROR, toolName, args);

                        ChatMessage modelCallMsg = new ChatMessage("Assistant", rawReply, false, ChatMessage.MessageType.TOOL_CALL, toolName, args);
                        ChatMessage toolResultMsg = new ChatMessage("System Tool", blockedMsg, true, ChatMessage.MessageType.ERROR, toolName, null);
                        synchronized (ChatSession.this) {
                            mMessages.add(modelCallMsg);
                            mMessages.add(toolResultMsg);
                        }

                        if (!mStopRequested) {
                            new Thread(() -> processUserRequest(context)).start();
                        }
                        return;
                    }
                }
            }

            // 3. Changeset Pre-edit Tracking
            if (context != null) {
                WorkspaceChangesetTracker tracker = getChangesetTracker(context);
                if ("file_create_file".equalsIgnoreCase(toolName)
                        || "file_create_directory".equalsIgnoreCase(toolName)
                        || "editor_replace_lines".equalsIgnoreCase(toolName)
                        || "editor_search_replace".equalsIgnoreCase(toolName)
                        || "editor_insert_lines".equalsIgnoreCase(toolName)
                        || "editor_save".equalsIgnoreCase(toolName)
                        || "editor_write_file".equalsIgnoreCase(toolName)) {
                    if (targetPath != null) tracker.recordPreEditState(targetPath);
                } else if ("file_move".equalsIgnoreCase(toolName)) {
                    String srcPath = args.optString("src", null);
                    String dstPath = args.optString("dst", null);
                    if (srcPath != null) tracker.recordPreDeleteState(srcPath);
                    if (dstPath != null) tracker.recordPreEditState(dstPath);
                } else if ("file_copy".equalsIgnoreCase(toolName)) {
                    String dstPath = args.optString("dst", null);
                    if (dstPath != null) tracker.recordPreEditState(dstPath);
                } else if ("file_delete".equalsIgnoreCase(toolName)) {
                    if (targetPath != null) tracker.recordPreDeleteState(targetPath);
                }
            }

            String requestMsg = rawReply;
            LogViewerService.getInstance().i(TAG, "Executing tool call requested by AI: " + toolName + " | Args: " + args.toString());

            postToolStatusMessage(requestMsg, ChatMessage.MessageType.TOOL_CALL, toolName, args);

            // Execute tool with context pass-through
            ToolResult toolResult = mToolExecutionManager.executeTool(context, toolCallObj);

            // Record assistant call and system tool result in conversation history (for LLM context)
            ChatMessage modelCallMsg = new ChatMessage("Assistant", rawReply, false, ChatMessage.MessageType.TOOL_CALL, toolName, args);
            ChatMessage toolResultMsg = new ChatMessage("System Tool", toolResult.getFormattedOutput(), true,
                    toolResult.isError() ? ChatMessage.MessageType.ERROR : ChatMessage.MessageType.TOOL_RESULT, toolName, null);

            synchronized (ChatSession.this) {
                mMessages.add(modelCallMsg);
                mMessages.add(toolResultMsg);
            }

            // If execution caused an error, notify UI
            if (toolResult.isError()) {
                postToolStatusMessage(toolResult.getFormattedOutput(), ChatMessage.MessageType.ERROR, toolName, null);
            }

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

    private void postToolStatusMessage(final String summaryText, final ChatMessage.MessageType type, final String toolName, final JSONObject toolArgs) {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                ChatMessage toolMsg = new ChatMessage("System Tool", summaryText, false, type, toolName, toolArgs);
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
