# VibeStudio IDE - Code Quality & Architectural Guidelines

This document outlines the strict architectural principles, code rules, and design patterns established for VibeStudio IDE. All future sessions, developers, and AI agents MUST adhere to these guidelines to ensure clean plugin isolation, decoupling, concurrency safety, and maintainability.

---

## ⚡ Pragmatic Engineering & Locality of Behavior

> **Core Philosophy:** Dogmatic "Clean Code" rules (such as "keep methods under 20 lines", "keep classes tiny", or "never duplicate code / DRY at all costs") lead to architectural fragmentation—splitting single operations into dozens of tiny classes, interfaces, and factories. Real elegance is **Locality of Behavior**: being able to read, debug, and understand what the system does without leaving the screen.

1. **The Funnel Pattern Beats Micro-Classes**: A great class or module has narrow, clean entry points and ONE deep, cohesive execution engine. If that engine needs 500 lines to execute a complex domain pipeline, keep those 500 lines together in one place behind a clean boundary instead of spraying them across shallow classes.
2. **Clean Outline Over Method Fragmentation**: A class's public outline must declare **WHAT** it does (domain concepts), not **HOW** it does it (internal string splitting or regex details). Exposing private helpers as public or creating shallow helper classes litters the outline. Keep internal parsing and formatting methods `private` (or `private static`).
3. **Respect the Timeline & Locality of Behavior (Temporal Locality)**: Code that executes together in time ($T_0 \rightarrow T_1 \rightarrow T_2$) must live together in space. Stepping through code (`F10`) in a debugger should move smoothly down the screen like reading a book, without snapping your viewport across files or up and down like a pinball machine.
4. **Stop Turning Verbs Into Classes**: If a class holds no state and only exists to wrap a single function (e.g. `GitTerminalParser` or `ToolCallParser`), it is not an object—it is a function wearing a class trench coat. Delete micro-parser classes and funnel execution into the domain manager.
5. **Optimize for Deleteability, Not Reusability**: Write code that is easy to remove. When a feature is deprecated, you should be able to delete one method or file without causing a domino collapse across 12 packages and 40 mock tests. Prefer pragmatic local duplication over tight coupling to shared, overly abstract helpers.
6. **Funnel Single-Use Helper Methods**: Functions or private helper methods that are invoked in only one place MUST be funneled directly into their caller implementation. Creating a single-use wrapper method adds useless indirection, clutters the class outline, and forces developers to jump around the file to read a single workflow. Keep the code inline where it runs unless it is reused in multiple call sites.

---

## 🏛️ Key Architectural Principles

### 1. Clean Plugin Architecture & Decentralization
- **Base as Loader/Framework**: Core application classes (`ChatSession`, `ToolExecutionManager`, `TabManager`, `MainActivity`) act strictly as a plugin framework/loader.
- **No Skill Hardcoding in Base**: Core engine components MUST NEVER contain skill names (`terminal`, `browser`, `file`, `editor`), specific tool names (`execute_command`, `browser_navigate`, `editor_open`), or skill-specific formatting logic.
- **Server Self-Containment**: Each MCP server (`TerminalMcpServer`, `BrowserMcpServer`, `FileMcpServer`, `EditorMcpServer`) is fully self-contained. Pre-execution state preparation (`prepareToolCall`), tool call display formatting, post-processing, and view creation are delegated directly to the respective server implementation.

---

## 🔒 Layer Separation & Upward Dependency Protection

- **Lower-Level Classes Are Tool-Agnostic**: Lower-level infrastructure classes (e.g. `TerminalSessionManager`, `CdpLocalClient`, `PathResolver`) MUST NEVER depend on, import, or invoke higher-level domain managers (`GitManager`, `ChatSession`).
  - *Example Violation*: `TerminalSessionManager` calling `GitManager.cleanTerminalOutput` is an upward dependency bug. If the system was used for SVN or custom terminal tools, this would break.
  - *Correct Pattern*: `TerminalSessionManager` returns raw captured output. `GitManager` (the higher-level domain manager) calls `TerminalSessionManager.runCommandOnSessionSync(...)` and applies its own `cleanTerminalOutput` or `cleanRawFileOutput`.

---

## 🌐 Domain-Driven APIs Over Raw Shell Commands in UI

- **UI Classes Consume Domain APIs**: UI components (`GitStatusView`, fragments, dialogs) MUST NEVER construct or execute raw shell strings (`"branch -a --format=..."`, `"commit -m '...'"`).
- **Domain Managers Hold Command Details**: Domain managers (`GitManager`) must expose clean, high-level, domain-driven methods (`fetchBranches`, `checkoutBranch`, `stageFile`, `unstageFile`, `commit`, `revertFile`) that encapsulate shell formatting and POSIX quote escaping (`quotePath`) internally.

---

## 🚦 Concurrency, Deadlocks & Non-Blocking Process Execution

### 1. Lock Inversion & Monitor Deadlock Prevention
- **Avoid Nested Object Monitors**: NEVER hold a lock on one monitor (`session`) while calling a `synchronized` method on another monitor (`this` / manager) when the manager also acquires locks on `session`.
- **Dedicated Private Lock Objects**: Serialize execution loops using a dedicated private lock (`private final Object mCommandLock = new Object();`).
- **Non-Synchronized State Reads**: Read-only state/transcript inspectors (`getSessionOutput`) must remain non-synchronized on `this` so background execution threads and UI refresh threads never enter opposing lock acquisition sequences.

### 2. Asynchronous Stream Draining & Process Deadlines
- **No Blocking Output Reads on Calling Thread**: NEVER perform a blocking `reader.read()` loop on a child process's stdout/stderr on the calling thread while holding a manager monitor or relying on a trailing `process.waitFor(timeoutMs)`. Stalled network operations or unprompted credentials keep stdout open indefinitely, causing `reader.read()` to block forever.
- **Background Drain Thread**: Drain process output asynchronously on a dedicated background reader thread while enforcing deadlines on the main thread via `process.waitFor(effectiveTimeout, TimeUnit.MILLISECONDS)`.
- **Forceful Termination on Timeout**: If a process exceeds its deadline, forcefully kill it (`process.destroyForcibly()`), interrupt the reader thread, log the timeout warning, and return `null`.

---

## 🗂️ Self-Contained Enum Types & Declarative Tab Dispatch

- **Declarative Tab Types**: Enum types representing tab concepts (`TabType`) must hold their own default titles, icons, AND Fragment class bindings (`Class<? extends Fragment>`).
  - *Example*: `BROWSER("Browser", "🌐", BrowserFragment.class)`
- **Standardized Reflection Instantiation**: `TabType.createDefaultFragment()` instantiates fragments uniformly via `fragmentClass.getDeclaredConstructor().newInstance()`.
- **Caller Simplicity**: Callers simply invoke `mTabManager.openTab(TabType.BROWSER)` or `mTabManager.openTab(TabType.FILES)` without specifying raw emojis, default titles, or fragment factories.

---

## 📄 Byte-Level Protocol Integrity & Exact Content Preservation

- **Terminal Output Cleaning vs Raw File Retrieval**: Distinguish between **terminal status cleaning** (`cleanTerminalOutput`, trimming prompt padding/whitespace) and **raw file content retrieval** (`cleanRawFileOutput`, e.g. `git show HEAD:<path>`).
- **Preserve Trailing Newlines**: Raw file content retrieval MUST preserve exact original file bytes including trailing newlines (`\n`) and whitespace, ensuring "Revert All" and diff operations restore 100% byte-for-byte identical files.
- **Complete HTTP & WebSocket Length Parsing**: Network socket readers (`CdpLocalClient`) MUST parse `Content-Length` headers for HTTP responses (>8KB) and decode 64-bit extended payload lengths (`payloadLen == 127`) for WebSocket frames (>65,535 bytes) to prevent JSON truncation and stream misalignment.

---

## 📂 Consistent Path Resolution & Workspace Boundary Enforcement

- **Shared PathResolver Utility**: All file path parameters across MCP servers MUST be resolved through `PathResolver.resolveFile(pathArg, context)`.
- **Scoped Workspace Resolution**: Relative paths and omitted/dot directory paths (`"."` in `file_list_directory`) MUST be populated with `workspacePath` before boundary checking and execution, ensuring commands resolve within the active project workspace rather than falling back to `$HOME`.
- **Non-Bypassable Boundary Guard**: `WorkspaceBoundaryGuard.getToolTargetPaths` must always return non-empty target paths for file operations so boundary validation (`isWithinWorkspace`) is strictly enforced.

---

## 📐 Verification & Build Standards

- **Clean Builds**: Always verify code changes by executing `./build.sh` and ensuring `bin/VibeStudio.apk` compiles cleanly without errors.
- **No Residual Dependencies**: When refactoring or moving logic, strip out all unused domain imports and verify code quality using static analysis (`analyze_file`).
