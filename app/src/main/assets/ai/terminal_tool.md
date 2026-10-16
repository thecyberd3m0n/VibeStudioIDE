# Terminal Tool Guidelines

The `terminal` skill provides control over the active shared terminal session in VibeStudio IDE.

## Command Execution Modes (`execute_command` vs `execute_command_async`)
- **`execute_command` (Synchronous / Instant)**: Use for quick, non-blocking bash shell commands (e.g., `ls`, `git status`, `cat file`, `pwd`, quick queries). Takes **no planned delay** (`planned_delay_ms: 0`) and returns results directly and instantly without waiting for future readouts, allowing the agent loop to restart immediately.
- **`execute_command_async` (Asynchronous / Long-running)**: Use for commands that start long-running background tasks, dev servers, watchers, or heavy build processes (e.g., `npm run dev`, `gradlew assembleDebug`, `npm install`). Supports and uses `planned_delay_ms` to pause for process startup or output before capturing initial snapshots. Use `read_terminal_output` to inspect output from ongoing async sessions.

## Non-Interactive & Automation Best Practices
- **Prefer Non-Interactive Flags**: Whenever possible, pass non-interactive flags or environment variables to CLI tools to bypass setup wizards (e.g., `npm create vite@latest my-app -- --template react`, `npm init -y`, `CI=true npm test`, `npx --yes ...`).
- **Auto-respond "No" to telemetry/analytics prompts**: When interactive CLI tools prompt for optional metrics or telemetry sharing (e.g. "share anonymous statistics? y/N", "Send telemetry? y/N"), respond "n" / "N".
- **Dedicated Session**: A dedicated terminal tab is automatically opened for you the first time you execute a command. Do NOT use `terminal_open_tab` unless you specifically need to run concurrent foreground processes (like a dev server in one tab, and testing in another).

## Interactive Prompts & Keystroke Rules
- **Inject Keystrokes On Demand**: Use the `send_keystroke` tool to send any character, keycode, arrow keys, or control sequence into the terminal session whenever a command requires input or needs to navigate interactive CLI menus:
  - Navigation Arrows: `{"tool": "send_keystroke", "args": {"keystroke": "UP"}}`, `{"keystroke": "DOWN"}`, `{"keystroke": "LEFT"}`, `{"keystroke": "RIGHT"}`
  - Special Keys: `{"keystroke": "TAB"}`, `{"keystroke": "ESC"}`, `{"keystroke": "BACKSPACE"}`, `{"keystroke": "SPACE"}`, `{"keystroke": "ENTER"}`, `{"keystroke": "PAGE_UP"}`, `{"keystroke": "PAGE_DOWN"}`, `{"keystroke": "HOME"}`, `{"keystroke": "END"}`
  - Function Keys & Controls: `{"keystroke": "F1"}`..`{"keystroke": "F12"}`, `{"keystroke": "CTRL+C"}`, `{"keystroke": "CTRL+D"}`, `{"keystroke": "CTRL+Z"}`, `{"keystroke": "CTRL+A"}`, `{"keystroke": "ALT+F"}`
  - Android KeyCodes: `{"tool": "send_keystroke", "args": {"key_code": 19}}` (19=UP, 20=DOWN, 21=LEFT, 22=RIGHT, 61=TAB, 66=ENTER, 111=ESC)
  - Raw Escape / Characters: `{"keystroke": "\u001b[A"}` or any arbitrary character sequence.
