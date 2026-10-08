# VibeStudio AI Model Refactoring Context

## Goals & Requirements
1. Remove hardcoded AI models.
2. For each provider with an API key, dynamically query available models from their respective API.
3. Create generic interface `AiProvider` with common methods:
   - `validateKey`
   - `getBillingInfo`
   - `fetchBudgetInfo` / `fetchBudgetInfoSync`
   - `fetchAvailableModels` / `fetchAvailableModelsSync`
   - `getAvailableModels` / `setAvailableModels`
   - `getDefaultModel`
   - `getSystemInstruction`
   - `generateContent`
4. Gather models on `MainActivity` start (`gatherModelsForConfiguredProviders`) and after API key validation in `ModelsFragment`.
5. Display budget bar + label ("n/y tokens used") for all providers in `ModelsFragment` UI when budget information is available (`totalBudget > 0`), querying live server budget information via `fetchBudgetInfo`.
6. Generic AI provider interface without single provider hardcoding (removed `GeminiValidator`, removed hardcoded `GeminiAiProvider.loadBaseSystemInstruction`).

## Completed Files
- `AiProvider.java`: Generic interface with callbacks, budget methods (`fetchBudgetInfo`, `BudgetInfo`), and provider methods.
- `AbstractAiProvider.java`: Abstract base provider with model caching, async callbacks, budget query defaults, default system instruction loading, and key censoring.
- `GeminiAiProvider.java`: Generic implementation extending `AbstractAiProvider`, implements `fetchAvailableModelsSync` via Gemini API.
- `OpenAiProvider.java`: Generic implementation extending `AbstractAiProvider`, implements `fetchAvailableModelsSync`, `fetchBudgetInfoSync`, & chat completions via OpenAI API.
- `ClaudeAiProvider.java`: Generic implementation extending `AbstractAiProvider`, implements `fetchAvailableModelsSync` & messages via Anthropic API.
- `AiRouter.java`: Manages providers, state persistence, token budget/usage tracking (`getTokenBudget`, `setTokenBudget`, `getUsedTokens`), cached models CSV, system instructions, and `gatherModelsForConfiguredProviders`.
- `MainActivity.java`: Calls `gatherModelsForConfiguredProviders` on start.
- `ModelsFragment.java`: Updated expander/collapser button look to be bigger (text size 20sp, padding 24x12) and positioned at the left side at vertical center of provider cards.
- `ModelsFragment.java`: Uses generic `providerObj.validateKey`, queries live budget via `provider.fetchBudgetInfo`, and displays progress bar with "n/y tokens used" label when budget information is available.
- `ChatService.java`: Uses generic `mAiRouter.getSystemInstruction(context)`.
- Removed `GeminiValidator.java`.

## Test Status
All tests passing (12 tests in unit/UI test suite).
