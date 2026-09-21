package com.vibestudio.app.mcp;

import android.content.Context;

import com.vibestudio.app.browser.BrowserManager;
import com.vibestudio.app.service.LogViewerService;

import org.json.JSONArray;
import org.json.JSONObject;

public class BrowserMcpServer {

    private static final String TAG = "BrowserMcpServer";
    private boolean mIsActive = true;

    public BrowserMcpServer() {}

    public void startServer() {
        mIsActive = true;
        LogViewerService.getInstance().i(TAG, "Browser MCP Server started.");
    }

    public void stopServer() {
        mIsActive = false;
        LogViewerService.getInstance().i(TAG, "Browser MCP Server stopped.");
    }

    public boolean isActive() {
        return mIsActive;
    }

    public JSONArray getToolsListSchema() {
        JSONArray tools = new JSONArray();
        try {
            // Tool 1: browser_navigate
            JSONObject navTool = new JSONObject();
            navTool.put("name", "browser_navigate");
            navTool.put("description", "Navigate the active IDE browser WebView to a target URL.");
            JSONObject navParams = new JSONObject();
            navParams.put("type", "object");
            JSONObject navProps = new JSONObject();
            JSONObject urlProp = new JSONObject();
            urlProp.put("type", "string");
            urlProp.put("description", "The URL to navigate to (e.g. 'https://google.com' or 'localhost:8080').");
            navProps.put("url", urlProp);
            navParams.put("properties", navProps);
            JSONArray navReq = new JSONArray();
            navReq.put("url");
            navParams.put("required", navReq);
            navTool.put("inputSchema", navParams);
            tools.put(navTool);

            // Tool 2: browser_get_current_url
            JSONObject urlTool = new JSONObject();
            urlTool.put("name", "browser_get_current_url");
            urlTool.put("description", "Get the current URL loaded in the active WebView.");
            JSONObject urlParams = new JSONObject();
            urlParams.put("type", "object");
            urlTool.put("inputSchema", urlParams);
            tools.put(urlTool);

            // Tool 3: browser_set_user_agent
            JSONObject uaTool = new JSONObject();
            uaTool.put("name", "browser_set_user_agent");
            uaTool.put("description", "Set a custom User-Agent string for the WebView.");
            JSONObject uaParams = new JSONObject();
            uaParams.put("type", "object");
            JSONObject uaProps = new JSONObject();
            JSONObject uaStrProp = new JSONObject();
            uaStrProp.put("type", "string");
            uaStrProp.put("description", "Custom User-Agent string.");
            uaProps.put("user_agent", uaStrProp);
            uaParams.put("properties", uaProps);
            JSONArray uaReq = new JSONArray();
            uaReq.put("user_agent");
            uaParams.put("required", uaReq);
            uaTool.put("inputSchema", uaParams);
            tools.put(uaTool);

            // Tool 4: browser_reset_cookies
            JSONObject cookieTool = new JSONObject();
            cookieTool.put("name", "browser_reset_cookies");
            cookieTool.put("description", "Reset/clear all browser cookies in the WebView CookieManager.");
            JSONObject cookieParams = new JSONObject();
            cookieParams.put("type", "object");
            cookieTool.put("inputSchema", cookieParams);
            tools.put(cookieTool);

            // Tool 5: browser_execute_js
            JSONObject jsTool = new JSONObject();
            jsTool.put("name", "browser_execute_js");
            jsTool.put("description", "Execute dynamic JavaScript code in the context of the active WebView page.");
            JSONObject jsParams = new JSONObject();
            jsParams.put("type", "object");
            JSONObject jsProps = new JSONObject();
            JSONObject scriptProp = new JSONObject();
            scriptProp.put("type", "string");
            scriptProp.put("description", "JavaScript code to evaluate in the web page.");
            jsProps.put("script", scriptProp);
            jsParams.put("properties", jsProps);
            JSONArray jsReq = new JSONArray();
            jsReq.put("script");
            jsParams.put("required", jsReq);
            jsTool.put("inputSchema", jsParams);
            tools.put(jsTool);

            // Tool 6: browser_click
            JSONObject clickTool = new JSONObject();
            clickTool.put("name", "browser_click");
            clickTool.put("description", "Click an element in the active web page using a CSS selector.");
            JSONObject clickParams = new JSONObject();
            clickParams.put("type", "object");
            JSONObject clickProps = new JSONObject();
            JSONObject selProp = new JSONObject();
            selProp.put("type", "string");
            selProp.put("description", "CSS selector of the element to click.");
            clickProps.put("selector", selProp);
            clickParams.put("properties", clickProps);
            JSONArray clickReq = new JSONArray();
            clickReq.put("selector");
            clickParams.put("required", clickReq);
            clickTool.put("inputSchema", clickParams);
            tools.put(clickTool);

            // Tool 7: browser_keyboard_click
            JSONObject keyTool = new JSONObject();
            keyTool.put("name", "browser_keyboard_click");
            keyTool.put("description", "Type text into an input or textarea element matched by a CSS selector.");
            JSONObject keyParams = new JSONObject();
            keyParams.put("type", "object");
            JSONObject keyProps = new JSONObject();
            JSONObject keySelProp = new JSONObject();
            keySelProp.put("type", "string");
            keySelProp.put("description", "CSS selector of the input field.");
            keyProps.put("selector", keySelProp);
            JSONObject textProp = new JSONObject();
            textProp.put("type", "string");
            textProp.put("description", "Text to type into the targeted input field.");
            keyProps.put("text", textProp);
            keyParams.put("properties", keyProps);
            JSONArray keyReq = new JSONArray();
            keyReq.put("selector");
            keyReq.put("text");
            keyParams.put("required", keyReq);
            keyTool.put("inputSchema", keyParams);
            tools.put(keyTool);

            // Tool 8: browser_screenshot
            JSONObject shotTool = new JSONObject();
            shotTool.put("name", "browser_screenshot");
            shotTool.put("description", "Capture a screenshot of the current active WebView rendering.");
            JSONObject shotParams = new JSONObject();
            shotParams.put("type", "object");
            shotTool.put("inputSchema", shotParams);
            tools.put(shotTool);

            // Tool 9: browser_read_logs
            JSONObject logsTool = new JSONObject();
            logsTool.put("name", "browser_read_logs");
            logsTool.put("description", "Read and filter browser console messages and lifecycle events.");
            JSONObject logsParams = new JSONObject();
            logsParams.put("type", "object");
            JSONObject logsProps = new JSONObject();
            JSONObject maxLinesProp = new JSONObject();
            maxLinesProp.put("type", "integer");
            maxLinesProp.put("description", "Maximum number of browser log lines to return (default: 50).");
            logsProps.put("max_lines", maxLinesProp);
            JSONObject grepProp = new JSONObject();
            grepProp.put("type", "string");
            grepProp.put("description", "Optional pattern/text to filter browser logs.");
            logsProps.put("grep_pattern", grepProp);
            JSONObject levelProp = new JSONObject();
            levelProp.put("type", "string");
            levelProp.put("description", "Optional log level filter (e.g. 'ERROR', 'WARNING', 'LOG', 'INFO', 'DEBUG').");
            logsProps.put("level", levelProp);
            logsParams.put("properties", logsProps);
            logsTool.put("inputSchema", logsParams);
            tools.put(logsTool);

        } catch (Exception e) {
            LogViewerService.getInstance().e(TAG, "Error building Browser MCP tools schema", e);
        }
        return tools;
    }

    public JSONObject callTool(String toolName, JSONObject args, Context context) {
        JSONObject result = new JSONObject();
        try {
            BrowserManager browserManager = BrowserManager.getInstance();

            if ("browser_reset_cookies".equals(toolName)) {
                String resStr = browserManager.resetCookies();
                result.put("status", "success");
                result.put("output", resStr);
                return result;
            }

            if ("browser_read_logs".equals(toolName)) {
                int maxLines = args != null ? args.optInt("max_lines", 50) : 50;
                String grepPattern = args != null ? args.optString("grep_pattern", null) : null;
                String levelFilter = args != null ? args.optString("level", null) : null;
                String logs = browserManager.getBrowserLogs(maxLines, grepPattern, levelFilter);
                result.put("status", "success");
                result.put("logs", logs);
                return result;
            }

            if (!browserManager.isWebViewAvailable()) {
                result.put("status", "error");
                result.put("message", "WebView is not available or open in the app.");
                return result;
            }

            if ("browser_navigate".equals(toolName)) {
                String url = args.optString("url", "");
                String resStr = browserManager.navigate(url);
                result.put("status", "success");
                result.put("output", resStr);

            } else if ("browser_get_current_url".equals(toolName)) {
                String currentUrl = browserManager.getCurrentUrl();
                result.put("status", "success");
                result.put("url", currentUrl);

            } else if ("browser_set_user_agent".equals(toolName)) {
                String userAgent = args.optString("user_agent", "");
                String resStr = browserManager.setUserAgent(userAgent);
                result.put("status", "success");
                result.put("output", resStr);

            } else if ("browser_execute_js".equals(toolName)) {
                String script = args.optString("script", "");
                String resStr = browserManager.executeJs(script);
                result.put("status", "success");
                result.put("result", resStr);

            } else if ("browser_click".equals(toolName)) {
                String selector = args.optString("selector", "");
                String resStr = browserManager.click(selector);
                result.put("status", "success");
                result.put("output", resStr);

            } else if ("browser_keyboard_click".equals(toolName)) {
                String selector = args.optString("selector", "");
                String text = args.optString("text", "");
                String resStr = browserManager.keyboardClick(selector, text);
                result.put("status", "success");
                result.put("output", resStr);

            } else if ("browser_screenshot".equals(toolName)) {
                return browserManager.takeScreenshot(context);

            } else {
                result.put("status", "error");
                result.put("message", "Unknown browser tool: " + toolName);
            }

        } catch (Exception e) {
            LogViewerService.getInstance().e(TAG, "Error executing Browser MCP tool: " + toolName, e);
            try {
                result.put("status", "error");
                result.put("message", e.getMessage());
            } catch (Exception ignored) {}
        }
        return result;
    }
}
