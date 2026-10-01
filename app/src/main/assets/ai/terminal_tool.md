# Terminal Tool Guidelines

The `terminal` skill provides control over the active shared terminal session in VibeStudio IDE.

## Interactive Prompts & Keystroke Rules
- **Auto-respond "No" to telemetry/analytics prompts**: When interactive CLI tools prompt for optional metrics or telemetry sharing (e.g. "share anonymous statistics? y/N", "Send telemetry? y/N"), ALWAYS auto-respond "No" (send "n\n" or "N\n").
- **Prefer Non-Interactive Flags**: Where available, pass flags such as `--yes`, `-y`, `--no`, `CI=true`, or `DEBIAN_FRONTEND=noninteractive`.
- **Inject Keystrokes On Demand**: Use the `send_keystroke` tool to send raw input or control sequences into the terminal session whenever a command requires input or needs to be interrupted:
  - Respond "No": `{"planned_delay_ms": 0, "tool": "send_keystroke", "args": {"keystroke": "n\n"}}`
  - Respond "Yes": `{"planned_delay_ms": 0, "tool": "send_keystroke", "args": {"keystroke": "y\n"}}`
  - Press ENTER: `{"planned_delay_ms": 0, "tool": "send_keystroke", "args": {"keystroke": "ENTER"}}`
  - Interrupt / Cancel: `{"planned_delay_ms": 0, "tool": "send_keystroke", "args": {"keystroke": "CTRL+C"}}`
