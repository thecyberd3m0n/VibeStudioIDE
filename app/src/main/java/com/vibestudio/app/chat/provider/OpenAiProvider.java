package com.vibestudio.app.chat.provider;

import android.content.Context;

import com.vibestudio.app.chat.model.AiResponse;
import com.vibestudio.app.chat.model.ChatMessage;

import java.util.Collections;
import java.util.List;

public class OpenAiProvider implements AiProvider {

    @Override
    public String getName() {
        return "OpenAI";
    }

    @Override
    public List<String> getAvailableModels() {
        return Collections.emptyList();
    }

    @Override
    public String getDefaultModel() {
        return "";
    }

    @Override
    public AiResponse generateContent(Context context, String apiKey, String selectedModel, String systemInstruction, List<ChatMessage> history) {
        return AiResponse.error(501, "OpenAI integration is not implemented yet.");
    }
}
