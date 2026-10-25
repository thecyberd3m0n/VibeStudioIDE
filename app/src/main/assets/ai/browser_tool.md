# Skill: browser

Use the browser tool to view web pages, inspect UI, take screenshots, click elements, and interact with web applications.

## Key Capabilities:
- `browser_open_tab`: Open a new browser tab/session.
- `browser_list_tabs`: List all currently open browser WebView tabs/sessions, including their session IDs, current URLs, page titles, and active tab status.
- `browser_select_tab`: Switch the active browser tab in the IDE to the given session_id.
- `browser_navigate`: Navigate active/specified tab to a URL.
- `browser_execute_js`: Run JavaScript in the page context and return the result.
- `browser_click`: Click an element matching a CSS selector or coordinates.
- `browser_type`: Type text into an input field matching a CSS selector.
- `browser_cdp_command`: Run a raw Chrome DevTools Protocol command.
- `browser_screenshot`: Capture a visual screenshot of the rendered page.
- `browser_get_current_url`: Retrieve the current URL and page title.
- `browser_close_tab`: Close a browser session/tab.

## Usage Guidelines:
- Specify `session_id` to target a specific browser tab if multiple tabs are open.
- All browser and terminal tool responses include `session_id` to track active sessions across tabs.
- Use `browser_list_tabs` to inspect all currently open browser tabs and session IDs.
- Use `browser_select_tab` to change focus to a specific tab by its session ID.
- If `session_id` is omitted, operations target the currently active browser tab.
- When inspecting single page applications (SPAs) or dynamically updated pages, use `browser_execute_js` to verify DOM state.
