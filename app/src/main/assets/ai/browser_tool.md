# Browser Tool Guidelines

The `browser` skill provides control over the integrated WebView for running, testing, and debugging web applications (such as local Angular, React, Vue dev servers, or static pages).

## Browser Tool Usage & Local Web App Rules
- **WebView Initialization**: Call `browser_enable` first if the WebView is not already active.
- **Local Dev Server Support**: The WebView allows cleartext HTTP (e.g. `http://localhost:4200` or `http://127.0.0.1:8080`) and mixed content.
- **Executing JavaScript & Interactions**: Use `browser_execute_js`, `browser_click`, and `browser_type` to interact with dynamic web elements.
- **Monitoring & Screenshots**: Use `browser_screenshot` to verify visual rendering and `browser_read_logs` to capture browser console output and errors.
