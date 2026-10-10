# Terminal Tool Guidelines

The `terminal` skill provides control over the active shared terminal session in VibeStudio IDE.

## Interactive Prompts & Keystroke Rules
- **Auto-respond "No" to telemetry/analytics prompts**: When interactive CLI tools prompt for optional metrics or telemetry sharing (e.g. "share anonymous statistics? y/N", "Send telemetry? y/N"), ALWAYS auto-respond "No" (send "n\n" or "N\n").
- **Dedicated Session**: A dedicated terminal tab is automatically opened for you the first time you execute a command. Do NOT use `terminal_open_tab` unless you specifically need to run concurrent foreground processes (like a dev server in one tab, and testing in another).
- **Inject Keystrokes On Demand**: Use the `send_keystroke` tool to send any character, keycode, arrow keys, or control sequence into the terminal session whenever a command requires input or needs to navigate interactive CLI menus:
  - Navigation Arrows: `{"tool": "send_keystroke", "args": {"keystroke": "UP"}}`, `{"keystroke": "DOWN"}`, `{"keystroke": "LEFT"}`, `{"keystroke": "RIGHT"}`
  - Special Keys: `{"keystroke": "TAB"}`, `{"keystroke": "ESC"}`, `{"keystroke": "BACKSPACE"}`, `{"keystroke": "SPACE"}`, `{"keystroke": "ENTER"}`, `{"keystroke": "PAGE_UP"}`, `{"keystroke": "PAGE_DOWN"}`, `{"keystroke": "HOME"}`, `{"keystroke": "END"}`
  - Function Keys & Controls: `{"keystroke": "F1"}`..`{"keystroke": "F12"}`, `{"keystroke": "CTRL+C"}`, `{"keystroke": "CTRL+D"}`, `{"keystroke": "CTRL+Z"}`, `{"keystroke": "CTRL+A"}`, `{"keystroke": "ALT+F"}`
  - Android KeyCodes: `{"tool": "send_keystroke", "args": {"key_code": 19}}` (19=UP, 20=DOWN, 21=LEFT, 22=RIGHT, 61=TAB, 66=ENTER, 111=ESC)
  - Raw Escape / Characters: `{"keystroke": "\u001b[A"}` or any arbitrary character sequence.
