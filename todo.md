# TODO: AI Agent Workspace Safety & Sandbox Architecture

> **Architecture & Implementation Scope:**
> We are not reimplementing VS Code's full low-level infrastructure (such as daemon helper processes).
> Instead, we focus on **3 core features** with our own tailored UX:
> 1. **Assigning workspace to chat session** (Workspace Scoping) - *Repository = Workspace*.
> 2. **Protecting rest of filesystem** (Boundary Protection & Permission Prompts).
> 3. **Keep / Undo of agent changes** (File Changeset Tracking & Rollback UI).

---

## 📍 Feature 1: Assigning Workspace to Chat Session (Workspace Scoping)
*Goal: Associate a workspace path (directory/repository) with a ChatSession, triggered via UI context menus.*

- [x] **Directory & Repository Context Menu Option**:
  - Add `"Open Scoped Chat"` to directory & repository context menus in the File Manager (`FileActionRegistry`).
  - When selected, create a new `ChatSession` bound to the selected directory path and open a dedicated Chat Tab.
- [x] **Session Workspace Domain Model**:
  - Store `workspacePath` in `ChatSession`.
  - Display workspace banner / label in `ChatFragment` (`📁 Workspace Scope: /path/to/repo`).
  - Pass the active workspace path into system prompt context (`<workspace_root>`).
- [x] **Path Contextualization**:
  - Canonicalize tool target paths relative to the session's assigned `workspacePath`.
  - Default terminal working directory (`cwd`) to the session's assigned workspace path.

---

## 🛡️ Feature 2: Protecting Rest of Filesystem (Boundary Protection)
*Goal: Prevent agent file read/write or terminal operations outside the assigned workspace scope without user approval.*

- [x] **Out-of-Bounds Detection (`WorkspaceBoundaryGuard`)**:
  - Intercept file tool calls (read, write, list, delete) and terminal commands targeting paths outside the assigned `workspacePath`.
  - Prevent relative path traversal attacks (`..` directory escape).
- [x] **Interactive Permission Escalation Prompt**:
  - Intercept out-of-bounds tool requests and display a user permission dialog:
    *"Agent is requesting access outside the workspace: <path>. Allow access?"*
- [x] **Session Access Grants**:
  - Maintain session-scoped permission grants for approved external paths.

---

## 🔄 Feature 3: Keep / Undo of Agent Changes (Workspace Sandbox & Rollback)
*Goal: Track changes made by the AI Agent and allow one-click Keep or Undo.*

- [x] **Changeset Tracking (`WorkspaceChangesetTracker`)**:
  - Capture pre-edit file states or git diff snapshots before agent file operations.
- [x] **Visual Diff & "Keep / Undo" UI**:
  - Render proposed/modified changes in chat/editor view with **Keep** and **Undo** buttons.
- [x] **Revert / Rollback Action**:
  - Restore modified files to their pre-agent snapshot when **Undo** is pressed.
