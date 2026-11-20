package com.vibestudio.app.chat.scenarios;

import android.content.Context;

import com.vibestudio.app.chat.model.AiResponse;
import com.vibestudio.app.chat.model.ChatMessage;
import com.vibestudio.app.chat.provider.AbstractAiProvider;
import com.vibestudio.app.chat.router.AiRouter;
import com.vibestudio.app.mcp.model.McpTool;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/** Provider that replays scripted replies and records every request it receives. */
class ScriptedProvider extends AbstractAiProvider {
    private final Deque<AiResponse> mReplies = new ArrayDeque<>();
    private final List<List<McpTool>> mTools = Collections.synchronizedList(new ArrayList<List<McpTool>>());
    private boolean mNativeTools;
    private boolean mParallelCalls;
    private final List<String> mSystemInstructions = Collections.synchronizedList(new ArrayList<String>());
    private final List<List<ChatMessage>> mHistories = Collections.synchronizedList(new ArrayList<List<ChatMessage>>());

    ScriptedProvider() {
        setAvailableModels(Collections.singletonList("scripted-model"));
    }

    /** Registers this provider as the router's default one. */
    static AiRouter newRouter(ScriptedProvider provider) {
        AiRouter router = new AiRouter();
        router.registerProvider(provider);
        router.setActiveProviderName(provider.getName());
        return router;
    }

    ScriptedProvider reply(String text) {
        mReplies.add(AiResponse.success(text));
        return this;
    }

    /** A reply whose tool calls came through the native API, each with the id and state the provider attached. */
    ScriptedProvider replyWithCalls(String content, AiResponse.ToolCall... calls) {
        mReplies.add(AiResponse.success(content).withToolCalls(java.util.Arrays.asList(calls)));
        return this;
    }

    ScriptedProvider replyError(int statusCode, String message) {
        mReplies.add(AiResponse.error(statusCode, message));
        return this;
    }

    /** Makes this provider declare tools natively, optionally with parallel tool calls. */
    ScriptedProvider nativeTools(boolean parallelCalls) {
        mNativeTools = true;
        mParallelCalls = parallelCalls;
        return this;
    }

    /** The tools declared with request {@code index}, or null when none were. */
    List<McpTool> tools(int index) {
        return mTools.get(index);
    }

    @Override
    public boolean supportsNativeTools() {
        return mNativeTools;
    }

    @Override
    public boolean supportsParallelToolCalls() {
        return mParallelCalls;
    }

    @Override
    public AiResponse generateContent(Context context, String apiKey, String selectedModel, String systemInstruction,
                                      List<ChatMessage> history, List<McpTool> tools) {
        AiResponse response = generateContent(context, apiKey, selectedModel, systemInstruction, history);
        mTools.set(mTools.size() - 1, tools);
        return response;
    }

    int requestCount() {
        return mSystemInstructions.size();
    }

    String systemInstruction(int index) {
        return mSystemInstructions.get(index);
    }

    String lastSystemInstruction() {
        return mSystemInstructions.get(mSystemInstructions.size() - 1);
    }

    List<ChatMessage> history(int index) {
        return mHistories.get(index);
    }

    @Override
    public String getName() {
        return AiRouter.DEFAULT_PROVIDER;
    }

    @Override
    public String getDefaultModel() {
        return "scripted-model";
    }

    @Override
    public List<String> fetchAvailableModelsSync(String apiKey) {
        return getAvailableModels();
    }

    @Override
    public String getSystemInstruction(Context context) {
        return "BASE-INSTRUCTION";
    }

    @Override
    public synchronized AiResponse generateContent(Context context, String apiKey, String selectedModel,
                                                   String systemInstruction, List<ChatMessage> history) {
        mSystemInstructions.add(systemInstruction);
        mHistories.add(new ArrayList<>(history));
        mTools.add(null);
        AiResponse next = mReplies.poll();
        return next != null ? next : AiResponse.error(500, "No scripted reply left");
    }
}
