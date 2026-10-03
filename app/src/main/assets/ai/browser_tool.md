# Browser Tool Guidelines

The `browser` skill provides full control over VibeStudio's integrated WebView for running, viewing, testing, and debugging web pages and web applications (such as Google, local Angular, React, Vue dev servers, or static HTML pages).

## Browser Tool Rules & Best Practices
1. **Prefer Integrated Browser Tooling**: ALWAYS use the `browser` skill (e.g., `browser_navigate`, `browser_get_current_url`, `browser_execute_js`, `browser_screenshot`, `browser_read_logs`) when tasked with viewing, checking, navigating, or interacting with websites or local web apps. Do NOT fallback to CLI tools like `curl`, `lynx`, or `wget` unless explicitly requested by the user.
2. **WebView Initialization**: A dedicated browser tab is automatically opened for you the first time you execute a browser command (like `browser_navigate`). Do NOT explicitly open tabs unless you specifically need to view multiple pages simultaneously.
3. **Local Dev Server & Public Web Support**: The WebView supports cleartext HTTP (e.g., `http://localhost:4200`) as well as secure HTTPS sites (e.g., `https://google.com`).
4. **Verifying Page Load & Content**: After navigating with `browser_navigate`, use `browser_execute_js` with scripts like `document.title` or `document.body.innerText.substring(0, 500)` or `browser_screenshot` to verify visual rendering and read page contents directly.
5. **Executing JavaScript & Interactions**: Use `browser_execute_js`, `browser_click`, and `browser_type` to interact with dynamic web elements.
6. **Monitoring & Logs**: Use `browser_screenshot` to verify visual rendering and `browser_read_logs` to capture browser console output and lifecycle events.
