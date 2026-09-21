package com.vibestudio.app.mcp;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class McpClientManager {

    public static class McpServerInfo {
        private final String name;
        private final String description;
        private final boolean isConnected;

        public McpServerInfo(String name, String description, boolean isConnected) {
            this.name = name;
            this.description = description;
            this.isConnected = isConnected;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
        public boolean isConnected() { return isConnected; }
        public String getStatus() { return isConnected ? "Status: Connected & Ready" : "Status: Disconnected"; }
        public String getColor() { return isConnected ? "#4CAF50" : "#F44336"; }
    }

    private final Map<String, Object> mRegisteredServers = new HashMap<>();

    public McpClientManager() {
        mRegisteredServers.put("terminal", new TerminalMcpServer());
        mRegisteredServers.put("browser", new BrowserMcpServer());
    }

    public List<McpServerInfo> getConfiguredServers() {
        List<McpServerInfo> list = new ArrayList<>();
        list.add(new McpServerInfo("Terminal Skill", "Shared Terminal Execution & Buffer Inspector MCP Skill", true));
        list.add(new McpServerInfo("Browser Skill", "Agentic WebView Automation & Browser Logs Inspector Skill", true));
        return list;
    }

    public JSONObject getAllAvailableToolsSchema() {
        JSONObject allTools = new JSONObject();
        try {
            JSONArray toolsArray = new JSONArray();
            for (Object server : mRegisteredServers.values()) {
                JSONArray tools = null;
                if (server instanceof TerminalMcpServer) {
                    tools = ((TerminalMcpServer) server).getToolsListSchema();
                } else if (server instanceof BrowserMcpServer) {
                    tools = ((BrowserMcpServer) server).getToolsListSchema();
                }
                if (tools != null) {
                    for (int i = 0; i < tools.length(); i++) {
                        toolsArray.put(tools.getJSONObject(i));
                    }
                }
            }
            allTools.put("tools", toolsArray);
        } catch (Exception ignored) {}
        return allTools;
    }

    public JSONObject getHighLevelCatalog() {
        JSONObject catalog = new JSONObject();
        try {
            JSONArray skills = new JSONArray();

            JSONObject terminalSkill = new JSONObject();
            terminalSkill.put("name", "terminal");
            terminalSkill.put("description", "Allows running shell commands in the active IDE terminal session and inspecting terminal logs.");
            skills.put(terminalSkill);

            JSONObject browserSkill = new JSONObject();
            browserSkill.put("name", "browser");
            browserSkill.put("description", "Allows navigating the IDE webview, setting user-agent, clearing cookies, inspecting active URL, clicking elements, typing text, executing JS scripts, taking screenshots, and reading isolated browser console/navigation logs.");
            skills.put(browserSkill);

            catalog.put("available_skills", skills);

            JSONObject getMetaTool = new JSONObject();
            getMetaTool.put("name", "get_skill_schema");
            getMetaTool.put("description", "Fetches detailed parameters and tool schemas for a given skill name when needed.");
            
            JSONObject skillArg = new JSONObject();
            skillArg.put("type", "string");
            skillArg.put("description", "Name of the skill to load (e.g. 'terminal', 'browser')");

            JSONObject properties = new JSONObject();
            properties.put("skill_name", skillArg);

            JSONObject parameters = new JSONObject();
            parameters.put("type", "object");
            parameters.put("properties", properties);
            
            JSONArray required = new JSONArray();
            required.put("skill_name");
            parameters.put("required", required);

            getMetaTool.put("parameters", parameters);

            catalog.put("meta_tool", getMetaTool);

        } catch (Exception ignored) {}
        return catalog;
    }

    public JSONObject getSkillSchema(String skillName) {
        JSONObject response = new JSONObject();
        try {
            if (skillName != null && mRegisteredServers.containsKey(skillName.toLowerCase())) {
                Object server = mRegisteredServers.get(skillName.toLowerCase());
                JSONArray tools = null;
                if (server instanceof TerminalMcpServer) {
                    tools = ((TerminalMcpServer) server).getToolsListSchema();
                } else if (server instanceof BrowserMcpServer) {
                    tools = ((BrowserMcpServer) server).getToolsListSchema();
                }
                if (tools != null) {
                    response.put("status", "success");
                    response.put("skill", skillName.toLowerCase());
                    JSONObject schemaObj = new JSONObject();
                    schemaObj.put("tools", tools);
                    response.put("schema", schemaObj);
                    return response;
                }
            }
            response.put("status", "error");
            response.put("message", "Unknown skill name: " + skillName);
        } catch (Exception e) {
            try {
                response.put("status", "error");
                response.put("message", e.getMessage());
            } catch (Exception ignored) {}
        }
        return response;
    }

    public JSONObject executeToolCall(String toolName, JSONObject args, Context context) {
        JSONObject result = new JSONObject();
        try {
            if ("get_skill_schema".equalsIgnoreCase(toolName)) {
                String skillName = args != null ? args.optString("skill_name") : "";
                return getSkillSchema(skillName);
            }

            for (Object server : mRegisteredServers.values()) {
                if (server instanceof TerminalMcpServer) {
                    TerminalMcpServer terminalServer = (TerminalMcpServer) server;
                    if ("execute_command".equals(toolName) || "read_terminal_output".equals(toolName)) {
                        return terminalServer.callTool(toolName, args);
                    }
                } else if (server instanceof BrowserMcpServer) {
                    BrowserMcpServer browserServer = (BrowserMcpServer) server;
                    if (toolName.startsWith("browser_") || "read_browser_logs".equals(toolName)) {
                        return browserServer.callTool(toolName, args, context);
                    }
                }
            }

            result.put("status", "error");
            result.put("message", "Tool execution failed: Unknown tool '" + toolName + "'. Lazy load skill schema via 'get_skill_schema' if not loaded.");

        } catch (Exception e) {
            try {
                result.put("status", "error");
                result.put("message", "Exception executing tool: " + e.getMessage());
            } catch (Exception ignored) {}
        }
        return result;
    }

    public JSONObject executeToolCall(String toolName, JSONObject args) {
        return executeToolCall(toolName, args, null);
    }
}
