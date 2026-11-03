package com.vibestudio.app.chat.agents;

import android.util.Log;

import com.vibestudio.app.chat.skills.FrontmatterParser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Discovers custom agents in the workspace (the repository root). Later folders override earlier ones. */
public class AgentScanner {
    private static final String TAG = "AgentScanner";

    static final String[] AGENT_DIR_REL_PATHS = new String[] {
            ".claude/agents",
            ".github/agents"
    };

    private static final String AGENT_FILE_SUFFIX = ".agent.md";

    public static List<Agent> scanAgents(String workspacePath) {
        if (workspacePath == null || workspacePath.trim().isEmpty()) return Collections.emptyList();
        File wsDir = new File(workspacePath);
        if (!wsDir.isDirectory()) return Collections.emptyList();

        Map<String, Agent> agents = new LinkedHashMap<>();
        for (String rel : AGENT_DIR_REL_PATHS) {
            File dir = new File(wsDir, rel);
            File[] files = dir.isDirectory() ? dir.listFiles() : null;
            if (files == null) continue;
            Arrays.sort(files);
            for (File f : files) {
                if (!f.isFile() || !f.getName().toLowerCase(Locale.ROOT).endsWith(".md")) continue;
                Agent agent = parseAgentFile(f);
                if (agent != null) {
                    agents.put(agent.getName().toLowerCase(Locale.ROOT), agent);
                }
            }
        }
        return new ArrayList<>(agents.values());
    }

    public static Agent findAgent(String workspacePath, String name) {
        if (name == null) return null;
        String wanted = normalizeName(name);
        for (Agent a : scanAgents(workspacePath)) {
            if (a.getName().equals(wanted)) return a;
        }
        return null;
    }

    public static Agent parseAgentFile(File file) {
        String raw;
        try {
            raw = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Log.e(TAG, "Failed to read agent file: " + file.getAbsolutePath(), e);
            return null;
        }
        if (raw.trim().isEmpty()) return null;

        FrontmatterParser.Result parsed = FrontmatterParser.parse(raw);
        String fileName = file.getName();
        String fallbackName = fileName.toLowerCase(Locale.ROOT).endsWith(AGENT_FILE_SUFFIX)
                ? fileName.substring(0, fileName.length() - AGENT_FILE_SUFFIX.length())
                : fileName.substring(0, fileName.length() - ".md".length());

        String name = parsed.getString("name");
        name = normalizeName(name == null || name.isEmpty() ? fallbackName : name);
        if (name.isEmpty()) return null;

        String description = parsed.getString("description");
        String body = parsed.getBody().trim();
        if (body.isEmpty() && (description == null || description.isEmpty())) return null;

        List<String> models = parsed.getStringList("model");
        String model = models != null && !models.isEmpty() ? models.get(0) : null;

        // Same visibility rules as VS Code: `infer` (legacy) wins over `disable-model-invocation`.
        Boolean infer = parsed.getBoolean("infer");
        Boolean disableModel = parsed.getBoolean("disable-model-invocation");
        boolean modelInvocable = infer != null ? infer : !(disableModel != null && disableModel);
        Boolean userInvocable = parsed.getBoolean("user-invocable");

        return new Agent(name, description, body, parsed.getStringList("tools"), model,
                file.getAbsolutePath(), userInvocable == null || userInvocable, modelInvocable);
    }

    /** Lowercase, whitespace to hyphens, everything outside [a-z0-9_-] dropped. */
    public static String normalizeName(String raw) {
        if (raw == null) return "";
        return raw.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "-").replaceAll("[^a-z0-9_-]", "");
    }
}
