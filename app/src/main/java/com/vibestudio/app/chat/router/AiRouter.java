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
    private static final String SETTING_ACTIVE_MODEL_PREFIX = "active_ai_model_";
    public static final String DEFAULT_PROVIDER = "Gemini";

    private static AiRouter sInstance;

    private final Map<String, AiProvider> mProviders = new LinkedHashMap<>();
    private String mActiveProviderName = DEFAULT_PROVIDER;
    private final Map<String, String> mActiveModelPerProvider = new LinkedHashMap<>();

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

    public String getActiveModelName(String providerName) {
        String selected = mActiveModelPerProvider.get(providerName);
        if (selected != null) return selected;
        AiProvider provider = getProvider(providerName);
        return (provider != null) ? provider.getDefaultModel() : null;
    }

    public void setActiveModelName(String providerName, String modelName) {
        if (providerName != null && modelName != null) {
            mActiveModelPerProvider.put(providerName, modelName);
        }
    }

    public void loadActiveState(Context context) {
        if (context == null) return;
        DatabaseHelper dbHelper = new DatabaseHelper(context.getApplicationContext());
        String activeProv = dbHelper.getSetting(SETTING_ACTIVE_PROVIDER);
        if (activeProv != null && !activeProv.trim().isEmpty()) {
            mActiveProviderName = activeProv.trim();
        } else {
            mActiveProviderName = DEFAULT_PROVIDER;
        }

        // Restore active model for active provider
        String savedActiveModel = dbHelper.getSetting(SETTING_ACTIVE_MODEL_PREFIX + mActiveProviderName);
        if (savedActiveModel != null && !savedActiveModel.trim().isEmpty()) {
            mActiveModelPerProvider.put(mActiveProviderName, savedActiveModel.trim());
        }

        for (AiProvider provider : mProviders.values()) {
            String pName = provider.getName();
            String savedModel = dbHelper.getSetting(SETTING_ACTIVE_MODEL_PREFIX + pName);
            if (savedModel != null && !savedModel.trim().isEmpty()) {
                mActiveModelPerProvider.put(pName, savedModel.trim());
            } else if (!mActiveModelPerProvider.containsKey(pName)) {
                mActiveModelPerProvider.put(pName, provider.getDefaultModel());
            }
        }
    }

    public void saveActiveSelection(Context context, String providerName, String modelName) {
        if (providerName == null || providerName.trim().isEmpty()) return;
        mActiveProviderName = providerName.trim();
        if (modelName != null && !modelName.trim().isEmpty()) {
            mActiveModelPerProvider.put(mActiveProviderName, modelName.trim());
        }

        if (context != null) {
            DatabaseHelper dbHelper = new DatabaseHelper(context.getApplicationContext());
            dbHelper.setSetting(SETTING_ACTIVE_PROVIDER, mActiveProviderName);
            if (modelName != null) {
                dbHelper.setSetting(SETTING_ACTIVE_MODEL_PREFIX + mActiveProviderName, modelName.trim());
            }
        }
    }

    // Retain loadActiveProvider for backwards compatibility
    public void loadActiveProvider(Context context) {
        loadActiveState(context);
    }

    // Retain saveActiveProvider for backwards compatibility
    public void saveActiveProvider(Context context, String providerName) {
        saveActiveSelection(context, providerName, getActiveModelName(providerName));
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
        String selectedModel = getActiveModelName(mActiveProviderName);
        return activeProvider.generateContent(context, apiKey, selectedModel, systemInstruction, history);
    }
}
