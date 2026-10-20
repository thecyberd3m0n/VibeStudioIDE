# VibeStudio IDE - Code Quality & Architectural Guidelines

This document outlines the strict architectural principles, code rules, and design patterns established for VibeStudio IDE. All future sessions and developers must adhere to these guidelines to ensure clean plugin isolation, decoupling, and maintainability.

---

## ⚡ Pragmatic Engineering & Locality of Behavior

> **Core Philosophy:** Dogmatic "Clean Code" rules (such as "keep methods under 20 lines", "keep classes tiny", or "never duplicate code / DRY at all costs") lead to architectural fragmentation—splitting single operations into dozens of tiny classes, interfaces, and factories. Real elegance is **Locality of Behavior**: being able to read, debug, and understand what the system does without leaving the screen.

1. **The Funnel Pattern Beats Micro-Classes**: A great class or module has narrow, clean entry points and ONE deep, cohesive execution engine. If that engine needs 500 lines to execute a complex domain pipeline, keep those 500 lines together in one place behind a clean boundary instead of spraying them across shallow classes.
2. **Scoped Functions Over Private Class Methods**: Private methods leak across the entire class, mutate class-level fields, and invite accidental reuse. Define helper functions locally inside the workflow where they are used (or scoped strictly to that execution context). They live there, die there, and delete with the workflow without polluting class state.
3. **Respect the Timeline & Locality of Behavior (Temporal Locality)**: Code that executes together in time ($T_0 \rightarrow T_1 \rightarrow T_2$) must live together in space. Stepping through code (`F10`) in a debugger should move smoothly down the screen like reading a book, without snapping your viewport across files or up and down like a pinball machine. Understand what the system does without leaving the screen.
4. **Stop Turning Verbs Into Classes**: If a class holds no state and only exists to wrap a single function (e.g. `ReceivedStringProcessor` or `UserPayloadValidationManager`), it is not an object—it is a function wearing a class trench coat. Use pure functions or plain methods instead.
5. **Optimize for Deleteability, Not Reusability**: Write code that is easy to remove. When a feature is deprecated, you should be able to delete one method or file without causing a domino collapse across 12 packages and 40 mock tests. Prefer pragmatic local duplication over tight coupling to shared, overly abstract helpers.

---

## 🏛️ Key Architectural Principles

### 1. Clean Plugin Architecture & Decentralization
- **Base as Loader/Framework**: The core application classes (`chat` engine, `service`, `fragments`, `view`) act strictly as a plugin framework/loader.
- **No Skill Hardcoding in Base**: Core engine components (such as `ChatSession`, `ToolExecutionManager`, `ToolMessageFactory`, `HistoryCompressor`) MUST NEVER contain skill names (`terminal`, `browser`, `file`, `editor`), specific tool names (`execute_command`, `browser_navigate`, `editor_open`), or skill-specific formatting logic.
- **Server Self-Containment**: Each MCP server (`TerminalMcpServer`, `BrowserMcpServer`, `FileMcpServer`, `EditorMcpServer`) is fully self-contained. Tool execution, tool call display formatting, tool-specific state post-processing (e.g. terminal output snapshots), and view creation are delegated directly to the respective server implementation.

---

## 📂 Consistent Path Resolution

- **Shared PathResolver Utility**: All file path parameters across MCP servers (`FileMcpServer`, `EditorMcpServer`, etc.) MUST be resolved through `PathResolver.resolveFile(pathArg, context)`.
- **Workspace Home Resolution**: Relative paths are automatically resolved relative to the workspace home directory (`$HOME` or `/data/data/com.termux/files/home`). Absolute paths and tilde (`~`) paths are properly handled and preserved.

---

## 🔄 Dynamic Lookup Over "If Spaghetti"

- **Loop Traversals Over Registry**: Use server list traversals (`McpClientManager.findServerForTool(...)`) instead of `if-else` or `switch-case` branching based on hardcoded tool names.
- **Polymorphic UI Instantiation**: UI factories (`ToolMessageFactory`) delegate view creation to `McpServer.createToolMessageView(context, toolName)`.
- **Dynamic Fallbacks**: If a tool view or message type is unregistered or unknown, generic status views (`CustomStatusToolMessage`) must be used as fallback rather than assuming a specific tool view (e.g., `TerminalToolMessage`).

---

## 🔝 Tool Prioritization & Zero-Delay Overhead for Simple Tools

- **Tier Declaration via Code**: Priority tiers are declared directly within each `McpServer` implementation via `McpServer.getPriority()`. The framework NEVER hardcodes tool categories or tiers in core engine logic.
  - **Tier 1 (Simpler/Lightweight Tools)**: `FileMcpServer` and `EditorMcpServer` return priority `1`.
  - **Tier 2 (Complex/Heavyweight Tools)**: `TerminalMcpServer` and `BrowserMcpServer` return priority `2`.
- **Zero-Delay Overhead for Tier 1 & Meta Tools**: Synchronous tools (`supportsPlannedDelay() == false`, defaulting to `getPriority() <= 1` or meta tools) override planned delays to `0ms` inside `ToolExecutionManager`. Asynchronous background readout delays are reserved exclusively for Tier 2 operations (e.g. long-running terminal commands or browser navigation).
- **Dynamic Catalog Sorting & Batch Loading**: `McpClientManager` dynamically sorts registered servers according to `server.getPriority()` when generating high-level skill catalogs, includes `"is_loaded": true/false` flags, and supports batch schema loading (`get_skill_schema` with `skills: ["file", "terminal", "browser"]`) to eliminate multi-turn setup roundtrips.

---

## ⚙️ Meta-Tools vs Domain Tools

- **Meta-Tools (Base Framework Tools)**: Meta-tools (e.g., `get_skill_schema`) belong to the VibeStudio framework itself (`McpClientManager`) for inspecting, lazy-loading, and managing skills.
- **Domain Tools (Plugin Tools)**: Domain tools belong exclusively to their respective `McpServer` implementations.
- **Central Constants**: Framework-level meta-tools are referenced via constants (e.g., `McpClientManager.META_TOOL_GET_SKILL_SCHEMA`) and helper methods (`McpClientManager.isMetaTool(...)`) rather than duplicated string literals across classes.

---

## 📐 Verification & Build Standards

- **Clean Builds**: Always verify code changes by executing `./build.sh` and ensuring `bin/VibeStudio.apk` compiles cleanly without errors.
- **No Residual Dependencies**: When moving logic into an `McpServer`, strip out all unused domain imports (such as terminal or browser managers) from base engine classes.
