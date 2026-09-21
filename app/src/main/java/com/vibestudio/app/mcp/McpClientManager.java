package com.vibestudio.app.mcp;

import android.content.Context;

import com.vibestudio.app.mcp.model.McpTool;
import com.vibestudio.app.mcp.model.McpToolResult;
import com.vibestudio.app.mcp.model.PropertyType;
import com.vibestudio.app.mcp.model.ToolProperty;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
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

    private final Map<String, McpServer> mRegisteredServers = new HashMap<>();

    public McpClientManager() {
        registerServer(new TerminalMcpServer());
        registerServer(new BrowserMcpServer());
    }

    public void registerServer(McpServer server) {
        if (server != null) {
            mRegisteredServers.put(server.getName().toLowerCase(), server);
            server.startServer();
        }
    }

    public List<McpServerInfo> getConfiguredServers() {
        List<McpServerInfo> list = new ArrayList<>();
        for (McpServer server : mRegisteredServers.values()) {
            String title = capitalize(server.getName()) + " Skill";
            list.add(new McpServerInfo(title, server.getDescription(), server.isActive()));
        }
        return list;
    }

    public JSONObject getAllAvailableToolsSchema() {
        JSONObject allTools = new JSONObject();
        try {
            JSONArray toolsArray = new JSONArray();
            for (McpServer server : mRegisteredServers.values()) {
                for (McpTool tool : server.getTools()) {
                    toolsArray.put(tool.toJsonSchema());
                }
            }
            allTools.put("tools", toolsArray);
        } catch (JSONException ignored) {}
        return allTools;
    }

    public JSONObject getHighLevelCatalog() {
        JSONObject catalog = new JSONObject();
        try {
            JSONArray skills = new JSONArray();

            for (McpServer server : mRegisteredServers.values()) {
                JSONObject skillObj = new JSONObject();
                skillObj.put("name", server.getName());
                skillObj.put("description", server.getDescription());
                skills.put(skillObj);
            }

            catalog.put("available_skills", skills);

            McpTool metaTool = McpTool.builder("get_skill_schema", "Fetches detailed parameters and tool schemas for a given skill name when needed.")
                    .addProperty("skill_name", PropertyType.STRING, "Name of the skill to load (e.g. 'terminal', 'browser')", true)
                    .build();

            catalog.put("meta_tool", metaTool.toJsonSchema());

        } catch (JSONException ignored) {}
        return catalog;
    }

    public JSONObject getSkillSchema(String skillName) {
        JSONObject response = new JSONObject();
        try {
            if (skillName != null && mRegisteredServers.containsKey(skillName.toLowerCase())) {
                McpServer server = mRegisteredServers.get(skillName.toLowerCase());
                if (server != null) {
                    JSONArray toolsArray = new JSONArray();
                    for (McpTool tool : server.getTools()) {
                        toolsArray.put(tool.toJsonSchema());
                    }

                    response.put("status", "success");
                    response.put("skill", skillName.toLowerCase());
                    JSONObject schemaObj = new JSONObject();
                    schemaObj.put("tools", toolsArray);
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
        try {
            if ("get_skill_schema".equalsIgnoreCase(toolName)) {
                String skillName = args != null ? args.optString("skill_name") : "";
                return getSkillSchema(skillName);
            }

            Map<String, Object> arguments = jsonObjectToMap(args);

            for (McpServer server : mRegisteredServers.values()) {
                if (server.handlesTool(toolName)) {
                    McpToolResult result = server.callTool(toolName, arguments, context);
                    return result.toJsonObject();
                }
            }

            McpToolResult errResult = McpToolResult.error("Tool execution failed: Unknown tool '" + toolName + "'. Lazy load skill schema via 'get_skill_schema' if not loaded.");
            return errResult.toJsonObject();

        } catch (Exception e) {
            return McpToolResult.error("Exception executing tool: " + e.getMessage()).toJsonObject();
        }
    }

    public JSONObject executeToolCall(String toolName, JSONObject args) {
        return executeToolCall(toolName, args, null);
    }

    private Map<String, Object> jsonObjectToMap(JSONObject jsonObj) {
        Map<String, Object> map = new HashMap<>();
        if (jsonObj == null) return map;

        Iterator<String> keys = jsonObj.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = jsonObj.opt(key);
            if (value != null && value != JSONObject.NULL) {
                map.put(key, value);
            }
        }
        return map;
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
