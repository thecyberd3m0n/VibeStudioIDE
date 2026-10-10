# Editor Tool Guidelines

The `editor` skill provides token-efficient single-file read and modification tools in VibeStudio IDE.

## Token Efficiency Rules
1. **Line Range Reads**: Use `editor_read_file` with `start_line` and `end_line` parameters when inspecting specific sections of large files instead of loading entire files.
2. **Targeted Replacements**:
   - Use `editor_replace_lines` to update specific line ranges or code blocks without sending the entire file back and forth.
   - Provide exact line numbers or search patterns to keep modifications token-efficient.
3. **Open & Save**:
   - Use `editor_open` to open a file in an Editor tab view in the background or foreground.
   - Use `editor_save` to persist edits to disk.
