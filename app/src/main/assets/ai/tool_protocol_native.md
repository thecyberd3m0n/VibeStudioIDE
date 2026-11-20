=== TOOLS ===
The tools are declared with this request: call them directly through function calling, do not write tool calls as text. Use `get_skill_schema` only for the custom skills or agents listed in the catalog below, never for the built-in skills (file, editor, terminal, browser). Never stop after `get_skill_schema`; proceed to the action tool.
Tools that wait on slow operations accept an optional "planned_delay_ms" argument: how long to wait before the result is read back (0 or omitted for instant operations, an estimate for slow ones; see the tool guidelines).
