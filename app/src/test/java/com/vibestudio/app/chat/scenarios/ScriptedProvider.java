package com.vibestudio.app.chat.scenarios;

import android.content.Context;

import com.vibestudio.app.chat.model.AiResponse;
import com.vibestudio.app.chat.model.ChatMessage;
import com.vibestudio.app.chat.provider.AbstractAiProvider;
import com.vibestudio.app.chat.router.AiRouter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/** Provider that replays scripted replies and records every request it receives. */
class ScriptedProvider extends AbstractAiProvider {
    private final Deque<String> mReplies = new ArrayDeque<>();
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
        mReplies.add(text);
        return this;
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
        String next = mReplies.poll();
        return next != null ? AiResponse.success(next) : AiResponse.error(500, "No scripted reply left");
    }
}
