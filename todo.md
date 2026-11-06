# Agentic Execution Loop & Architecture Refactoring Plan

## Architectural Overview
The current agent execution loop relies on standard string parsing of JSON blocks within normal text responses. When models (e.g. GitHub Copilot GPT-4o) generate conversational filler without emitting a structured tool call JSON, the execution loop terminates prematurely, forcing manual user intervention ("go").

To align VibeStudio with standard agentic loop patterns (such as VS Code Agent Sessions, OpenAI Function Calling loop, and LangChain Agent Executors), the loop architecture needs to be refactored systematically.

---

## Refactoring Tasks

### 1. Robust Tool Call Resolution & State Machine (`AgentLoopController`)
- **Task**: Separate loop control state from `ChatSession.java` into a dedicated `AgentLoopController` or `AgentTurnHandler`.
- **States**:
  - `IDLE`: Waiting for user input.
  - `EXECUTING_TOOL`: Tool execution in progress.
  - `AWAITING_MODEL_TURN`: Payload sent to LLM, waiting for tool call or final text output.
  - `STEP_COMPLETED`: Tool executed and output appended to history; automatically triggers next model turn.
  - `TASK_FINISHED`: Model generated final assistant output without tool calls, or explicitly signaled completion.

### 2. Multi-Modal Function Calling & Tool Injection Strategy
- **Task**: Standardize system instructions and JSON tool schemas across all providers (`GitHub Copilot`, `OpenAI`, `Claude`, `Gemini`).
- **Enforcement**:
  - Inject explicit system prompt stop-guards instructing model: "When executing multi-step tasks, EVERY response MUST contain a tool call JSON object until the task is complete. Never output conversational updates alone."
  - Handle partial responses and native function/tool call payloads directly where supported by the provider APIs.

### 3. Asynchronous Task & Terminal Monitoring Handlers
- **Task**: Improve async tool monitoring (`execute_command_async`, long-running builds).
- **Mechanism**:
  - Introduce an `AsyncToolMonitor` pattern that polls terminal logs/process status without depending on LLM conversational pauses.
  - Automatically feed terminal status updates back into the conversation history as `System Tool` messages to drive the next agent turn.

### 4. Continuous Turn Execution & Loop Guard
- **Task**: Provide clean max-turn guards, error recoveries, and cancellation handling without string matching heuristical hacks.
- **Guardrails**:
  - Support configurable maximum turn counts (`max_turns: 50`) per user request.
  - Maintain session history integrity and cleanly post model reasoning (`ReasoningToolMessage`) and UI updates on the main UI thread.

