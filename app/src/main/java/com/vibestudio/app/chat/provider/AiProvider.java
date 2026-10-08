package com.vibestudio.app.chat.provider;

import android.content.Context;

import com.vibestudio.app.chat.model.AiResponse;
import com.vibestudio.app.chat.model.ChatMessage;

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
}
