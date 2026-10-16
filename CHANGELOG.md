# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.0.4]

### Added
- **Command Execution Differentiation**: Split terminal command execution into `execute_command` (synchronous, zero planned delay, instant return) and `execute_command_async` (asynchronous background execution supporting `planned_delay_ms` for long-running processes or dev servers).
- **Tool Prioritization Tier Hierarchy**: Categorized tools into Tier 1 (lightweight tools: `file`, `editor`) and Tier 2 (heavyweight tools: `terminal`, `browser`) to optimize tool selection and context window usage.
- **Lazy & Batch Skill Schema Loading**: Added dynamic skill catalog sorting (`is_loaded` state) and batch skill schema loading via `get_skill_schema` meta-tool.
- **Shared Path Resolver**: Introduced `PathResolver` utility for consistent path resolution (`~`, relative to workspace home, absolute) across all file and editor tools.
- **Robolectric Unit Tests**: Added `TestMCPServerTest` to test custom MCP server registration, tool execution, and LLM provider integration.
- **Architectural Guidelines**: Created `CODE_QUALITY.md` documenting plugin architecture, self-containment, and zero-delay execution rules.

### Changed
- **System Instructions & Tool Prompts**: Updated system instructions (`system_instruction.md`) and skill guidelines (`terminal_tool.md`) to reflect priority tiers, zero-delay rules, and `execute_command` vs `execute_command_async` execution modes.
- **Zero-Delay Overhead for Synchronous Tools**: Automatically override `planned_delay_ms` to 0 for synchronous operations (Tier 1 tools, meta-tools, and `execute_command`).

### Fixed / Refactored
- **MCP Plugin Architecture**: Fully decoupled MCP servers (`TerminalMcpServer`, `BrowserMcpServer`, `FileMcpServer`, `EditorMcpServer`) from core chat engine components (`ChatSession`, `ToolExecutionManager`, `ToolMessageFactory`).
- **Clean Chat UI & Output Suppression**: Suppressed raw bloated MCP tool execution outputs and redundant UI notifications across all tool views (`TerminalToolMessage`, `BrowserToolMessage`, `CustomStatusToolMessage`) to keep the chat interface clean.
- **Custom Status View Rendering**: Blocked arbitrary plain text and cleaned up `CustomStatusToolMessage` rendering for meta-tools and skill loading states.
