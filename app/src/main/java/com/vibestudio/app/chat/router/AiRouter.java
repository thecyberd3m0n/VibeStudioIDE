package com.vibestudio.app.chat.router;

import android.content.Context;

import com.vibestudio.app.chat.model.AiResponse;
import com.vibestudio.app.chat.model.ChatMessage;
import com.vibestudio.app.chat.provider.AiProvider;
import com.vibestudio.app.chat.provider.ClaudeAiProvider;
import com.vibestudio.app.chat.provider.GeminiAiProvider;
import com.vibestudio.app.chat.provider.OpenAiProvider;
import com.vibestudio.app.db.DatabaseHelper;
import com.vibestudio.app.service.LogViewerService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AiRouter {

    private static final String TAG = "AiRouter";
    private static final String SETTING_ACTIVE_PROVIDER = "active_ai_provider";
    private static final String SETTING_ACTIVE_MODEL_PREFIX = "active_ai_model_";
    private static final String SETTING_CACHED_MODELS_PREFIX = "cached_ai_models_";
    private static final String SETTING_USED_TOKENS_PREFIX = "token_used_";
    private static final String SETTING_BUDGET_TOKENS_PREFIX = "token_budget_";
    public static final String DEFAULT_PROVIDER = "Gemini";
    public static final int DEFAULT_TOKEN_BUDGET = 100000;

    private static AiRouter sInstance;

    private final Map<String, AiProvider> mProviders = new LinkedHashMap<>();
    private String mActiveProviderName = DEFAULT_PROVIDER;
    private final Map<String, String> mActiveModelPerProvider = new LinkedHashMap<>();

    public AiRouter() {
        registerProvider(new GeminiAiProvider());
        registerProvider(new OpenAiProvider());
        registerProvider(new ClaudeAiProvider());
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

    public AiProvider getActiveProvider() {
        AiProvider provider = getProvider(mActiveProviderName);
        if (provider == null && !mProviders.isEmpty()) {
            return mProviders.values().iterator().next();
        }
        return provider;
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

    public String getSystemInstruction(Context context) {
        AiProvider activeProvider = getActiveProvider();
        if (activeProvider != null) {
            return activeProvider.getSystemInstruction(context);
        }
        if (context != null) {
            try (java.io.InputStream in = context.getAssets().open("ai/system_instruction.md");
                 java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                return sb.toString();
            } catch (Exception e) {
                com.vibestudio.app.service.LogViewerService.getInstance().w(TAG, "Failed to load system_instruction.md from assets/ai", e);
            }
        }
        return "";
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

        for (AiProvider provider : mProviders.values()) {
            String pName = provider.getName();

            // Load cached models from Database
            String cachedModelsCsv = dbHelper.getSetting(SETTING_CACHED_MODELS_PREFIX + pName);
            if (cachedModelsCsv != null && !cachedModelsCsv.trim().isEmpty()) {
                String[] split = cachedModelsCsv.split(",");
                List<String> cachedList = new ArrayList<>();
                for (String s : split) {
                    if (!s.trim().isEmpty()) {
                        cachedList.add(s.trim());
                    }
                }
                if (!cachedList.isEmpty()) {
                    provider.setAvailableModels(cachedList);
                }
            }

            // Restore active model selection
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

    public void saveCachedModels(Context context, String providerName, List<String> models) {
        if (context == null || providerName == null || models == null || models.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < models.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(models.get(i));
        }
        DatabaseHelper dbHelper = new DatabaseHelper(context.getApplicationContext());
        dbHelper.setSetting(SETTING_CACHED_MODELS_PREFIX + providerName, sb.toString());
    }

    public int getUsedTokens(Context context, String providerName) {
        if (context == null || providerName == null) return 0;
        DatabaseHelper dbHelper = new DatabaseHelper(context.getApplicationContext());
        String val = dbHelper.getSetting(SETTING_USED_TOKENS_PREFIX + providerName);
        if (val != null) {
            try {
                return Integer.parseInt(val.trim());
            } catch (NumberFormatException ignored) {}
        }
        return 0;
    }

    public void addUsedTokens(Context context, String providerName, int count) {
        if (context == null || providerName == null || count <= 0) return;
        int current = getUsedTokens(context, providerName);
        DatabaseHelper dbHelper = new DatabaseHelper(context.getApplicationContext());
        dbHelper.setSetting(SETTING_USED_TOKENS_PREFIX + providerName, String.valueOf(current + count));
    }

    public int getTokenBudget(Context context, String providerName) {
        if (context == null || providerName == null) return DEFAULT_TOKEN_BUDGET;
        DatabaseHelper dbHelper = new DatabaseHelper(context.getApplicationContext());
        String val = dbHelper.getSetting(SETTING_BUDGET_TOKENS_PREFIX + providerName);
        if (val != null) {
            try {
                return Integer.parseInt(val.trim());
            } catch (NumberFormatException ignored) {}
        }
        return DEFAULT_TOKEN_BUDGET;
    }

    public void setTokenBudget(Context context, String providerName, int budget) {
        if (context == null || providerName == null || budget <= 0) return;
        DatabaseHelper dbHelper = new DatabaseHelper(context.getApplicationContext());
        dbHelper.setSetting(SETTING_BUDGET_TOKENS_PREFIX + providerName, String.valueOf(budget));
    }

    public int getTokensLeft(Context context, String providerName) {
        int budget = getTokenBudget(context, providerName);
        int used = getUsedTokens(context, providerName);
        return Math.max(0, budget - used);
    }

    public void gatherModelsForConfiguredProviders(final Context context, final Runnable onComplete) {
        if (context == null) {
            if (onComplete != null) onComplete.run();
            return;
        }

        final List<AiProvider> providers = getProviders();
        final DatabaseHelper dbHelper = new DatabaseHelper(context.getApplicationContext());
        final int total = providers.size();
        final int[] counter = new int[]{0};

        LogViewerService.getInstance().i(TAG, "Gathering available models for configured providers...");

        for (final AiProvider provider : providers) {
            final String apiKey = dbHelper.getApiKey(provider.getName());
            if (apiKey != null && !apiKey.isEmpty()) {
                provider.fetchAvailableModels(apiKey, new AiProvider.ModelsCallback() {
                    @Override
                    public void onSuccess(List<String> models) {
                        saveCachedModels(context, provider.getName(), models);
                        LogViewerService.getInstance().i(TAG, "Successfully gathered " + models.size() + " models for " + provider.getName());
                        checkComplete();
                    }

                    @Override
                    public void onError(String errorMessage) {
                        LogViewerService.getInstance().w(TAG, "Failed to gather models for " + provider.getName() + ": " + errorMessage);
                        checkComplete();
                    }

                    private synchronized void checkComplete() {
                        counter[0]++;
                        if (counter[0] >= total && onComplete != null) {
                            onComplete.run();
                        }
                    }
                });
            } else {
                synchronized (this) {
                    counter[0]++;
                    if (counter[0] >= total && onComplete != null) {
                        onComplete.run();
                    }
                }
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
        return apiKey;
    }

    public AiResponse generateContent(Context context, String apiKey, String systemInstruction, List<ChatMessage> history) {
        AiProvider activeProvider = getProvider(mActiveProviderName);
        if (activeProvider == null) {
            throw new IllegalStateException("Active provider '" + mActiveProviderName + "' is not registered in AiRouter");
        }
        String selectedModel = getActiveModelName(mActiveProviderName);
        AiResponse response = activeProvider.generateContent(context, apiKey, selectedModel, systemInstruction, history);
        if (response != null && response.isSuccess() && response.getUsedTokens() > 0) {
            addUsedTokens(context, activeProvider.getName(), response.getUsedTokens());
        }
        return response;
    }
}
