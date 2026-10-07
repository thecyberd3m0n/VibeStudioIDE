package com.vibestudio.app.chat.router;

import android.content.Context;

import com.vibestudio.app.chat.model.AiResponse;
import com.vibestudio.app.chat.model.ChatMessage;
import com.vibestudio.app.chat.provider.AiProvider;
import com.vibestudio.app.chat.provider.GeminiAiProvider;
import com.vibestudio.app.db.DatabaseHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AiRouter {

    private static final String SETTING_ACTIVE_PROVIDER = "active_ai_provider";
    public static final String DEFAULT_PROVIDER = "Gemini";

    private static AiRouter sInstance;

    private final Map<String, AiProvider> mProviders = new LinkedHashMap<>();
    private String mActiveProviderName = DEFAULT_PROVIDER;

    public AiRouter() {
        registerProvider(new GeminiAiProvider());
    }

    public static synchronized AiRouter getInstance() {
        if (sInstance == null) {
            sInstance = new AiRouter();
        }
        return sInstance;
    }

    public void registerProvider(AiProvider provider) {
        if (provider != null && provider.getName() != null) {
            mProviders.put(provider.getName(), provider);
        }
    }

    public List<AiProvider> getProviders() {
        return new ArrayList<>(mProviders.values());
    }

    public AiProvider getProvider(String name) {
        return mProviders.get(name);
    }

    public String getActiveProviderName() {
        return mActiveProviderName;
    }

    public void setActiveProviderName(String providerName) {
        this.mActiveProviderName = providerName;
    }

    public void loadActiveProvider(Context context) {
        if (context == null) return;
        DatabaseHelper dbHelper = new DatabaseHelper(context.getApplicationContext());
        String active = dbHelper.getSetting(SETTING_ACTIVE_PROVIDER);
        if (active != null && !active.trim().isEmpty()) {
            mActiveProviderName = active.trim();
        } else {
            mActiveProviderName = DEFAULT_PROVIDER;
        }
    }

    public void saveActiveProvider(Context context, String providerName) {
        if (providerName == null || providerName.trim().isEmpty()) return;
        mActiveProviderName = providerName.trim();
        if (context != null) {
            DatabaseHelper dbHelper = new DatabaseHelper(context.getApplicationContext());
            dbHelper.setSetting(SETTING_ACTIVE_PROVIDER, mActiveProviderName);
        }
    }

    public String getActiveApiKey(Context context) {
        if (context == null) return null;
        DatabaseHelper dbHelper = new DatabaseHelper(context.getApplicationContext());
        String apiKey = dbHelper.getApiKey(mActiveProviderName);
        return (apiKey != null) ? apiKey.trim() : null;
    }

    public AiResponse generateContent(Context context, String apiKey, String systemInstruction, List<ChatMessage> history) {
        AiProvider activeProvider = getProvider(mActiveProviderName);
        if (activeProvider == null) {
            throw new IllegalStateException("Active provider '" + mActiveProviderName + "' is not registered in AiRouter");
        }
        return activeProvider.generateContent(context, apiKey, systemInstruction, history);
    }
}
