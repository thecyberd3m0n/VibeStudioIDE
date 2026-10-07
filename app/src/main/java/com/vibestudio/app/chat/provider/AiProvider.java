package com.vibestudio.app.chat.provider;

import android.content.Context;

import com.vibestudio.app.chat.model.AiResponse;
import com.vibestudio.app.chat.model.ChatMessage;

import java.util.List;

public interface AiProvider {

    String getName();

    List<String> getAvailableModels();

    String getDefaultModel();

    AiResponse generateContent(Context context, String apiKey, String selectedModel, String systemInstruction, List<ChatMessage> history);
}
