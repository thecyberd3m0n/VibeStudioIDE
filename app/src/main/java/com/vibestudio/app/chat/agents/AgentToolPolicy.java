package com.vibestudio.app.chat.agents;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Maps an agent's {@code tools} allow-list (VS Code tool-set names such as {@code read}, {@code edit},
 * {@code execute}) onto VibeStudio's built-in tools, and decides whether a tool call may proceed.
 */
public final class AgentToolPolicy {
    private static final List<String> READ_TOOLS = Arrays.asList(
            "editor_read_file", "editor_open", "editor_diff", "file_list_directory", "file_get_info");
    private static final List<String> EDIT_TOOLS = Arrays.asList(
            "editor_replace_lines", "editor_search_replace", "editor_insert_lines", "editor_save",
            "file_create_file", "file_create_directory", "file_delete", "file_move", "file_copy");
    private static final List<String> EXECUTE_TOOLS = Arrays.asList(
            "terminal_open_tab", "execute_command", "execute_command_async", "send_keystroke", "read_terminal_output", "terminal_wait");
    /** Skill tool families addressed by prefix; an agent naming the family allows all of its tools. */
    private static final Map<String, String> PREFIX_FAMILIES = new HashMap<>();
    static {
        PREFIX_FAMILIES.put("browser", "browser_");
        PREFIX_FAMILIES.put("web", "browser_");
        PREFIX_FAMILIES.put("android", "android_");
        PREFIX_FAMILIES.put("device", "android_");
        PREFIX_FAMILIES.put("automator", "android_");
    }

    private final boolean mEnforced;
    private final boolean mAllowAll;
    private final Set<String> mAllowedTools = new HashSet<>();
    private final Set<String> mAllowedPrefixes = new HashSet<>();


    public AgentToolPolicy(List<String> declaredTools) {
        if (declaredTools == null) {
            mEnforced = false;
            mAllowAll = true;
            return;
        }
        boolean recognised = declaredTools.isEmpty();
        boolean all = false;
        for (String raw : declaredTools) {
            String t = raw.trim().toLowerCase(Locale.ROOT);
            if (t.endsWith("/*")) continue; // wildcard of some other tool set, not one of ours
            int slash = t.indexOf('/');
            if (slash >= 0) t = t.substring(slash + 1); // "execute/runInTerminal" style names
            switch (t) {
                case "*":
                case "all":
                    all = true;
                    recognised = true;
                    break;
                case "read":
                case "search":
                case "codebase":
                    mAllowedTools.addAll(READ_TOOLS);
                    recognised = true;
                    break;
                case "edit":
                    mAllowedTools.addAll(READ_TOOLS);
                    mAllowedTools.addAll(EDIT_TOOLS);
                    recognised = true;
                    break;
                case "execute":
                case "terminal":
                case "shell":
                    mAllowedTools.addAll(EXECUTE_TOOLS);
                    recognised = true;
                    break;
                default:
                    if (PREFIX_FAMILIES.containsKey(t)) {
                        mAllowedPrefixes.add(PREFIX_FAMILIES.get(t));
                        recognised = true;
                    } else if (isBuiltInTool(t)) {
                        mAllowedTools.add(t);
                        recognised = true;
                    }

            }
        }
        // A list naming only tools we do not have (e.g. MCP tools of another host) must not brick the agent.
        mAllowAll = all || !recognised;
        mEnforced = !mAllowAll;
    }

    private static boolean isBuiltInTool(String t) {
        return READ_TOOLS.contains(t) || EDIT_TOOLS.contains(t) || EXECUTE_TOOLS.contains(t) || PREFIX_FAMILIES.containsValue(prefixOf(t));
    }

    private static String prefixOf(String tool) {
        return tool.substring(0, tool.indexOf('_') + 1);
    }

    public boolean isEnforced() {
        return mEnforced;
    }

    public boolean isAllowed(String toolName) {
        if (mAllowAll || toolName == null) return true;
        String t = toolName.toLowerCase(Locale.ROOT);
        return mAllowedTools.contains(t) || mAllowedPrefixes.contains(prefixOf(t));
    }

}