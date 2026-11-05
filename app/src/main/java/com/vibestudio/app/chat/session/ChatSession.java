package com.vibestudio.app.chat.session;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.vibestudio.app.chat.agents.Agent;
import com.vibestudio.app.chat.agents.AgentScanner;
import com.vibestudio.app.chat.agents.AgentToolPolicy;
import com.vibestudio.app.chat.compressor.HistoryCompressor;
import com.vibestudio.app.chat.instructions.InstructionsLoader;
import com.vibestudio.app.chat.model.AiResponse;
import com.vibestudio.app.chat.model.ChatMessage;
import org.json.JSONObject;
import com.vibestudio.app.chat.model.ToolResult;
import com.vibestudio.app.chat.router.AiRouter;
import com.vibestudio.app.chat.safety.PermissionPromptManager;
import com.vibestudio.app.chat.safety.WorkspaceBoundaryGuard;
import com.vibestudio.app.chat.safety.WorkspaceChangesetTracker;
import com.vibestudio.app.chat.skills.Skill;
import com.vibestudio.app.chat.skills.SkillScanner;
import com.vibestudio.app.chat.tool.ToolExecutionManager;
import com.vibestudio.app.mcp.McpClientManager;
import com.vibestudio.app.service.LogViewerService;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class ChatSession {

    private static final String TAG = "ChatSession";
    private static final String GENERIC_ASSISTANT_SENDER = "Assistant";

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
    // Agent invoked via /agent-name in the latest user message; applies to that whole turn (including tool loops).
    private volatile Agent mActiveAgent;

    public ChatSession() {
        this(null);
    }

    public ChatSession(String workspacePath) {
        this(workspacePath, AiRouter.getInstance());
    }

    /** Allows tests to supply an isolated router with scripted providers. */
    public ChatSession(String workspacePath, AiRouter aiRouter) {
        this.mId = UUID.randomUUID().toString();
        this.mWorkspacePath = workspacePath;
        this.mMcpClientManager = new McpClientManager();
        this.mMcpClientManager.setWorkspacePath(workspacePath);
        this.mToolExecutionManager = new ToolExecutionManager(this.mMcpClientManager);
        this.mAiRouter = aiRouter;
        
        String welcomeMsg = "Hello! Welcome to VibeStudio. How can I assist you?";
        this.mMessages.add(new ChatMessage(GENERIC_ASSISTANT_SENDER, welcomeMsg, false));
    }

    public String getId() {
        return mId;
    }

    public String getWorkspacePath() {
        return mWorkspacePath;
    }

    public void setWorkspacePath(String workspacePath) {
        this.mWorkspacePath = workspacePath;
        if (this.mMcpClientManager != null) {
            this.mMcpClientManager.setWorkspacePath(workspacePath);
        }
    }

    public synchronized WorkspaceChangesetTracker getChangesetTracker(Context context) {
        if (mChangesetTracker == null && context != null) {
            mChangesetTracker = new WorkspaceChangesetTracker(context);
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

    /** Slash-command entries: user-invocable skills, plus user-invocable agents (shown as "[agent]") not shadowed by a skill. */
    public List<Skill> getAvailableSkills() {
        List<Skill> result = new ArrayList<>();
        Set<String> names = new HashSet<>();
        for (Skill skill : SkillScanner.scanSkills(mWorkspacePath)) {
            if (skill.isUserInvocable()) {
                result.add(skill);
                names.add(skill.getName().toLowerCase());
            }
        }
        for (Agent agent : AgentScanner.scanAgents(mWorkspacePath)) {
            if (agent.isUserInvocable() && names.add(agent.getName().toLowerCase())) {
                result.add(new Skill(agent.getName(), "[agent] " + agent.getDescription(), agent.getInstructions(), agent.getSourcePath()));
            }
        }
        return result;
    }

    public Agent getActiveAgent() {
        return mActiveAgent;
    }

    public Skill resolveSkill(String slashCommandOrName) {
        if (slashCommandOrName == null) return null;
        String name = slashCommandOrName.trim();
        if (name.startsWith("/")) {
            name = name.substring(1);
        }
        name = name.toLowerCase();
        for (Skill skill : SkillScanner.scanSkills(mWorkspacePath)) {
            if (skill.isUserInvocable() && skill.getName().equalsIgnoreCase(name)) {
                return skill;
            }
        }
        return null;
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
        String messageToStore = userText;
        Agent newActiveAgent = null;
        String trimmed = userText.trim();
        if (trimmed.startsWith("/")) {
            int spaceIdx = trimmed.indexOf(" ");
            String cmdName = spaceIdx > 0 ? trimmed.substring(1, spaceIdx) : trimmed.substring(1);
            cmdName = cmdName.toLowerCase().trim();

            Skill matchedSkill = resolveSkill(cmdName);
            Agent matchedAgent = matchedSkill == null ? AgentScanner.findAgent(mWorkspacePath, cmdName) : null;

            if (matchedSkill != null) {
                mMcpClientManager.markSkillLoaded(matchedSkill.getName());
            } else if (matchedAgent != null && matchedAgent.isUserInvocable()) {
                newActiveAgent = matchedAgent;
            } else if (mMcpClientManager.isRegisteredServer(cmdName)) {
                mMcpClientManager.markSkillLoaded(cmdName);
            }
        }
        mActiveAgent = newActiveAgent;

        ChatMessage userMsg = new ChatMessage("User", messageToStore, true);

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
        // Messages produced during this turn are attributed to the model that generated them, so a
        // single chat window can show which model authored each reply after the user switches.
        final String modelAuthor = resolveModelAuthor(activeProviderName, activeModelName);

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
        String workspaceInstructions = InstructionsLoader.buildInstructions(mWorkspacePath);
        Agent activeAgent = mActiveAgent;
        String fullSystemInstruction = baseInstruction 
                + workspaceInstruction
                + workspaceInstructions
                + buildActiveAgentSection(activeAgent)
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

        // 1. Tool Call Parsing, Path Canonicalization & Pre-execution State Preparation (Funneled)
        WorkspaceChangesetTracker tracker = context != null ? getChangesetTracker(context) : null;
        ToolExecutionManager.PreparedToolCall prep = null;
        try {
            prep = mToolExecutionManager.prepareToolCall(context, rawReply, mWorkspacePath, tracker);
        } catch (Exception e) {
            String errorMsg = "Error: " + e.getMessage();
            LogViewerService.getInstance().e(TAG, errorMsg);
            postToolStatusMessage(rawReply, ChatMessage.MessageType.ERROR, "tool", null);
            ChatMessage modelCallMsg = new ChatMessage(modelAuthor, rawReply, false, ChatMessage.MessageType.TOOL_CALL, "tool", null);
            ChatMessage toolResultMsg = new ChatMessage("System Tool", errorMsg, true, ChatMessage.MessageType.ERROR, "tool", null);
            synchronized (ChatSession.this) {
                mMessages.add(modelCallMsg);
                mMessages.add(toolResultMsg);
            }
            notifyMessageAdded(modelCallMsg);
            notifyMessageAdded(toolResultMsg);
            notifyLoading(false);
            return;
        }

        if (prep != null) {
            JSONObject toolCallObj = prep.getToolCallObj();
            String toolName = prep.getToolName();
            JSONObject args = prep.getArgs();

            // 1b. Active agent's tool allow-list
            AgentToolPolicy toolPolicy = new AgentToolPolicy(activeAgent != null ? activeAgent.getTools() : null);
            if (toolPolicy.isEnforced() && !McpClientManager.isMetaTool(toolName) && !toolPolicy.isAllowed(toolName)) {
                String blockedMsg = "Error: Tool '" + toolName + "' is not permitted for agent '" + activeAgent.getName()
                        + "' (restricted by its 'tools' list). Use only permitted tools or answer without it.";
                postToolStatusMessage(blockedMsg, ChatMessage.MessageType.ERROR, toolName, args);

                ChatMessage modelCallMsg = new ChatMessage(modelAuthor, rawReply, false, ChatMessage.MessageType.TOOL_CALL, toolName, args);
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

            // 2. Boundary Guard Check & Permission Escalation
            if (mWorkspacePath != null && !mWorkspacePath.isEmpty()) {
                List<String> targetPaths = WorkspaceBoundaryGuard.getToolTargetPaths(toolName, args);
                for (String targetPath : targetPaths) {
                    boolean insideWorkspace = WorkspaceBoundaryGuard.isWithinWorkspace(targetPath, mWorkspacePath);
                    if (!insideWorkspace) {
                        boolean granted = PermissionPromptManager.getInstance().requestPermissionSync(context, toolName, targetPath);
                        if (!granted) {
                            String blockedMsg = "Error: Access denied to path outside workspace boundary (" + targetPath + ").";
                            postToolStatusMessage(blockedMsg, ChatMessage.MessageType.ERROR, toolName, args);

                            ChatMessage modelCallMsg = new ChatMessage(modelAuthor, rawReply, false, ChatMessage.MessageType.TOOL_CALL, toolName, args);
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
            }

            String requestMsg = prep.getFormattedText() != null ? prep.getFormattedText().toString() : rawReply;
            LogViewerService.getInstance().i(TAG, "Executing tool call requested by AI: " + toolName + " | Args: " + args.toString());

            postToolStatusMessage(requestMsg, ChatMessage.MessageType.TOOL_CALL, toolName, args);

            // Execute tool with context pass-through
            ToolResult toolResult = mToolExecutionManager.executeTool(context, toolCallObj);

            // Record assistant call and system tool result in conversation history (for LLM context)
            ChatMessage modelCallMsg = new ChatMessage(modelAuthor, rawReply, false, ChatMessage.MessageType.TOOL_CALL, toolName, args);
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
        postAssistantResponse(modelAuthor, rawReply);
    }

    private static String buildActiveAgentSection(Agent agent) {
        if (agent == null) return "";
        StringBuilder sb = new StringBuilder("\n=== ACTIVE AGENT: ").append(agent.getName()).append(" ===\n");
        sb.append("The user invoked the custom agent '").append(agent.getName())
                .append("'. For this request, act according to its instructions below.\n");
        if (!agent.getDescription().isEmpty()) {
            sb.append("Purpose: ").append(agent.getDescription()).append("\n");
        }
        sb.append("\n").append(agent.getInstructions()).append("\n");
        return sb.toString();
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

    /** Builds the sender label, e.g. "GitHub Copilot gpt-4o"; falls back to whichever part is known. */
    static String resolveModelAuthor(String providerName, String modelName) {
        String provider = providerName != null ? providerName.trim() : "";
        String model = modelName != null ? modelName.trim() : "";
        if (!provider.isEmpty() && !model.isEmpty()) return provider + " " + model;
        if (!model.isEmpty()) return model;
        if (!provider.isEmpty()) return provider;
        return GENERIC_ASSISTANT_SENDER;
    }

    private void postAssistantResponse(final String reply) {
        postAssistantResponse(GENERIC_ASSISTANT_SENDER, reply);
    }

    private void postAssistantResponse(final String sender, final String reply) {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                ChatMessage assistantMsg = new ChatMessage(sender, reply, false);
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
