# Skill: browser

The `browser` skill provides full control over VibeStudio's integrated WebView for running, viewing, testing, debugging, and automating web pages and web applications (such as Google, local Angular, React, Vue dev servers, or static HTML forms).

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

## Browser Tool Rules & Best Practices
1. **Prefer Native CDP Automation**: ALWAYS PREFER `browser_cdp_command` (Chrome DevTools Protocol) over `browser_execute_js` or JS script injection for browser operations, DOM/Network manipulation, element interaction, and file attachments. Native CDP connects directly via UNIX domain socket (`@webview_devtools_remote_<PID>`) bypassing Content Security Policy (`unsafe-eval` restrictions), DOM isolation, and browser sandbox limitations.
2. **Tab Session Management**:
   - Specify `session_id` to target a specific browser tab if multiple tabs are open.
   - All browser and terminal tool responses include `session_id` to track active sessions across tabs.
   - Use `browser_list_tabs` to inspect all currently open browser tabs and session IDs.
   - Use `browser_select_tab` to change focus to a specific tab by its session ID.
   - If `session_id` is omitted, operations target the currently active browser tab.
3. **WebView Initialization**: A dedicated browser tab is automatically opened for you the first time you execute a browser command (like `browser_navigate`). Do NOT explicitly open tabs unless you specifically need to view multiple pages simultaneously.
4. **Local Dev Server & Public Web Support**: The WebView supports cleartext HTTP (e.g., `http://localhost:4200`) as well as secure HTTPS sites (e.g., `https://google.com`).
5. **Verifying Page Load & Content**: After navigating with `browser_navigate`, use `browser_cdp_command` or `browser_execute_js` (e.g., `document.title` or `document.body.innerText.substring(0, 500)`) or `browser_screenshot` to verify visual rendering and read page contents directly.
6. **Executing JavaScript & Interactions**: Use `browser_cdp_command` for native element interactions, file uploads, and DOM manipulation. Fall back to `browser_execute_js`, `browser_click`, and `browser_type` only for basic lightweight evaluations when CDP is not required.
7. **Monitoring, Logs & Network Inspection**: Use `browser_screenshot` to verify visual rendering and `browser_read_logs` to capture browser console output. Full Network tracking and HTTP traffic inspection are also available via standard CDP domains (`Network.enable`, `Network.getResponseBody`).
8. **Chrome DevTools Protocol (CDP) Standard**:
   - VibeStudio enables WebView debugging (`setWebContentsDebuggingEnabled(true)`) and exposes raw CDP execution via `browser_cdp_command`.
   - You can issue ANY Chrome DevTools Protocol method (e.g. `DOM.setFileInputFiles`, `Page.reload`, `Page.navigate`, `Input.dispatchMouseEvent`, `Input.dispatchTouchEvent`, `Network.enable`, etc.) as the primary standard for browser automation.
