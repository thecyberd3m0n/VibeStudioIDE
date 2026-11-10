package com.vibestudio.app.chat.session;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.vibestudio.app.chat.session.AgentLoopController.StepResult;
import com.vibestudio.app.chat.agents.Agent;
import com.vibestudio.app.chat.agents.AgentScanner;
import com.vibestudio.app.chat.agents.AgentToolPolicy;
import com.vibestudio.app.chat.compressor.HistoryCompressor;
import com.vibestudio.app.chat.instructions.InstructionsLoader;
import com.vibestudio.app.chat.model.AiResponse;
import com.vibestudio.app.chat.model.ChatMessage;
import org.json.JSONObject;
import com.vibestudio.app.chat.model.ToolResult;
import com.vibestudio.app.chat.prompt.ToolProtocolPrompt;
import com.vibestudio.app.chat.router.AiRouter;
import com.vibestudio.app.chat.safety.PermissionPromptManager;
import com.vibestudio.app.chat.safety.WorkspaceBoundaryGuard;
import com.vibestudio.app.chat.safety.WorkspaceChangesetTracker;
import com.vibestudio.app.chat.skills.Skill;
import com.vibestudio.app.chat.skills.SkillScanner;
import com.vibestudio.app.chat.tool.ToolExecutionManager;
import com.vibestudio.app.mcp.McpClientManager;
import com.vibestudio.app.mcp.model.McpTool;
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

    private String mLastToolName;
    private final AgentLoopController mAgentLoop = new AgentLoopController();
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

    public AgentLoopController getAgentLoop() {
        return mAgentLoop;
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
        startAgentLoop(context);
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

        startAgentLoop(context);
    }

    private void startAgentLoop(final Context context) {
        mLastToolName = null;
        new Thread(new Runnable() {
            @Override
            public void run() {
                mAgentLoop.run(
                        loop -> runTurn(context),
                        () -> mStopRequested,
                        () -> postErrorResponse("Stopped after " + mAgentLoop.getMaxTurns()
                                + " agent turns without finishing. Send a message to continue."),
                        error -> postErrorResponse("Error: " + error.getMessage()));
            }
        }).start();
    }

    private StepResult runTurn(final Context context) {
        if (mStopRequested) {
            postAssistantResponse("⏹️ Operation stopped by user.");
            return StepResult.FINISHED;
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
            return StepResult.FINISHED;
        }

        List<ChatMessage> snapshot;
        synchronized (this) {
            snapshot = new ArrayList<>(mMessages);
        }

        // Apply automatic token history compression rule before sending payload
        snapshot = HistoryCompressor.compressIfNeeded(snapshot);

        Agent activeAgent = mActiveAgent;
        AgentToolPolicy toolPolicy = new AgentToolPolicy(activeAgent != null ? activeAgent.getTools() : null);
        boolean nativeTools = mAiRouter.usesNativeTools(activeProviderName, activeModelName);
        boolean parallelCalls = mAiRouter.usesParallelToolCalls(activeProviderName, activeModelName);

        LogViewerService.getInstance().i(TAG, "Generating content via " + activeProviderName + " using selected model: " + activeModelName
                + " (" + (nativeTools ? "native tools" + (parallelCalls ? ", parallel calls" : "") : "text tool protocol") + ")");
        AiResponse response = mAiRouter.generateContent(context, apiKey,
                buildSystemInstruction(context, nativeTools, parallelCalls, activeAgent), snapshot,
                nativeTools ? declaredTools(toolPolicy) : null);

        // The API refused the declared tools: ask again with the text protocol (prompt rebuilt with the schemas
        // and call format) and, if that works, remember to use it for this model.
        if (nativeTools && !response.isSuccess() && response.getStatusCode() == 400 && !mStopRequested) {
            LogViewerService.getInstance().w(TAG, "Request with native tools rejected (HTTP 400); retrying with the text tool protocol.");
            AiResponse textResponse = mAiRouter.generateContent(context, apiKey,
                    buildSystemInstruction(context, false, false, activeAgent), snapshot, null);
            if (textResponse.isSuccess()) {
                mAiRouter.markNativeToolsUnsupported(context, activeProviderName, activeModelName);
                response = textResponse;
                parallelCalls = false;
            }
        }

        if (mStopRequested) {
            postAssistantResponse("⏹️ Operation stopped by user.");
            return StepResult.FINISHED;
        }

        if (!response.isSuccess()) {
            LogViewerService.getInstance().w(TAG, "AI Provider (" + activeProviderName + " / " + activeModelName + ") error: " + response.getErrorMessage());
            postErrorResponse(response.getErrorMessage());
            return StepResult.FINISHED;
        }

        String reasoningContent = response.getReasoningContent();
        String rawReply = response.getContent();

        if (rawReply == null || rawReply.trim().isEmpty()) {
            LogViewerService.getInstance().w(TAG, "Received empty response content from " + activeProviderName);
            postErrorResponse("Error: Received empty response from " + activeProviderName + ". Please tap Retry below.");
            return StepResult.FINISHED;
        }

        if (rawReply.startsWith("Error:")) {
            postErrorResponse(rawReply);
            return StepResult.FINISHED;
        }

        if ((reasoningContent == null || reasoningContent.trim().isEmpty()) && rawReply.contains("{")) {
            int jsonIdx = rawReply.indexOf("{");
            String preceding = rawReply.substring(0, jsonIdx).trim();
            if (!preceding.isEmpty()) {
                reasoningContent = preceding;
            }
        }

        if (reasoningContent != null && !reasoningContent.trim().isEmpty()) {
            postReasoningMessage(modelAuthor, reasoningContent.trim());
        }

        // 1. Tool call parsing. Every call in the reply is run in order when the model supports parallel calls,
        // otherwise only the first; the rest are reported back as skipped so the model can re-issue them.
        String turnId = UUID.randomUUID().toString();
        List<TurnCall> parsedCalls = new ArrayList<>();
        if (nativeTools && !response.getToolCalls().isEmpty()) {
            for (AiResponse.ToolCall nativeCall : response.getToolCalls()) {
                parsedCalls.add(new TurnCall(nativeCall.getId(), nativeCall.getCall(), nativeCall.getState(), nativeCall.getStateKind(), turnId));
            }
        } else {
            for (JSONObject textCall : ToolExecutionManager.parseToolCalls(rawReply)) {
                parsedCalls.add(new TurnCall("call_" + UUID.randomUUID().toString().replace("-", "").substring(0, 24), textCall, null, null, turnId));
            }
        }
        if (!parsedCalls.isEmpty()) {
            int allowed = parallelCalls ? AgentLoopController.MAX_PARALLEL_TOOL_CALLS : 1;
            if (parsedCalls.size() > allowed) {
                LogViewerService.getInstance().w(TAG, parsedCalls.size() + " tool calls in one reply; running " + allowed + ", skipping the rest.");
            }
            WorkspaceChangesetTracker tracker = context != null ? getChangesetTracker(context) : null;
            for (int i = 0; i < Math.min(allowed, parsedCalls.size()); i++) {
                ToolExecutionManager.PreparedToolCall prep;
                try {
                    prep = mToolExecutionManager.prepareToolCall(context, parsedCalls.get(i).call, mWorkspacePath, tracker);
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
                    return StepResult.FINISHED;
                }
                if (runToolCall(context, prep, parsedCalls.get(i), modelAuthor, activeAgent, toolPolicy) == StepResult.FINISHED) {
                    return StepResult.FINISHED;
                }
            }
            for (int i = allowed; i < parsedCalls.size(); i++) {
                recordSkippedToolCall(modelAuthor, parsedCalls.get(i), allowed);
            }
            return StepResult.CONTINUE;
        }

        // A reply without a tool call is not a finish signal; only 'task_complete' is, so the loop keeps nudging.
        // A cap on consecutive nudges is tracked as a future enhancement (issue #34).
        ChatMessage replyMsg = new ChatMessage(modelAuthor, rawReply, false);
        ChatMessage nudgeMsg = new ChatMessage("System Tool", AgentLoopController.nudgeFor(mLastToolName), true);
        synchronized (this) {
            mMessages.add(replyMsg);
            mMessages.add(nudgeMsg);
        }
        notifyMessageAdded(replyMsg);
        return StepResult.CONTINUE;
    }

    private String buildSystemInstruction(Context context, boolean nativeTools, boolean parallelCalls, Agent activeAgent) {
        String workspaceScope = (mWorkspacePath != null && !mWorkspacePath.isEmpty())
                ? "\n=== ACTIVE WORKSPACE SCOPE ===\nTarget Workspace Path: " + mWorkspacePath
                + "\nAll file tool paths and terminal working directories default to this workspace scope. Do not read/modify files outside this path without need.\n"
                : "";
        String catalogString = mMcpClientManager.getHighLevelCatalog(nativeTools).toString();
        String loadedSkillsString = mMcpClientManager.getLoadedSkillsInstructions(context, nativeTools);
        // Stable parts first, parts that change during a session last (helps prompt-prefix caching).
        return mAiRouter.getSystemInstruction(context)
                + ToolProtocolPrompt.build(context, nativeTools, parallelCalls)
                + workspaceScope
                + InstructionsLoader.buildInstructions(mWorkspacePath)
                + buildActiveAgentSection(activeAgent)
                + "\n=== HIGH-LEVEL SKILLS CATALOG (LAZY LOADING) ===\n" + catalogString
                + (loadedSkillsString != null && !loadedSkillsString.isEmpty() ? "\n" + loadedSkillsString : "")
                + AgentLoopController.STOP_GUARD;
    }

    /** The tools to declare natively: everything the active agent may use, in a fixed order. */
    private List<McpTool> declaredTools(AgentToolPolicy toolPolicy) {
        List<McpTool> tools = new ArrayList<>();
        for (McpTool tool : mMcpClientManager.getActiveTools()) {
            String name = tool.getName();
            if (!toolPolicy.isEnforced() || McpClientManager.isMetaTool(name)
                    || McpClientManager.isTaskCompleteTool(name) || toolPolicy.isAllowed(name)) {
                tools.add(tool);
            }
        }
        return tools;
    }

    /** One tool call of a model reply, with the id and replay data its provider attached. */
    private static final class TurnCall {
        final String id;
        final JSONObject call;
        final String state;
        final String stateKind;
        final String turnId;

        TurnCall(String id, JSONObject call, String state, String stateKind, String turnId) {
            this.id = id;
            this.call = call;
            this.state = state;
            this.stateKind = stateKind;
            this.turnId = turnId;
        }

        /** The call message carries the replay state; its result only the link. */
        ChatMessage linkCall(ChatMessage message) {
            return message.withToolLink(id, turnId, state, stateKind);
        }

        ChatMessage linkResult(ChatMessage message) {
            return message.withToolLink(id, turnId, null, null);
        }
    }

    /** Tells the model that a call beyond the per-response limit was not run, as that call's result. */
    private void recordSkippedToolCall(String modelAuthor, TurnCall turnCall, int allowed) {
        String toolName = turnCall.call.optString("tool", "tool");
        JSONObject args = turnCall.call.optJSONObject("args");
        String note = "Not executed: only " + allowed + " tool call" + (allowed == 1 ? " is" : "s are")
                + " run per response. Call '" + toolName + "' again in a later response if it is still needed.";
        ChatMessage modelCallMsg = turnCall.linkCall(new ChatMessage(modelAuthor, turnCall.call.toString(), false, ChatMessage.MessageType.TOOL_CALL, toolName, args));
        ChatMessage toolResultMsg = turnCall.linkResult(new ChatMessage("System Tool", note, true, ChatMessage.MessageType.ERROR, toolName, null));
        synchronized (ChatSession.this) {
            mMessages.add(modelCallMsg);
            mMessages.add(toolResultMsg);
        }
        postToolStatusMessage(note, ChatMessage.MessageType.ERROR, toolName, args);
    }

    /** Runs one prepared tool call: agent allow-list, workspace boundary, execution and history entries. */
    private StepResult runToolCall(Context context, ToolExecutionManager.PreparedToolCall prep, TurnCall turnCall, String modelAuthor,
                                   Agent activeAgent, AgentToolPolicy toolPolicy) {
        JSONObject toolCallObj = prep.getToolCallObj();
        String toolName = prep.getToolName();
        JSONObject args = prep.getArgs();
        String callText = toolCallObj.toString();

        mLastToolName = toolName;

        if (McpClientManager.isTaskCompleteTool(toolName)) {
            String summary = args.optString("summary", "").trim();
            if (summary.isEmpty()) {
                finishLoading();
            } else {
                postAssistantResponse(modelAuthor, summary);
            }
            return StepResult.FINISHED;
        }

        // 1b. Active agent's tool allow-list
        if (toolPolicy.isEnforced() && !McpClientManager.isMetaTool(toolName) && !toolPolicy.isAllowed(toolName)) {
            String blockedMsg = "Error: Tool '" + toolName + "' is not permitted for agent '" + activeAgent.getName()
                    + "' (restricted by its 'tools' list). Use only permitted tools or answer without it.";
            recordBlockedToolCall(modelAuthor, turnCall, callText, toolName, args, blockedMsg);
            return StepResult.CONTINUE;
        }

        // 2. Boundary Guard Check & Permission Escalation
        if (mWorkspacePath != null && !mWorkspacePath.isEmpty()) {
            List<String> targetPaths = WorkspaceBoundaryGuard.getToolTargetPaths(toolName, args);
            for (String targetPath : targetPaths) {
                boolean insideWorkspace = WorkspaceBoundaryGuard.isWithinWorkspace(targetPath, mWorkspacePath);
                if (!insideWorkspace) {
                    boolean granted = PermissionPromptManager.getInstance().requestPermissionSync(context, toolName, targetPath);
                    if (!granted) {
                        recordBlockedToolCall(modelAuthor, turnCall, callText, toolName, args,
                                "Error: Access denied to path outside workspace boundary (" + targetPath + ").");
                        return StepResult.CONTINUE;
                    }
                }
            }
        }

        String requestMsg = prep.getFormattedText() != null ? prep.getFormattedText().toString() : callText;
        LogViewerService.getInstance().i(TAG, "Executing tool call requested by AI: " + toolName + " | Args: " + args.toString());

        postToolStatusMessage(requestMsg, ChatMessage.MessageType.TOOL_CALL, toolName, args);

        // Execute tool with context pass-through
        mAgentLoop.setState(AgentLoopController.State.EXECUTING_TOOL);
        ToolResult toolResult = mToolExecutionManager.executeTool(context, toolCallObj);

        // Record assistant call and system tool result in conversation history (for LLM context)
        ChatMessage modelCallMsg = turnCall.linkCall(new ChatMessage(modelAuthor, callText, false, ChatMessage.MessageType.TOOL_CALL, toolName, args));
        ChatMessage toolResultMsg = turnCall.linkResult(new ChatMessage("System Tool", toolResult.getFormattedOutput(), true,
                toolResult.isError() ? ChatMessage.MessageType.ERROR : ChatMessage.MessageType.TOOL_RESULT, toolName, null));

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
            return StepResult.FINISHED;
        }

        LogViewerService.getInstance().i(TAG, "Tool execution completed for " + toolName + ". Continuing agent execution loop...");

        return StepResult.CONTINUE;
    }

    private void recordBlockedToolCall(String modelAuthor, TurnCall turnCall, String callText, String toolName, JSONObject args, String blockedMsg) {
        postToolStatusMessage(blockedMsg, ChatMessage.MessageType.ERROR, toolName, args);

        ChatMessage modelCallMsg = turnCall.linkCall(new ChatMessage(modelAuthor, callText, false, ChatMessage.MessageType.TOOL_CALL, toolName, args));
        ChatMessage toolResultMsg = turnCall.linkResult(new ChatMessage("System Tool", blockedMsg, true, ChatMessage.MessageType.ERROR, toolName, null));
        synchronized (ChatSession.this) {
            mMessages.add(modelCallMsg);
            mMessages.add(toolResultMsg);
        }
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

    private void postReasoningMessage(final String sender, final String text) {
        if (text == null || text.trim().isEmpty()) return;
        ChatMessage msg = new ChatMessage(sender, text, false, ChatMessage.MessageType.REASONING);
        synchronized (this) {
            mMessages.add(msg);
        }
        notifyMessageAdded(msg);
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

    private void finishLoading() {
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
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
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
        });
    }

    private void notifyLoading(final boolean isLoading) {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
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
        });
    }
}
