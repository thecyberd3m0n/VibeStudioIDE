package com.vibestudio.app.mcp;

import android.content.Context;

import com.vibestudio.app.mcp.model.McpTool;
import com.vibestudio.app.mcp.model.McpToolResult;
import com.vibestudio.app.mcp.model.PropertyType;
import com.vibestudio.app.service.LogViewerService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class McpClientManager {

    private static final String TAG = "McpClientManager";
    public static final String META_TOOL_GET_SKILL_SCHEMA = "get_skill_schema";

    public static boolean isMetaTool(String toolName) {
        return META_TOOL_GET_SKILL_SCHEMA.equalsIgnoreCase(toolName);
    }

    public static class McpServerInfo {
        private final String name;
        private final String description;
        private final boolean isConnected;
        private final int priority;

        public McpServerInfo(String name, String description, boolean isConnected, int priority) {
            this.name = name;
            this.description = description;
            this.isConnected = isConnected;
            this.priority = priority;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
        public boolean isConnected() { return isConnected; }
        public int getPriority() { return priority; }
        public String getStatus() { return isConnected ? "Status: Connected & Ready" : "Status: Disconnected"; }
        public String getColor() { return isConnected ? "#4CAF50" : "#F44336"; }
    }

    private final Map<String, McpServer> mRegisteredServers = new LinkedHashMap<>();
    private final Set<String> mLoadedSkills = new HashSet<>();

    public McpClientManager() {
        registerServer(new FileMcpServer());
        registerServer(new EditorMcpServer());
        registerServer(new TerminalMcpServer());
        registerServer(new BrowserMcpServer());
    }

    public void registerServer(McpServer server) {
        if (server != null) {
            mRegisteredServers.put(server.getName().toLowerCase(), server);
            server.startServer();
        }
    }

    public McpServer findServerForTool(String toolName) {
        if (toolName == null) return null;
        for (McpServer server : mRegisteredServers.values()) {
            if (server.handlesTool(toolName)) {
                return server;
            }
        }
        return null;
    }

    public boolean supportsPlannedDelay(String toolName) {
        if (toolName == null || isMetaTool(toolName)) {
            return false;
        }
        McpServer server = findServerForTool(toolName);
        return server != null && server.supportsPlannedDelay();
    }

    public CharSequence formatToolCall(String toolName, JSONObject args) {
        if (isMetaTool(toolName)) {
            return formatMetaToolCall(args);
        }
        McpServer server = findServerForTool(toolName);
        if (server != null) {
            CharSequence custom = server.formatToolCall(toolName, args);
            if (custom != null) {
                return custom;
            }
        }
        return null;
    }

    private CharSequence formatMetaToolCall(JSONObject args) {
        if (args == null) return "loading skill...";
        List<String> skills = new ArrayList<>();

        JSONArray skillsArray = args.optJSONArray("skills");
        if (skillsArray == null) {
            skillsArray = args.optJSONArray("skill_names");
        }

        if (skillsArray != null) {
            for (int i = 0; i < skillsArray.length(); i++) {
                String s = skillsArray.optString(i);
                if (s != null && !s.trim().isEmpty()) {
                    skills.add(s.trim());
                }
            }
        } else {
            String rawSkillName = args.optString("skill_name", args.optString("skill", args.optString("tool", "")));
            if (!rawSkillName.trim().isEmpty()) {
                String[] parts = rawSkillName.split(",");
                for (String part : parts) {
                    if (!part.trim().isEmpty()) {
                        skills.add(part.trim());
                    }
                }
            }
        }

        if (skills.isEmpty()) {
            return "loading skill...";
        } else if (skills.size() == 1) {
            return "loading skill: " + skills.get(0);
        } else {
            StringBuilder sb = new StringBuilder("loading skills: ");
            for (int i = 0; i < skills.size(); i++) {
                sb.append(skills.get(i));
                if (i < skills.size() - 1) {
                    sb.append(", ");
                }
            }
            return sb.toString();
        }
    }

    private List<McpServer> getSortedServers() {
        List<McpServer> sortedList = new ArrayList<>(mRegisteredServers.values());
        Collections.sort(sortedList, (s1, s2) -> Integer.compare(s1.getPriority(), s2.getPriority()));
        return sortedList;
    }

    public List<McpServerInfo> getServerInfos() {
        List<McpServerInfo> list = new ArrayList<>();
        for (McpServer server : getSortedServers()) {
            list.add(new McpServerInfo(server.getName(), server.getDescription(), server.isActive(), server.getPriority()));
        }
        return list;
    }

    public List<McpServerInfo> getConfiguredServers() {
        return getServerInfos();
    }

    public void markSkillLoaded(String skillName) {
        if (skillName != null && !skillName.trim().isEmpty()) {
            mLoadedSkills.add(skillName.toLowerCase().trim());
        }
    }

    public boolean isSkillLoaded(String skillName) {
        if (skillName == null) return false;
        return mLoadedSkills.contains(skillName.toLowerCase().trim());
    }

    public Set<String> getLoadedSkills() {
        return new HashSet<>(mLoadedSkills);
    }

    public String getLoadedSkillsInstructions(Context context) {
        if (mLoadedSkills.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("\n=== LOADED SKILLS SCHEMAS (ACTIVE IN CONTEXT) ===\n");
        sb.append("The following skills are already loaded and ACTIVE in your context window:\n");
        for (String skillName : mLoadedSkills) {
            JSONObject schema = getSkillSchema(context, skillName);
            sb.append("\n--- SKILL: ").append(skillName.toUpperCase()).append(" ---\n");
            if (schema != null && schema.has("schema")) {
                JSONObject schemaObj = schema.optJSONObject("schema");
                if (schemaObj != null) {
                    sb.append(schemaObj.toString()).append("\n");
                }
            }
        }
        sb.append("\nIMPORTANT: Do NOT call '").append(META_TOOL_GET_SKILL_SCHEMA).append("' again for any of the above loaded skills. Invoke their action tools directly.\n");
        return sb.toString();
    }

    public JSONObject getHighLevelCatalog() {
        JSONObject catalog = new JSONObject();
        try {
            JSONArray skills = new JSONArray();

            for (McpServer server : getSortedServers()) {
                JSONObject skillObj = new JSONObject();
                skillObj.put("name", server.getName());
                skillObj.put("description", server.getDescription());
                skillObj.put("priority_tier", server.getPriority());
                skillObj.put("is_loaded", isSkillLoaded(server.getName()));
                skills.put(skillObj);
            }

            catalog.put("available_skills", skills);

            McpTool metaTool = McpTool.builder(META_TOOL_GET_SKILL_SCHEMA, "Fetches detailed parameters and tool schemas for one or multiple skill names in a single batch call.")
                    .addProperty("skill_name", PropertyType.STRING, "Single skill name (e.g. 'file') or comma-separated list of skills to batch load.", false)
                    .addProperty("skills", PropertyType.ARRAY, "List of skill names to batch load (e.g. ['file', 'terminal', 'browser']).", false)
                    .build();

            catalog.put("meta_tool", metaTool.toJsonSchema());

        } catch (JSONException ignored) {}
        return catalog;
    }

    public JSONObject getSkillSchema(Context context, String skillName) {
        JSONObject response = new JSONObject();
        try {
            if (skillName != null && mRegisteredServers.containsKey(skillName.toLowerCase())) {
                String cleanSkillName = skillName.toLowerCase();
                markSkillLoaded(cleanSkillName);
                McpServer server = mRegisteredServers.get(cleanSkillName);
                if (server != null) {
                    JSONArray toolsArray = new JSONArray();
                    for (McpTool tool : server.getTools()) {
                        toolsArray.put(tool.toJsonSchema());
                    }

                    response.put("status", "success");
                    response.put("skill", cleanSkillName);
                    JSONObject schemaObj = new JSONObject();
                    schemaObj.put("tools", toolsArray);

                    // Load guidelines from assets/ai/<skill>_tool.md if present
                    String instructions = loadSkillGuidelinesAsset(context, cleanSkillName);
                    if (instructions != null && !instructions.trim().isEmpty()) {
                        schemaObj.put("guidelines", instructions.trim());
                    }

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

    public JSONObject getSkillSchema(String skillName) {
        return getSkillSchema(null, skillName);
    }

    private String loadSkillGuidelinesAsset(Context context, String skillName) {
        if (context == null || skillName == null) return null;
        String assetPath = "ai/" + skillName + "_tool.md";
        try (InputStream in = context.getAssets().open(assetPath);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            LogViewerService.getInstance().i(TAG, "No asset guidelines found for path: " + assetPath);
            return null;
        }
    }

    public JSONObject executeToolCall(String toolName, JSONObject args, Context context) {
        try {
            if (isMetaTool(toolName)) {
                return executeMetaToolGetSkillSchema(args, context);
            }

            Map<String, Object> arguments = jsonObjectToMap(args);

            for (McpServer server : mRegisteredServers.values()) {
                if (server.handlesTool(toolName)) {
                    markSkillLoaded(server.getName());
                    McpToolResult result = server.callTool(toolName, arguments, context);
                    return result.toJsonObject();
                }
            }

            McpToolResult errResult = McpToolResult.error("Tool execution failed: Unknown tool '" + toolName + "'. Lazy load skill schema via '" + META_TOOL_GET_SKILL_SCHEMA + "' if not loaded.");
            return errResult.toJsonObject();

        } catch (Exception e) {
            return McpToolResult.error("Exception executing tool: " + e.getMessage()).toJsonObject();
        }
    }

    private JSONObject executeMetaToolGetSkillSchema(JSONObject args, Context context) {
        List<String> requestedSkills = new ArrayList<>();

        if (args != null) {
            JSONArray skillsArray = args.optJSONArray("skills");
            if (skillsArray == null) {
                skillsArray = args.optJSONArray("skill_names");
            }

            if (skillsArray != null) {
                for (int i = 0; i < skillsArray.length(); i++) {
                    String s = skillsArray.optString(i);
                    if (s != null && !s.trim().isEmpty()) {
                        requestedSkills.add(s.trim().toLowerCase());
                    }
                }
            } else {
                String rawSkillName = args.optString("skill_name", args.optString("skill", args.optString("tool", "")));
                if (!rawSkillName.trim().isEmpty()) {
                    String[] parts = rawSkillName.split(",");
                    for (String part : parts) {
                        if (!part.trim().isEmpty()) {
                            requestedSkills.add(part.trim().toLowerCase());
                        }
                    }
                }
            }
        }

        if (requestedSkills.isEmpty()) {
            return McpToolResult.error("Missing skill parameter. Provide 'skill_name' (e.g. 'file') or 'skills' array (e.g. ['file', 'terminal']).").toJsonObject();
        }

        if (requestedSkills.size() == 1) {
            return getSkillSchema(context, requestedSkills.get(0));
        }

        JSONObject batchResponse = new JSONObject();
        try {
            JSONArray loadedList = new JSONArray();
            JSONArray schemasArray = new JSONArray();

            for (String skillName : requestedSkills) {
                JSONObject schemaRes = getSkillSchema(context, skillName);
                if ("success".equals(schemaRes.optString("status"))) {
                    loadedList.put(skillName);
                }
                schemasArray.put(schemaRes);
            }

            batchResponse.put("status", "success");
            batchResponse.put("loaded_skills", loadedList);
            batchResponse.put("schemas", schemasArray);
        } catch (JSONException e) {
            return McpToolResult.error("Error creating batch schema response: " + e.getMessage()).toJsonObject();
        }

        return batchResponse;
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
}
