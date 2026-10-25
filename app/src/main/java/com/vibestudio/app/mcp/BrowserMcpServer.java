package com.vibestudio.app.mcp;

import android.content.Context;
import android.webkit.WebView;

import com.vibestudio.app.browser.BrowserManager;
import com.vibestudio.app.chat.safety.WorkspaceChangesetTracker;
import com.vibestudio.app.fragments.BrowserFragment;
import com.vibestudio.app.mcp.model.McpTool;
import com.vibestudio.app.mcp.model.McpToolResult;
import com.vibestudio.app.mcp.model.PropertyType;
import com.vibestudio.app.service.LogViewerService;
import com.vibestudio.app.tab.TabItem;
import com.vibestudio.app.tab.TabManager;
import com.vibestudio.app.view.BaseToolMessageView;
import com.vibestudio.app.view.BrowserLogsToolMessage;
import com.vibestudio.app.view.BrowserToolMessage;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class BrowserMcpServer implements McpServer {

    @Override
    public int getPriority() {
        return 2;
    }

    @Override
    public CharSequence prepareToolCall(String toolName, JSONObject args, Context context, WorkspaceChangesetTracker tracker) throws Exception {
        JSONObject toolArgs = args != null ? args : new JSONObject();
        if ("read_browser_logs".equalsIgnoreCase(toolName) || "browser_logs".equalsIgnoreCase(toolName)) {
            return "Reading browser logs (" + toolArgs.optInt("max_lines", 30) + " lines)";
        } else if ("form_file_attach".equalsIgnoreCase(toolName) || "browser_form_file_attach".equalsIgnoreCase(toolName) || "attach_file".equalsIgnoreCase(toolName)) {
            String path = toolArgs.optString("file_path", toolArgs.optString("path", "file"));
            return "Attaching file: " + path;
        } else if ("browser_list_tabs".equalsIgnoreCase(toolName) || "browser_list_sessions".equalsIgnoreCase(toolName)) {
            return "Listing open browser tabs";
        } else if ("browser_select_tab".equalsIgnoreCase(toolName)) {
            return "Switching active browser tab to session #" + toolArgs.optInt("session_id", -1);
        }
        return "Browser Tool [" + toolName + "]: " + toolArgs.toString();
    }

    @Override
    public BaseToolMessageView createToolMessageView(Context context, String toolName) {
        if ("read_browser_logs".equalsIgnoreCase(toolName) || "browser_logs".equalsIgnoreCase(toolName)) {
            return new BrowserLogsToolMessage(context);
        }
        return new BrowserToolMessage(context);
    }

    private static final String TAG = "BrowserMcpServer";
    private static final String NAME = "browser";
    private static final String DESCRIPTION = "Allows opening browser tabs via 'browser_open_tab', listing tabs via 'browser_list_tabs', switching active tab via 'browser_select_tab', navigating webview sessions, setting user-agent, clearing cookies, inspecting current URL/title, clicking elements, typing text, executing JS scripts, taking screenshots, and reading browser console/navigation logs.";

    private boolean mIsActive = true;
    private final List<McpTool> mTools;

    public BrowserMcpServer() {
        mTools = new ArrayList<>();

        // Tool 0: browser_open_tab
        mTools.add(McpTool.builder("browser_open_tab", "Explicitly request opening a new isolated browser session/tab.")
                .build());

        // Tool 1: browser_navigate
        mTools.add(McpTool.builder("browser_navigate", "Navigate an IDE browser WebView session to a target URL.")
                .addProperty("url", PropertyType.STRING, "The URL to navigate to (e.g. 'https://google.com' or 'localhost:8080').", true)
                .addProperty("session_id", PropertyType.INTEGER, "Optional target browser session ID (integer, e.g. 1).", false)
                .build());

        // Tool 2: browser_get_current_url
        mTools.add(McpTool.builder("browser_get_current_url", "Get the current URL loaded in a WebView session.")
                .addProperty("session_id", PropertyType.INTEGER, "Optional target browser session ID (integer, e.g. 1).", false)
                .build());

        // Tool 3: browser_set_user_agent
        mTools.add(McpTool.builder("browser_set_user_agent", "Set a custom User-Agent string for a WebView session.")
                .addProperty("user_agent", PropertyType.STRING, "Custom User-Agent string.", true)
                .addProperty("session_id", PropertyType.INTEGER, "Optional target browser session ID (integer, e.g. 1).", false)
                .build());

        // Tool 4: browser_reset_cookies
        mTools.add(McpTool.builder("browser_reset_cookies", "Reset/clear all browser cookies in the WebView CookieManager.")
                .build());

        // Tool 5: browser_execute_js
        mTools.add(McpTool.builder("browser_execute_js", "Execute dynamic JavaScript code in the context of a WebView page session.")
                .addProperty("script", PropertyType.STRING, "JavaScript code to evaluate in the web page.", true)
                .addProperty("session_id", PropertyType.INTEGER, "Optional target browser session ID (integer, e.g. 1).", false)
                .build());

        // Tool 6: browser_click
        mTools.add(McpTool.builder("browser_click", "Click an element in a web page session using a CSS selector.")
                .addProperty("selector", PropertyType.STRING, "CSS selector of the element to click.", true)
                .addProperty("session_id", PropertyType.INTEGER, "Optional target browser session ID (integer, e.g. 1).", false)
                .build());

        // Tool 7: browser_type
        mTools.add(McpTool.builder("browser_type", "Type text into an input or textarea element matched by a CSS selector.")
                .addProperty("selector", PropertyType.STRING, "CSS selector of the input field.", true)
                .addProperty("text", PropertyType.STRING, "Text to type into the targeted input field.", true)
                .addProperty("session_id", PropertyType.INTEGER, "Optional target browser session ID (integer, e.g. 1).", false)
                .build());

        // Tool 8: browser_screenshot
        mTools.add(McpTool.builder("browser_screenshot", "Capture a screenshot of a WebView session rendering.")
                .addProperty("session_id", PropertyType.INTEGER, "Optional target browser session ID (integer, e.g. 1).", false)
                .build());

        // Tool 9: browser_read_logs
        mTools.add(McpTool.builder("browser_read_logs", "Read and filter browser console messages and lifecycle events.")
                .addProperty("max_lines", PropertyType.INTEGER, "Maximum number of browser log lines to return (default: 50).", false)
                .addProperty("grep_pattern", PropertyType.STRING, "Optional pattern/text to filter browser logs.", false)
                .addProperty("level", PropertyType.STRING, "Optional log level filter (e.g. 'ERROR', 'WARNING', 'LOG', 'INFO', 'DEBUG').", false)
                .build());

        // Tool 10: browser_cdp_command
        mTools.add(McpTool.builder("browser_cdp_command", "Execute raw Chrome DevTools Protocol (CDP) commands (e.g. DOM.setFileInputFiles, Page.navigate, Page.reload, Input, Network, DOM, Emulation, etc.) directly against the active WebView.")
                .addProperty("method", PropertyType.STRING, "The CDP domain method name (e.g. 'DOM.setFileInputFiles', 'Page.reload', 'DOM.getDocument').", true)
                .addProperty("params", PropertyType.STRING, "JSON string containing parameter key-values for the CDP command.", false)
                .addProperty("session_id", PropertyType.INTEGER, "Optional target browser session ID (integer, e.g. 1).", false)
                .build());

        // Tool 11: browser_list_tabs
        mTools.add(McpTool.builder("browser_list_tabs", "List all currently open browser WebView tabs/sessions, including their session IDs, current URLs, page titles, and which tab is currently active.")
                .build());

        // Tool 12: browser_select_tab
        mTools.add(McpTool.builder("browser_select_tab", "Switch the active browser tab in the IDE to the given session_id.")
                .addProperty("session_id", PropertyType.INTEGER, "The target browser session ID to select as active.", true)
                .build());
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Override
    public void startServer() {
        mIsActive = true;
        LogViewerService.getInstance().i(TAG, "Browser MCP Server started.");
    }

    @Override
    public void stopServer() {
        mIsActive = false;
        LogViewerService.getInstance().i(TAG, "Browser MCP Server stopped.");
    }

    @Override
    public boolean isActive() {
        return mIsActive;
    }

    @Override
    public List<McpTool> getTools() {
        return mTools;
    }

    @Override
    public boolean handlesTool(String toolName) {
        if (toolName == null) return false;
        if ("read_browser_logs".equals(toolName) || "form_file_attach".equals(toolName) || "browser_form_file_attach".equals(toolName) || "form_file_upload".equals(toolName) || "attach_file".equals(toolName) || "browser_attach_file".equals(toolName) || "browser_list_sessions".equals(toolName) || "list_tabs".equals(toolName) || "select_tab".equals(toolName)) return true;
        for (McpTool tool : mTools) {
            if (tool.getName().equals(toolName)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public McpToolResult callTool(String toolName, Map<String, Object> arguments, Context context) {
        try {
            BrowserManager browserManager = BrowserManager.getInstance();
            ToolSessionManager sessionManager = ToolSessionManager.getInstance();

            if ("browser_open_tab".equals(toolName) || "open_tab".equals(toolName)) {
                int newSessionId = sessionManager.getOrCreateSession(ToolType.BROWSER, context, true);
                Map<String, Object> extras = new HashMap<>();
                extras.put("session_id", newSessionId);
                extras.put("message", "Opened new browser session #" + newSessionId + " in taskbar.");
                return McpToolResult.success(extras);
            }

            if ("browser_list_tabs".equals(toolName) || "browser_list_sessions".equals(toolName) || "list_tabs".equals(toolName)) {
                List<ToolSessionManager.ToolSession> sessions = sessionManager.getSessionsForType(ToolType.BROWSER);
                int activeSessionId = sessionManager.getActiveSessionId(ToolType.BROWSER);

                List<Map<String, Object>> sessionList = new ArrayList<>();
                for (ToolSessionManager.ToolSession s : sessions) {
                    WebView wv = s.getWebView();
                    if (wv != null) {
                        Map<String, Object> item = new HashMap<>();
                        item.put("session_id", s.id);
                        item.put("url", browserManager.getCurrentUrl(wv));
                        item.put("title", browserManager.getCurrentTitle(wv));
                        item.put("is_active", s.id == activeSessionId);
                        sessionList.add(item);
                    }
                }

                Map<String, Object> extras = new HashMap<>();
                extras.put("sessions", sessionList);
                return McpToolResult.success(extras);
            }

            if ("browser_select_tab".equals(toolName) || "select_tab".equals(toolName)) {
                int targetSessionId = arguments != null ? getIntArg(arguments, "session_id", -1) : -1;
                if (targetSessionId <= 0) {
                    return McpToolResult.error("Missing required argument: 'session_id' (integer).");
                }
                ToolSessionManager.SessionValidationResult validation = sessionManager.validateSession(targetSessionId, ToolType.BROWSER);
                if (!validation.isValid()) {
                    return McpToolResult.error(validation.errorMessage);
                }

                sessionManager.setActiveSession(ToolType.BROWSER, targetSessionId);

                WebView targetWebView = validation.session.getWebView();
                if (targetWebView != null) {
                    for (TabItem tab : TabManager.getInstance().getTabs()) {
                        if (tab.getFragment() instanceof BrowserFragment) {
                            BrowserFragment bf = (BrowserFragment) tab.getFragment();
                            if (bf.getWebView() == targetWebView) {
                                TabManager.getInstance().selectTab(tab);
                                break;
                            }
                        }
                    }
                }

                Map<String, Object> extras = new HashMap<>();
                extras.put("session_id", targetSessionId);
                extras.put("message", "Switched active browser tab to session #" + targetSessionId + ".");
                return McpToolResult.success(extras);
            }

            if ("browser_reset_cookies".equals(toolName)) {
                String resStr = browserManager.resetCookies();
                Map<String, Object> extras = new HashMap<>();
                int activeSessionId = sessionManager.getActiveSessionId(ToolType.BROWSER);
                if (activeSessionId > 0) {
                    extras.put("session_id", activeSessionId);
                }
                return McpToolResult.success(resStr, extras);
            }

            if ("browser_read_logs".equals(toolName) || "read_browser_logs".equals(toolName)) {
                int maxLines = arguments != null ? getIntArg(arguments, "max_lines", 50) : 50;
                String grepPattern = arguments != null ? getStringArg(arguments, "grep_pattern", null) : null;
                String levelFilter = arguments != null ? getStringArg(arguments, "level", null) : null;
                String logs = browserManager.getBrowserLogs(maxLines, grepPattern, levelFilter);
                
                Map<String, Object> extras = new HashMap<>();
                extras.put("logs", logs);
                int activeSessionId = sessionManager.getActiveSessionId(ToolType.BROWSER);
                if (activeSessionId > 0) {
                    extras.put("session_id", activeSessionId);
                }
                return McpToolResult.success(extras);
            }

            int sessionIdArg = arguments != null ? getIntArg(arguments, "session_id", -1) : -1;
            ToolSessionManager.ToolSession session;

            if (sessionIdArg > 0) {
                ToolSessionManager.SessionValidationResult validation = sessionManager.validateSession(sessionIdArg, ToolType.BROWSER);
                if (!validation.isValid()) {
                    return McpToolResult.error(validation.errorMessage);
                }
                session = validation.session;
            } else {
                int activeSessionId = sessionManager.getOrCreateSession(ToolType.BROWSER, context, false);
                session = sessionManager.getSession(activeSessionId);
                if (session == null) {
                    return McpToolResult.error("Failed to initialize active browser session.");
                }
            }

            WebView webView = session.getWebView();
            if (webView == null) {
                return McpToolResult.error("WebView handle is null or destroyed for session #" + session.id);
            }

            if ("browser_navigate".equals(toolName)) {
                String url = arguments != null ? getStringArg(arguments, "url", "") : "";
                String resStr = browserManager.navigate(webView, url);
                Map<String, Object> extras = new HashMap<>();
                extras.put("session_id", session.id);
                return McpToolResult.success(resStr, extras);

            } else if ("browser_get_current_url".equals(toolName)) {
                String currentUrl = browserManager.getCurrentUrl(webView);
                String currentTitle = browserManager.getCurrentTitle(webView);
                Map<String, Object> extras = new HashMap<>();
                extras.put("session_id", session.id);
                extras.put("url", currentUrl);
                extras.put("title", currentTitle);
                return McpToolResult.success(extras);

            } else if ("browser_set_user_agent".equals(toolName)) {
                String userAgent = arguments != null ? getStringArg(arguments, "user_agent", "") : "";
                String resStr = browserManager.setUserAgent(webView, userAgent);
                Map<String, Object> extras = new HashMap<>();
                extras.put("session_id", session.id);
                return McpToolResult.success(resStr, extras);

            } else if ("browser_execute_js".equals(toolName)) {
                String script = arguments != null ? getStringArg(arguments, "script", "") : "";
                String resStr = browserManager.executeJs(webView, script);
                Map<String, Object> extras = new HashMap<>();
                extras.put("session_id", session.id);
                extras.put("result", resStr);
                return McpToolResult.success(extras);

            } else if ("browser_click".equals(toolName)) {
                String selector = arguments != null ? getStringArg(arguments, "selector", "") : "";
                String resStr = browserManager.click(webView, selector);
                Map<String, Object> extras = new HashMap<>();
                extras.put("session_id", session.id);
                return McpToolResult.success(resStr, extras);

            } else if ("browser_type".equals(toolName)) {
                String selector = arguments != null ? getStringArg(arguments, "selector", "") : "";
                String text = arguments != null ? getStringArg(arguments, "text", "") : "";
                String resStr = browserManager.type(webView, selector, text);
                Map<String, Object> extras = new HashMap<>();
                extras.put("session_id", session.id);
                return McpToolResult.success(resStr, extras);

            } else if ("browser_cdp_command".equals(toolName) || "browser_cdp".equals(toolName) || "browser_attach_file_cdp".equals(toolName)) {
                String method = arguments != null ? getStringArg(arguments, "method", "DOM.setFileInputFiles") : "DOM.setFileInputFiles";
                String paramsStr = arguments != null ? getStringArg(arguments, "params", "{}") : "{}";
                String resStr = browserManager.executeCdpCommand(webView, method, paramsStr);
                Map<String, Object> extras = new HashMap<>();
                extras.put("session_id", session.id);
                return McpToolResult.success(resStr, extras);

            } else if ("form_file_attach".equals(toolName) || "browser_form_file_attach".equals(toolName) || "form_file_upload".equals(toolName) || "attach_file".equals(toolName) || "browser_attach_file".equals(toolName)) {
                String filePath = arguments != null ? getStringArg(arguments, "file_path", getStringArg(arguments, "path", "")) : "";
                String selector = arguments != null ? getStringArg(arguments, "selector", "input[type='file']") : "input[type='file']";

                String resStr = browserManager.attachFile(context, webView, selector, filePath);
                Map<String, Object> extras = new HashMap<>();
                extras.put("session_id", session.id);
                return McpToolResult.success(resStr, extras);

            } else if ("browser_screenshot".equals(toolName)) {
                JSONObject jsonRes = browserManager.takeScreenshot(webView, context);
                if (jsonRes != null) {
                    jsonRes.put("session_id", session.id);
                }
                return jsonToToolResult(jsonRes);

            } else {
                return McpToolResult.error("Unknown browser tool: " + toolName);
            }

        } catch (Exception e) {
            LogViewerService.getInstance().e(TAG, "Error executing Browser MCP tool: " + toolName, e);
            return McpToolResult.error(e.getMessage());
        }
    }

    private McpToolResult jsonToToolResult(JSONObject jsonObj) {
        if (jsonObj == null) {
            return McpToolResult.error("Null response from screenshot execution.");
        }
        String status = jsonObj.optString("status", "error");
        if ("success".equals(status)) {
            Map<String, Object> extras = new HashMap<>();
            Iterator<String> keys = jsonObj.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                if (!"status".equals(k)) {
                    extras.put(k, jsonObj.opt(k));
                }
            }
            return McpToolResult.success(extras);
        } else {
            String msg = jsonObj.optString("message", "Unknown error");
            return McpToolResult.error(msg);
        }
    }

    private String getStringArg(Map<String, Object> args, String key, String defaultValue) {
        Object val = args.get(key);
        return (val != null && !"null".equalsIgnoreCase(val.toString())) ? String.valueOf(val) : defaultValue;
    }

    private int getIntArg(Map<String, Object> args, String key, int defaultValue) {
        Object val = args.get(key);
        if (val instanceof Number) {
            return ((Number) val).intValue();
        } else if (val instanceof String) {
            try {
                return Integer.parseInt((String) val);
            } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }
}
