package com.vibestudio.app.chat.router;

import android.content.Context;

import com.vibestudio.app.chat.model.AiResponse;
import com.vibestudio.app.chat.prompt.PromptAssets;
import com.vibestudio.app.chat.model.ChatMessage;
import com.vibestudio.app.mcp.model.McpTool;
import com.vibestudio.app.chat.provider.AiProvider;
import com.vibestudio.app.chat.provider.ClaudeAiProvider;
import com.vibestudio.app.chat.provider.GeminiAiProvider;
import com.vibestudio.app.chat.provider.GithubCopilotAiProvider;
import com.vibestudio.app.chat.provider.OpenAiProvider;
import com.vibestudio.app.db.DatabaseHelper;
import com.vibestudio.app.service.LogViewerService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AiRouter {

    private static final String TAG = "AiRouter";
    private static final String SETTING_ACTIVE_PROVIDER = "active_ai_provider";
    private static final String SETTING_ACTIVE_MODEL_PREFIX = "active_ai_model_";
    private static final String SETTING_CACHED_MODELS_PREFIX = "cached_ai_models_";
    private static final String SETTING_MODEL_CAPS_PREFIX = "cached_ai_model_caps_";
    private static final String SETTING_NATIVE_TOOLS_DOWNGRADED = "native_tools_downgraded";
    private static final String SETTING_USED_TOKENS_PREFIX = "token_used_";
    private static final String SETTING_BUDGET_TOKENS_PREFIX = "token_budget_";
    public static final String DEFAULT_PROVIDER = "Gemini";
    public static final int DEFAULT_TOKEN_BUDGET = 100000;

    private static AiRouter sInstance;

    private final Map<String, AiProvider> mProviders = new LinkedHashMap<>();
    private String mActiveProviderName = DEFAULT_PROVIDER;
    private final Map<String, String> mActiveModelPerProvider = new LinkedHashMap<>();
    /** "provider|model" entries whose API rejected native tools; they use the text protocol from then on. */
    private final Set<String> mNativeToolsDowngraded = Collections.synchronizedSet(new LinkedHashSet<String>());

    public AiRouter() {
        registerProvider(new GeminiAiProvider());
        registerProvider(new OpenAiProvider());
        registerProvider(new ClaudeAiProvider());
        registerProvider(new GithubCopilotAiProvider());
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
        AiProvider provider = getProvider(providerName);
        if (provider == null) {
            return selected;
        }

        if (selected != null && selected.contains("embedding")) {
            selected = null;
        }

        if (selected != null && !selected.trim().isEmpty()) {
            return selected.trim();
        }

        return provider.getDefaultModel();
    }

    public void setActiveModelName(String providerName, String modelName) {
        if (providerName != null && modelName != null) {
            mActiveModelPerProvider.put(providerName, modelName);
        }
    }

    private static String downgradeKey(String providerName, String modelName) {
        return providerName + "|" + modelName;
    }

    /**
     * Whether requests to this provider/model declare tools through function calling. False means the text
     * protocol: the provider or model can't do it, or its API already rejected it.
     */
    public boolean usesNativeTools(String providerName, String modelName) {
        AiProvider provider = getProvider(providerName);
        return provider != null
                && provider.supportsNativeTools(modelName)
                && !mNativeToolsDowngraded.contains(downgradeKey(providerName, modelName));
    }

    /** Whether the model may return several tool calls in one reply; only possible with native tools. */
    public boolean usesParallelToolCalls(String providerName, String modelName) {
        AiProvider provider = getProvider(providerName);
        return provider != null && usesNativeTools(providerName, modelName) && provider.supportsParallelToolCalls(modelName);
    }

    /** Remembers (and persists) that the model's API rejected native tools. */
    public void markNativeToolsUnsupported(Context context, String providerName, String modelName) {
        if (providerName == null || modelName == null) return;
        mNativeToolsDowngraded.add(downgradeKey(providerName, modelName));
        if (context != null) {
            StringBuilder sb = new StringBuilder();
            synchronized (mNativeToolsDowngraded) {
                for (String entry : mNativeToolsDowngraded) {
                    if (sb.length() > 0) sb.append(",");
                    sb.append(entry);
                }
            }
            new DatabaseHelper(context.getApplicationContext()).setSetting(SETTING_NATIVE_TOOLS_DOWNGRADED, sb.toString());
        }
        LogViewerService.getInstance().w(TAG, "Native tools rejected by " + providerName + " / " + modelName + "; using the text protocol for it from now on.");
    }

    public String getSystemInstruction(Context context) {
        AiProvider activeProvider = getActiveProvider();
        if (activeProvider != null) {
            return activeProvider.getSystemInstruction(context);
        }
        return PromptAssets.read(context, "system_instruction.md");
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

        String activeKey = dbHelper.getApiKey(mActiveProviderName);
        if (activeKey == null || activeKey.isEmpty()) {
            for (AiProvider provider : mProviders.values()) {
                String key = dbHelper.getApiKey(provider.getName());
                if (key != null && !key.isEmpty()) {
                    LogViewerService.getInstance().i(TAG, "Active provider '" + mActiveProviderName
                            + "' has no credentials; switching to configured provider '" + provider.getName() + "'");
                    mActiveProviderName = provider.getName();
                    dbHelper.setSetting(SETTING_ACTIVE_PROVIDER, mActiveProviderName);
                    break;
                }
            }
        }

        String downgraded = dbHelper.getSetting(SETTING_NATIVE_TOOLS_DOWNGRADED);
        if (downgraded != null && !downgraded.trim().isEmpty()) {
            for (String entry : downgraded.split(",")) {
                if (!entry.trim().isEmpty()) mNativeToolsDowngraded.add(entry.trim());
            }
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
            String cachedCaps = dbHelper.getSetting(SETTING_MODEL_CAPS_PREFIX + pName);
            if (cachedCaps != null && !cachedCaps.trim().isEmpty()) {
                provider.restoreModelCapabilitiesState(cachedCaps);
            }

            // Restore active model selection
            String savedModel = dbHelper.getSetting(SETTING_ACTIVE_MODEL_PREFIX + pName);
            if (savedModel != null && !savedModel.trim().isEmpty()) {
                String trimmed = savedModel.trim();
                if (trimmed.contains("embedding")) {
                    trimmed = provider.getDefaultModel();
                }
                mActiveModelPerProvider.put(pName, trimmed);
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
        AiProvider provider = getProvider(providerName);
        if (provider != null) {
            provider.setAvailableModels(models);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < models.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(models.get(i));
        }
        DatabaseHelper dbHelper = new DatabaseHelper(context.getApplicationContext());
        dbHelper.setSetting(SETTING_CACHED_MODELS_PREFIX + providerName, sb.toString());
        String capsState = provider != null ? provider.getModelCapabilitiesState() : null;
        if (capsState != null) {
            dbHelper.setSetting(SETTING_MODEL_CAPS_PREFIX + providerName, capsState);
        }
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

    public void loadActiveProvider(Context context) {
        loadActiveState(context);
    }

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
        return generateContent(context, apiKey, systemInstruction, history, null);
    }

    public AiResponse generateContent(Context context, String apiKey, String systemInstruction, List<ChatMessage> history, List<McpTool> tools) {
        AiProvider activeProvider = getProvider(mActiveProviderName);
        if (activeProvider == null) {
            throw new IllegalStateException("Active provider '" + mActiveProviderName + "' is not registered in AiRouter");
        }
        String selectedModel = getActiveModelName(mActiveProviderName);
        AiResponse response = activeProvider.generateContent(context, apiKey, selectedModel, systemInstruction, history, tools);

        if (response != null && !response.isSuccess() && response.getErrorMessage() != null) {
            String err = response.getErrorMessage().toLowerCase();
            if ((err.contains("unsupported_api_for_model") || err.contains("is not accessible")
                    || (err.contains("model") && (err.contains("not found") || err.contains("invalid"))))
                    && !selectedModel.equalsIgnoreCase(activeProvider.getDefaultModel())) {
                String fallbackModel = activeProvider.getDefaultModel();
                LogViewerService.getInstance().w(TAG, "Model '" + selectedModel + "' failed with error: "
                        + response.getErrorMessage() + ". Retrying with default model: " + fallbackModel);
                saveActiveSelection(context, mActiveProviderName, fallbackModel);
                response = activeProvider.generateContent(context, apiKey, fallbackModel, systemInstruction, history, tools);
            }
        }

        if (response != null && response.isSuccess() && response.getUsedTokens() > 0) {
            addUsedTokens(context, activeProvider.getName(), response.getUsedTokens());
        }
        return response;
    }
}
