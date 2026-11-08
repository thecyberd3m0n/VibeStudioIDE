package com.vibestudio.app.mcp;

import android.content.Context;

import com.vibestudio.app.chat.agents.Agent;
import com.vibestudio.app.chat.agents.AgentScanner;
import com.vibestudio.app.chat.skills.Skill;
import com.vibestudio.app.chat.skills.SkillScanner;
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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class McpClientManager {

    private static final String TAG = "McpClientManager";
    public static final String PLANNED_DELAY_FIELD = "planned_delay_ms";
    private static final String PLANNED_DELAY_DESCRIPTION = "Optional. Milliseconds to wait after dispatching, before the result is read back "
            + "(e.g. 5000 to start a dev server, 15000 for dependency installs). Omit or 0 for instant operations.";
    public static final String META_TOOL_GET_SKILL_SCHEMA = "get_skill_schema";

    public static final String TOOL_TASK_COMPLETE = "task_complete";

    public static boolean isTaskCompleteTool(String toolName) {
        return TOOL_TASK_COMPLETE.equalsIgnoreCase(toolName);
    }

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
    private final Set<String> mLoadedSkills = ConcurrentHashMap.newKeySet();
    private final Map<String, JSONObject> mCachedSchemas = new ConcurrentHashMap<>();
    private String mWorkspacePath;

    public McpClientManager() {
        registerServer(new FileMcpServer());
        registerServer(new EditorMcpServer());
        registerServer(new TerminalMcpServer());
        registerServer(new BrowserMcpServer());
    }

    public void setWorkspacePath(String workspacePath) {
        this.mWorkspacePath = workspacePath;
    }

    public String getWorkspacePath() {
        return mWorkspacePath;
    }

    public boolean isRegisteredServer(String name) {
        if (name == null) return false;
        return mRegisteredServers.containsKey(name.toLowerCase().trim());
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
        return server != null && server.supportsPlannedDelay(toolName);
    }

    public boolean plannedDelayBeforeExecution(String toolName) {
        if (toolName == null || isMetaTool(toolName)) {
            return false;
        }
        McpServer server = findServerForTool(toolName);
        return server != null && server.plannedDelayBeforeExecution(toolName);
    }

    public CharSequence formatToolCall(String toolName, JSONObject args) {
        if (isMetaTool(toolName)) {
            return formatMetaToolCall(args);
        }
        McpServer server = findServerForTool(toolName);
        if (server != null) {
            try {
                CharSequence custom = server.prepareToolCall(toolName, args, null, null);
                if (custom != null) {
                    return custom;
                }
            } catch (Exception ignored) {}
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

    public String getLoadedSkillsInstructions(Context context) {
        return getLoadedSkillsInstructions(context, false);
    }

    /**
     * @param nativeTools the built-in skills' tool schemas already reach the model as declared tools, so only their
     *                    usage guidelines are injected; custom skills and agents are always injected in full
     */
    public String getLoadedSkillsInstructions(Context context, boolean nativeTools) {
        if (mLoadedSkills.isEmpty()) {
            return "";
        }
        StringBuilder body = new StringBuilder();
        for (String skillName : mLoadedSkills) {
            JSONObject schema = getSkillSchema(context, skillName);
            JSONObject schemaObj = schema != null ? schema.optJSONObject("schema") : null;
            if (schemaObj == null) {
                if (!nativeTools) {
                    body.append("\n--- SKILL: ").append(skillName.toUpperCase()).append(" ---\n");
                }
                continue;
            }
            if (nativeTools && mRegisteredServers.containsKey(skillName.toLowerCase())) {
                String guidelines = schemaObj.optString("guidelines", "").trim();
                if (!guidelines.isEmpty()) {
                    body.append("\n--- ").append(skillName.toUpperCase()).append(" GUIDELINES ---\n").append(guidelines).append("\n");
                }
                continue;
            }
            body.append("\n--- SKILL: ").append(skillName.toUpperCase()).append(" ---\n").append(schemaObj).append("\n");
        }
        if (body.length() == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (nativeTools) {
            sb.append("\n=== LOADED SKILLS (ACTIVE IN CONTEXT) ===\n");
            sb.append("Built-in skill tools are declared natively; these are their guidelines, plus any custom skills or agents you loaded:\n");
        } else {
            sb.append("\n=== LOADED SKILLS SCHEMAS (ACTIVE IN CONTEXT) ===\n");
            sb.append("The following skills are already loaded and ACTIVE in your context window:\n");
        }
        sb.append(body);
        sb.append("\nIMPORTANT: Do NOT call '").append(META_TOOL_GET_SKILL_SCHEMA).append("' again for any of the above loaded skills. Invoke their action tools directly.\n");
        return sb.toString();
    }

    private static McpTool buildMetaTool() {
        return McpTool.builder(META_TOOL_GET_SKILL_SCHEMA, "Fetches detailed parameters and tool schemas for one or multiple skill names in a single batch call.")
                .addProperty("skill_name", PropertyType.STRING, "Single skill name (e.g. 'file') or comma-separated list of skills to batch load.", false)
                .addProperty("skills", PropertyType.ARRAY, "List of skill names to batch load (e.g. ['file', 'terminal', 'browser']).", false)
                .build();
    }

    /**
     * Tools to declare through a provider's native function-calling API: every registered server's tools, so the
     * model can act immediately without a schema-loading round trip, plus the meta tool for custom skills and agents.
     */
    public List<McpTool> getActiveTools() {
        List<McpTool> tools = new ArrayList<>();
        tools.add(buildMetaTool());
        tools.add(McpTool.builder(TOOL_TASK_COMPLETE, "Call when the user's request is fully done, or when you need input from the user. This ends the agent loop.")
                .addProperty("summary", PropertyType.STRING, "Final message shown to the user: what was done, or the question you need answered.", false)
                .build());
        for (McpServer server : getSortedServers()) {
            for (McpTool tool : server.getTools()) {
                tools.add(server.supportsPlannedDelay(tool.getName())
                        ? tool.withOptionalProperty(PLANNED_DELAY_FIELD, PropertyType.INTEGER, PLANNED_DELAY_DESCRIPTION)
                        : tool);
            }
        }
        return tools;
    }

    public JSONObject getHighLevelCatalog() {
        return getHighLevelCatalog(false);
    }

    /**
     * @param nativeTools the built-in skills' tools are declared natively: their entries carry no is_loaded flag
     *                    (it would change the prompt mid-session for nothing) and the meta tool schema is left out,
     *                    since it is declared with the other tools
     */
    public JSONObject getHighLevelCatalog(boolean nativeTools) {
        JSONObject catalog = new JSONObject();
        try {
            JSONArray skills = new JSONArray();

            for (McpServer server : getSortedServers()) {
                JSONObject skillObj = new JSONObject();
                skillObj.put("name", server.getName());
                skillObj.put("description", server.getDescription());
                skillObj.put("priority_tier", server.getPriority());
                if (!nativeTools) {
                    skillObj.put("is_loaded", isSkillLoaded(server.getName()));
                }
                skills.put(skillObj);
            }

            List<Skill> customSkills = SkillScanner.scanSkills(mWorkspacePath);
            for (Skill customSkill : customSkills) {
                if (!customSkill.isModelInvocable()) continue;
                boolean exists = false;
                for (int i = 0; i < skills.length(); i++) {
                    if (skills.getJSONObject(i).optString("name", "").equalsIgnoreCase(customSkill.getName())) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    JSONObject skillObj = new JSONObject();
                    skillObj.put("name", customSkill.getName());
                    skillObj.put("description", customSkill.getDescription());
                    skillObj.put("priority_tier", 100);
                    skillObj.put("is_loaded", isSkillLoaded(customSkill.getName()));
                    skills.put(skillObj);
                }
            }

            // Custom agents are adoptable personas: loading one returns its instructions like a skill does.
            for (Agent agent : AgentScanner.scanAgents(mWorkspacePath)) {
                if (!agent.isModelInvocable()) continue;
                boolean exists = false;
                for (int i = 0; i < skills.length(); i++) {
                    if (skills.getJSONObject(i).optString("name", "").equalsIgnoreCase(agent.getName())) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    JSONObject agentObj = new JSONObject();
                    agentObj.put("name", agent.getName());
                    agentObj.put("type", "agent");
                    agentObj.put("description", agent.getDescription());
                    agentObj.put("priority_tier", 100);
                    agentObj.put("is_loaded", isSkillLoaded(agent.getName()));
                    skills.put(agentObj);
                }
            }

            catalog.put("available_skills", skills);

            if (!nativeTools) {
                catalog.put("meta_tool", buildMetaTool().toJsonSchema());
            }

        } catch (JSONException ignored) {}
        return catalog;
    }

    public JSONObject getSkillSchema(Context context, String skillName) {
        if (skillName != null && mCachedSchemas.containsKey(skillName.toLowerCase())) {
            markSkillLoaded(skillName.toLowerCase());
            return mCachedSchemas.get(skillName.toLowerCase());
        }

        JSONObject response = new JSONObject();
        try {
            if (skillName != null) {
                String cleanSkillName = skillName.toLowerCase().trim();
                if (mRegisteredServers.containsKey(cleanSkillName)) {
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
                        mCachedSchemas.put(cleanSkillName, response);
                        return response;
                    }
                }

                List<Skill> customSkills = SkillScanner.scanSkills(mWorkspacePath);
                for (Skill skill : customSkills) {
                    if (skill.getName().equalsIgnoreCase(cleanSkillName)) {
                        markSkillLoaded(cleanSkillName);
                        response.put("status", "success");
                        response.put("skill", cleanSkillName);
                        JSONObject schemaObj = new JSONObject();
                        if (skill.getDescription() != null && !skill.getDescription().isEmpty()) {
                            schemaObj.put("description", skill.getDescription());
                        }
                        if (skill.getContent() != null && !skill.getContent().isEmpty()) {
                            schemaObj.put("guidelines", skill.getContent());
                        }
                        response.put("schema", schemaObj);
                        mCachedSchemas.put(cleanSkillName, response);
                        return response;
                    }
                }
            }
            if (skillName != null) {
                Agent agent = AgentScanner.findAgent(mWorkspacePath, skillName);
                if (agent != null && agent.isModelInvocable()) {
                    String cleanAgentName = agent.getName();
                    markSkillLoaded(cleanAgentName);
                    response.put("status", "success");
                    response.put("skill", cleanAgentName);
                    JSONObject schemaObj = new JSONObject();
                    schemaObj.put("type", "agent");
                    if (!agent.getDescription().isEmpty()) {
                        schemaObj.put("description", agent.getDescription());
                    }
                    if (!agent.getInstructions().isEmpty()) {
                        schemaObj.put("guidelines", agent.getInstructions());
                    }
                    response.put("schema", schemaObj);
                    mCachedSchemas.put(cleanAgentName, response);
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


    public JSONObject onToolCallCompleted(String toolName, JSONObject args, Context context, JSONObject result) {
        McpServer server = findServerForTool(toolName);
        if (server == null) {
            return result;
        }
        return server.onToolCallCompleted(toolName, jsonObjectToMap(args), context, result);
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
