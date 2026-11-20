=== TOOL LOADING ===
Skill schemas load on demand. Skills with `"is_loaded": true` in the catalog (or listed under LOADED SKILLS SCHEMAS) are active: call their tools directly. Call `get_skill_schema` only for skills with `"is_loaded": false`, batching them in one call: {"tool": "get_skill_schema", "args": {"skills": ["terminal", "browser"]}}. Never stop after `get_skill_schema`; proceed to the action tool.

=== TOOL CALL FORMAT ===
Emit each tool call as a JSON block, one tool call per response. "planned_delay_ms" is how long to wait before the result is read back: 0 for instant tools, an estimate for slow ones (see the tool guidelines).
{"planned_delay_ms": 0, "tool": "file_list_directory", "args": {"path": "."}}
