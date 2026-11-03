# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.6]

### Added
- **Custom Agents Support**: The chat can now discover and use custom agents (`AgentScanner`, `AgentToolPolicy`) with per-agent tool restrictions.
- **Instructions Loader**: Workspace instruction files are now loaded and applied to chat sessions (`InstructionsLoader`).
- **Markdown Frontmatter Parsing**: Skills and agents share a new `FrontmatterParser` for their markdown definitions.

### Changed
- **Skill Scanner**: Reworked `SkillScanner` on top of the shared frontmatter parser.
- **MCP Client**: `McpClientManager` extended to support agent-scoped tool access.

## [0.1.5]

### Changed
- **Permanent Release Signing Key**: Releases are now built by GitHub Actions and signed with a dedicated, permanent release keystore (certificate SHA-256 `48:47:89:0A:30:B0:EC:FA:1C:AE:94:1C:FD:10:2D:EC:DB:5B:7D:00:63:50:79:D6:C4:DC:51:F7:BD:CC:8F:B2`). Earlier releases were signed with a throwaway debug key, so upgrading from 0.1.4 or older requires uninstalling the old build once. All future releases will update in place.
- **CI Pipeline**: Added GitHub Actions workflows — debug build on every push/PR to `main`, and a tag-triggered (`v*`) release workflow that publishes a signed APK with SHA-256 checksum and changelog notes.

### Fixed
- **Gradle Release Build**: Fixed `assembleRelease` failing on a missing `consumer-rules.pro` in the bundled `terminal-view` module and on the `ExpiredTargetSdkVersion` lint error (`targetSdk 28` is intentional).

## [0.1.4]

### Fixed
- **Editor Text Coloring & Multidex Packaging**: Packaging all generated `classes*.dex` files into APK to prevent missing classes (TextMate syntax highlighting) and fixed editor text coloring in standalone builds.

## [0.1.3]

### Added
- **Git Commit History View**: Added commit history list and details view with avatar support for inspecting past commits and changed files.
- **Git Stage All / Unstage All & Stash**: Added quick actions to stage or unstage all changes, as well as stash and unstash working directory changes.
- **Log Viewer Text Selection**: Enhanced `LogViewerFragment` with custom selection controller and touch listener for selecting and copying log lines.

### Fixed
- **Git Checkout Strategy**: Improved branch checkout and checkout error handling in `GitManager`.

## [0.1.2]

### Added
- **Interactive Conflict Editor**: Added `ConflictEditorFragment` and `ConflictParser` for visual three-way merge conflict resolution directly in the editor.
- **Dedicated Diff Editor Fragment**: Extracted diff view logic into `DiffEditorFragment` with hunk accept/revert controls and overlay rendering.
- **Session Logging Infrastructure**: Added `SessionLogger` and `LogViewerService` for streaming, filtering, and capturing terminal process I/O and app logs.

### Fixed
- **Terminal Execution & Output Parsing**: Added terminal I/O taps and improved marker extraction (`extractBetweenMarkers`) in `TerminalSessionManager` to safely parse stdout without prompt echoes or leaks.
- **Git State & Onboarding Reliability**: Streamlined Git operations, ahead/behind status detection, and error handling during initial onboarding and branch switches.
- **Editor & UI Layout Improvements**: Refactored `EditorFragment` into `BaseEditorFragment`, improved file list layout in `FilesFragment`, and adjusted onboarding setup flows.

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
