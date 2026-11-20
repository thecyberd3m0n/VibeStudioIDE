package com.vibestudio.app.chat.provider;

import android.content.Context;

import com.vibestudio.app.chat.model.AiResponse;
import com.vibestudio.app.chat.model.ChatMessage;
import com.vibestudio.app.mcp.model.McpTool;

import java.util.List;

public interface AiProvider {

    class BudgetInfo {
        private final long usedTokens;
        private final long totalBudget;

        public BudgetInfo(long usedTokens, long totalBudget) {
            this.usedTokens = usedTokens;
            this.totalBudget = totalBudget;
        }

        public long getUsedTokens() {
            return usedTokens;
        }

        public long getTotalBudget() {
            return totalBudget;
        }
    }

    interface BudgetCallback {
        void onSuccess(BudgetInfo budgetInfo);
        void onError(String errorMessage);
    }

    interface ValidationCallback {
        void onSuccess(List<String> models);
        void onError(String errorMessage);
    }

    interface ModelsCallback {
        void onSuccess(List<String> models);
        void onError(String errorMessage);
    }

    String getName();

    List<String> getAvailableModels();

    void setAvailableModels(List<String> models);

    String getDefaultModel();

    void fetchAvailableModels(String apiKey, ModelsCallback callback);

    List<String> fetchAvailableModelsSync(String apiKey) throws Exception;

    void validateKey(String apiKey, ValidationCallback callback);

    String getBillingInfo(String apiKey);

    BudgetInfo fetchBudgetInfoSync(String apiKey) throws Exception;

    void fetchBudgetInfo(String apiKey, BudgetCallback callback);

    String getSystemInstruction(Context context);

    AiResponse generateContent(Context context, String apiKey, String selectedModel, String systemInstruction, List<ChatMessage> history);

    /**
     * Whether this provider can declare {@code tools} through its native function-calling API and return structured
     * tool calls. When false, the model must use the text protocol (JSON tool calls written in its reply).
     */
    default boolean supportsNativeTools() {
        return false;
    }

    /** Per-model refinement of {@link #supportsNativeTools()}; providers whose models differ override this. */
    default boolean supportsNativeTools(String model) {
        return supportsNativeTools();
    }

    /**
     * Whether the model may return several tool calls in one reply (parallel function calling). Provider-wide
     * answer; false when unsure. Providers whose models differ override the per-model variant.
     */
    default boolean supportsParallelToolCalls() {
        return false;
    }

    /** Per-model refinement of {@link #supportsParallelToolCalls()}. */
    default boolean supportsParallelToolCalls(String model) {
        return supportsParallelToolCalls();
    }

    /** Serialized per-model capability data to cache next to the model list, or null when the provider has none. */
    default String getModelCapabilitiesState() {
        return null;
    }

    /** Restores what {@link #getModelCapabilitiesState()} returned in an earlier run. */
    default void restoreModelCapabilitiesState(String state) {
    }

    /**
     * Like the overload above, but declares {@code tools} through the provider's native function-calling API.
     * A native call comes back as {"tool": ..., "args": ...} JSON in {@link AiResponse#getContent()}.
     * Providers without native support ignore {@code tools}.
     */
    default AiResponse generateContent(Context context, String apiKey, String selectedModel, String systemInstruction, List<ChatMessage> history, List<McpTool> tools) {
        return generateContent(context, apiKey, selectedModel, systemInstruction, history);
    }
}
