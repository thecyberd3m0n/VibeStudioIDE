package com.vibestudio.app.mcp;

import android.content.Context;

import com.vibestudio.app.browser.BrowserManager;
import com.vibestudio.app.mcp.model.McpTool;
import com.vibestudio.app.mcp.model.McpToolResult;
import com.vibestudio.app.mcp.model.PropertyType;
import com.vibestudio.app.service.LogViewerService;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class BrowserMcpServer implements McpServer {

    private static final String TAG = "BrowserMcpServer";
    private static final String NAME = "browser";
    private static final String DESCRIPTION = "Allows enabling the WebView on demand via 'browser_enable', navigating IDE webview sessions, setting user-agent, clearing cookies, inspecting active URL, clicking elements, typing text, executing JS scripts, taking screenshots, and reading browser console/navigation logs.";

    private boolean mIsActive = true;
    private final List<McpTool> mTools;

    public BrowserMcpServer() {
        mTools = new ArrayList<>();

        // Tool 0: browser_enable
        mTools.add(McpTool.builder("browser_enable", "Enable and initialize the active WebView for browser operations on demand.")
                .build());

        // Tool 1: browser_navigate
        mTools.add(McpTool.builder("browser_navigate", "Navigate an IDE browser WebView session to a target URL.")
                .addProperty("url", PropertyType.STRING, "The URL to navigate to (e.g. 'https://google.com' or 'localhost:8080').", true)
                .addProperty("session_id", PropertyType.STRING, "Optional target browser session ID.", false)
                .build());

        // Tool 2: browser_get_current_url
        mTools.add(McpTool.builder("browser_get_current_url", "Get the current URL loaded in a WebView session.")
                .addProperty("session_id", PropertyType.STRING, "Optional target browser session ID.", false)
                .build());

        // Tool 3: browser_set_user_agent
        mTools.add(McpTool.builder("browser_set_user_agent", "Set a custom User-Agent string for a WebView session.")
                .addProperty("user_agent", PropertyType.STRING, "Custom User-Agent string.", true)
                .addProperty("session_id", PropertyType.STRING, "Optional target browser session ID.", false)
                .build());

        // Tool 4: browser_reset_cookies
        mTools.add(McpTool.builder("browser_reset_cookies", "Reset/clear all browser cookies in the WebView CookieManager.")
                .build());

        // Tool 5: browser_execute_js
        mTools.add(McpTool.builder("browser_execute_js", "Execute dynamic JavaScript code in the context of a WebView page session.")
                .addProperty("script", PropertyType.STRING, "JavaScript code to evaluate in the web page.", true)
                .addProperty("session_id", PropertyType.STRING, "Optional target browser session ID.", false)
                .build());

        // Tool 6: browser_click
        mTools.add(McpTool.builder("browser_click", "Click an element in a web page session using a CSS selector.")
                .addProperty("selector", PropertyType.STRING, "CSS selector of the element to click.", true)
                .addProperty("session_id", PropertyType.STRING, "Optional target browser session ID.", false)
                .build());

        // Tool 7: browser_type
        mTools.add(McpTool.builder("browser_type", "Type text into an input or textarea element matched by a CSS selector.")
                .addProperty("selector", PropertyType.STRING, "CSS selector of the input field.", true)
                .addProperty("text", PropertyType.STRING, "Text to type into the targeted input field.", true)
                .addProperty("session_id", PropertyType.STRING, "Optional target browser session ID.", false)
                .build());

        // Tool 8: browser_screenshot
        mTools.add(McpTool.builder("browser_screenshot", "Capture a screenshot of a WebView session rendering.")
                .addProperty("session_id", PropertyType.STRING, "Optional target browser session ID.", false)
                .build());

        // Tool 9: browser_read_logs
        mTools.add(McpTool.builder("browser_read_logs", "Read and filter browser console messages and lifecycle events.")
                .addProperty("max_lines", PropertyType.INTEGER, "Maximum number of browser log lines to return (default: 50).", false)
                .addProperty("grep_pattern", PropertyType.STRING, "Optional pattern/text to filter browser logs.", false)
                .addProperty("level", PropertyType.STRING, "Optional log level filter (e.g. 'ERROR', 'WARNING', 'LOG', 'INFO', 'DEBUG').", false)
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
        if ("read_browser_logs".equals(toolName)) return true;
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
            String sessionId = arguments != null ? getStringArg(arguments, "session_id", null) : null;

            if ("browser_enable".equals(toolName) || "enable".equals(toolName)) {
                if (context != null) {
                    browserManager.ensureInitialized(context);
                }
                if (browserManager.isWebViewAvailable()) {
                    return McpToolResult.success("WebView enabled and initialized successfully.");
                } else {
                    return McpToolResult.error("Failed to enable WebView. Context is missing or initialization failed.");
                }
            }

            if ("browser_reset_cookies".equals(toolName)) {
                String resStr = browserManager.resetCookies();
                return McpToolResult.success(resStr);
            }

            if ("browser_read_logs".equals(toolName) || "read_browser_logs".equals(toolName)) {
                int maxLines = arguments != null ? getIntArg(arguments, "max_lines", 50) : 50;
                String grepPattern = arguments != null ? getStringArg(arguments, "grep_pattern", null) : null;
                String levelFilter = arguments != null ? getStringArg(arguments, "level", null) : null;
                String logs = browserManager.getBrowserLogs(maxLines, grepPattern, levelFilter);
                
                Map<String, Object> extras = new HashMap<>();
                extras.put("logs", logs);
                return McpToolResult.success(extras);
            }

            if (!browserManager.isWebViewAvailable(sessionId)) {
                if (context != null) {
                    browserManager.ensureInitialized(context);
                }
            }

            if (!browserManager.isWebViewAvailable(sessionId)) {
                return McpToolResult.error("WebView is not available or enabled. Call 'browser_enable' to turn on the WebView first.");
            }

            if ("browser_navigate".equals(toolName)) {
                String url = arguments != null ? getStringArg(arguments, "url", "") : "";
                String resStr = (sessionId != null && !sessionId.isEmpty())
                        ? browserManager.navigate(sessionId, url)
                        : browserManager.navigate(url);
                return McpToolResult.success(resStr);

            } else if ("browser_get_current_url".equals(toolName)) {
                String currentUrl = (sessionId != null && !sessionId.isEmpty())
                        ? browserManager.getCurrentUrl(sessionId)
                        : browserManager.getCurrentUrl();
                Map<String, Object> extras = new HashMap<>();
                extras.put("url", currentUrl);
                return McpToolResult.success(extras);

            } else if ("browser_set_user_agent".equals(toolName)) {
                String userAgent = arguments != null ? getStringArg(arguments, "user_agent", "") : "";
                String resStr = (sessionId != null && !sessionId.isEmpty())
                        ? browserManager.setUserAgent(sessionId, userAgent)
                        : browserManager.setUserAgent(userAgent);
                return McpToolResult.success(resStr);

            } else if ("browser_execute_js".equals(toolName)) {
                String script = arguments != null ? getStringArg(arguments, "script", "") : "";
                String resStr = (sessionId != null && !sessionId.isEmpty())
                        ? browserManager.executeJs(sessionId, script)
                        : browserManager.executeJs(script);
                Map<String, Object> extras = new HashMap<>();
                extras.put("result", resStr);
                return McpToolResult.success(extras);

            } else if ("browser_click".equals(toolName)) {
                String selector = arguments != null ? getStringArg(arguments, "selector", "") : "";
                String resStr = (sessionId != null && !sessionId.isEmpty())
                        ? browserManager.click(sessionId, selector)
                        : browserManager.click(selector);
                return McpToolResult.success(resStr);

            } else if ("browser_type".equals(toolName)) {
                String selector = arguments != null ? getStringArg(arguments, "selector", "") : "";
                String text = arguments != null ? getStringArg(arguments, "text", "") : "";
                String resStr = (sessionId != null && !sessionId.isEmpty())
                        ? browserManager.type(sessionId, selector, text)
                        : browserManager.type(selector, text);
                return McpToolResult.success(resStr);

            } else if ("browser_screenshot".equals(toolName)) {
                JSONObject jsonRes = (sessionId != null && !sessionId.isEmpty())
                        ? browserManager.takeScreenshot(sessionId, context)
                        : browserManager.takeScreenshot(context);
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
