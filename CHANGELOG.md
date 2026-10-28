# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.1]

### Added
- **Git & Version Control Onboarding**: Integrated a Version Control setup step into `OnboardingActivity` (Step 4) allowing developers to configure Git author name/email and generate or copy SSH public keys.
- **Git Settings Sub-Tab**: Added a dedicated Git configuration sub-tab inside `SettingsFragment` (`GitSettingsFragment`) for post-onboarding Git identity and SSH key management.
- **Editor Word Wrap**: Added toggleable word wrapping support for file viewing and editing in `EditorFragment`.

### Fixed
- **Stateless Git Execution**: Refactored `GitManager` to operate statelessly and clear stale repository status when navigating away or switching directory contexts.
- **MCP Session ID Resolution**: Fixed session ID tracking for browser tabs and MCP tools in `ToolSessionManager` and `BrowserMcpServer`.
- **Notification Priority & Icons**: Restored proper notification icons and set default channel importance and priority for background terminal services.
- **Code Hygiene Guidelines**: Added the Anti-Slop Rule to `CODE_QUALITY.md` enforcing net code deletion preference for bugfixes.

## [0.1.0]

### Added
- **Terminal Foreground Service & WakeLock**: Bound active terminal sessions to `TerminalService` foreground service holding a `PARTIAL_WAKE_LOCK` to prevent background process/server suspension when switching apps. Added an **Exit** notification action to terminate active sessions and stop the service.
- **Built-in Git Manager & Status Panel**: Complete Git integration (`GitManager`, `GitStatusView`) in `FilesFragment` featuring real-time status inspection, branch dropdown selector, branch switching/creation, staging/unstaging, stashing, commits, and line addition/deletion counters (+N/-N). Includes a smooth swipe gesture panel with responsive 150ms snap animations.
- **In-Editor Diff Overlay & Hunk Operations**: Interactive diff viewer in `EditorFragment` with declarable hunk options, line-by-line highlight rendering, and Accept/Revert controls for individual hunks or entire files. Added TextMate diff grammar syntax highlighting.
- **Workspace Scoping & Safety Changeset Tracker**: Introduced `WorkspaceBoundaryGuard` for workspace scoping and `WorkspaceChangesetTracker` for real-time tracking of file creations, modifications, and deletions with one-click Keep/Undo rollback bar in the chat UI.
- **Chrome DevTools Protocol (CDP) & Browser Engine**: Implemented `CdpLocalClient` for persistent WebSocket CDP connections, supporting native DOM file upload inputs and interactive browser control.
- **Workspace Skills Scanner & Actions**: Added `SkillScanner` to discover workspace skills dynamically and added "Open Scoped Chat" file action.
- **Log Viewer & Filtering**: Enhanced `LogViewerFragment` with real-time log streaming, log level filtering, search, and export capabilities.

### Fixed
- **Git Execution & Parser Sanitization**: Resolved compound shell subcommand syntax errors in `GitManager` and sanitized terminal stdout line echoes to prevent parser corruption.
- **Diff Hunk Recursion**: Fixed a recursive overlay update loop (`StackOverflowError`) during post-revert hunk updates by decoupling diff revert listeners.
- **Chat UI Theme Tinting**: Fixed changeset bar button background tinting issues by utilizing clickable `TextView` elements.

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
