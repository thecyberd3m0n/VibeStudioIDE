# VibeStudio IDE - Code Quality & Architectural Guidelines

This document outlines the strict architectural principles, code rules, and design patterns established for VibeStudio IDE. All future sessions and developers must adhere to these guidelines to ensure clean plugin isolation, decoupling, and maintainability.

---

## 🏛️ Key Architectural Principles

### 1. Clean Plugin Architecture & Decentralization
- **Base as Loader/Framework**: The core application classes (`chat` engine, `service`, `fragments`, `view`) act strictly as a plugin framework/loader.
- **No Skill Hardcoding in Base**: Core engine components (such as `ChatSession`, `ToolExecutionManager`, `ToolMessageFactory`, `HistoryCompressor`) MUST NEVER contain skill names (`terminal`, `browser`, `file`, `editor`), specific tool names (`execute_command`, `browser_navigate`, `editor_open`), or skill-specific formatting logic.
- **Server Self-Containment**: Each MCP server (`TerminalMcpServer`, `BrowserMcpServer`, `FileMcpServer`, `EditorMcpServer`) is fully self-contained. Tool execution, tool call display formatting, tool-specific state post-processing (e.g. terminal output snapshots), and view creation are delegated directly to the respective server implementation.

---

## 🔄 Dynamic Lookup Over "If Spaghetti"

- **Loop Traversals Over Registry**: Use server list traversals (`McpClientManager.findServerForTool(...)`) instead of `if-else` or `switch-case` branching based on hardcoded tool names.
- **Polymorphic UI Instantiation**: UI factories (`ToolMessageFactory`) delegate view creation to `McpServer.createToolMessageView(context, toolName)`.
- **Dynamic Fallbacks**: If a tool view or message type is unregistered or unknown, generic status views (`CustomStatusToolMessage`) must be used as fallback rather than assuming a specific tool view (e.g., `TerminalToolMessage`).

---

## ⚙️ Meta-Tools vs Domain Tools

- **Meta-Tools (Base Framework Tools)**: Meta-tools (e.g., `get_skill_schema`) belong to the VibeStudio framework itself (`McpClientManager`) for inspecting, lazy-loading, and managing skills.
- **Domain Tools (Plugin Tools)**: Domain tools belong exclusively to their respective `McpServer` implementations.
- **Central Constants**: Framework-level meta-tools are referenced via constants (e.g., `McpClientManager.META_TOOL_GET_SKILL_SCHEMA`) and helper methods (`McpClientManager.isMetaTool(...)`) rather than duplicated string literals across classes.

---

## 📐 Verification & Build Standards

- **Clean Builds**: Always verify code changes by executing `./build.sh` and ensuring `bin/VibeStudio.apk` compiles cleanly without errors.
- **No Residual Dependencies**: When moving logic into an `McpServer`, strip out all unused domain imports (such as terminal or browser managers) from base engine classes.
