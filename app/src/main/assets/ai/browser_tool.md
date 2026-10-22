# Browser Tool Guidelines

The `browser` skill provides full control over VibeStudio's integrated WebView for running, viewing, testing, debugging, and automating web pages and web applications (such as Google, local Angular, React, Vue dev servers, or static HTML forms).

## Browser Tool Rules & Best Practices
1. **Prefer Native CDP Automation**: ALWAYS PREFER `browser_cdp_command` (Chrome DevTools Protocol) over `browser_execute_js` or JS script injection for browser operations, DOM/Network manipulation, element interaction, and file attachments. Native CDP connects directly via UNIX domain socket (`@webview_devtools_remote_<PID>`) bypassing Content Security Policy (`unsafe-eval` restrictions), DOM isolation, and browser sandbox limitations.
2. **Native File Attachments via CDP**: To attach a file natively, use `browser_cdp_command` with method `DOM.setFileInputFiles`:
   ```json
   {
     "method": "DOM.setFileInputFiles",
     "params": "{\"selector\": \"input[type='file']\", \"files\": [\"/data/data/com.termux/files/home/sample.pdf\"]}"
   }
   ```
3. **WebView Initialization**: A dedicated browser tab is automatically opened for you the first time you execute a browser command (like `browser_navigate`). Do NOT explicitly open tabs unless you specifically need to view multiple pages simultaneously.
4. **Local Dev Server & Public Web Support**: The WebView supports cleartext HTTP (e.g., `http://localhost:4200`) as well as secure HTTPS sites (e.g., `https://google.com`).
5. **Verifying Page Load & Content**: After navigating with `browser_navigate`, use `browser_cdp_command` or `browser_execute_js` (e.g., `document.title` or `document.body.innerText.substring(0, 500)`) or `browser_screenshot` to verify visual rendering and read page contents directly.
6. **Executing JavaScript & Interactions**: Use `browser_cdp_command` for native element interactions, file uploads, and DOM manipulation. Fall back to `browser_execute_js`, `browser_click`, and `browser_type` only for basic lightweight evaluations when CDP is not required.
7. **Monitoring & Logs**: Use `browser_screenshot` to verify visual rendering and `browser_read_logs` to capture browser console output and lifecycle events.
8. **Chrome DevTools Protocol (CDP) Standard**:
   - VibeStudio enables WebView debugging (`setWebContentsDebuggingEnabled(true)`) and exposes raw CDP execution via `browser_cdp_command`.
   - You can use `browser_cdp_command` to issue ANY Chrome DevTools Protocol method (e.g. `DOM.setFileInputFiles`, `Page.reload`, `Page.navigate`, `Input.dispatchMouseEvent`, `Input.dispatchTouchEvent`, `Network.enable`, etc.) as the primary standard for browser automation.

9. **Important Notes on CDP State & File Uploads**:
   - Each `browser_cdp_command` execution opens a direct connection to Chromium DevTools. DevTools `nodeId` and `objectId` handles are session-scoped to an active DevTools WebSocket session. Do not attempt multi-step `DOM.getDocument` -> `DOM.querySelector` across separate tool calls; issue target CDP commands directly (like `DOM.setFileInputFiles`) or perform calculations in a single step.
   - When `DOM.setFileInputFiles` returns `Successfully attached files via Native CDP`, the native browser engine has already set the file list on the target element and dispatched DOM `input` and `change` events. You do not need to second-guess the engine with JS queries.
