# VibeStudio AI Refactoring Todo List

- [x] Reset branch base onto latest `Browser` branch (`25d5a0d`) with full Browser MCP and WebView integration
- [x] Create strongly-typed message model (`ChatMessage`, `ToolCall`, `ToolResult`, `AiResponse`)
- [x] Implement AI Provider interface (`AiProvider`) and Gemini implementation (`GeminiAiProvider`) loading `assets/ai/system_instruction.md`
- [x] Create output truncator (`OutputTruncator`) for 25KB truncation of command/tool execution results
- [x] Create history compressor (`HistoryCompressor`) for context summarizing when exceeding 100k tokens
- [x] Create tool parser (`ToolCallParser`) and execution manager (`ToolExecutionManager`)
- [x] Create dedicated connection layer (`ModelConnectionManager`) for API key and provider configuration
- [x] Refactor `ChatService` into clean facade delegating execution loop, output truncation, context compression, and connection management
- [x] Update `ToolMessageFactory` and tool views (`BaseToolMessageView`, `CustomStatusToolMessage`, etc.) to use typed `ChatMessage`
- [x] Update `ChatFragment` to interact with `ModelConnectionManager` and display typed chat messages
- [x] Verify clean build (`./build.sh`)
