package com.vibestudio.app.mcp;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.vibestudio.app.fragments.EditorFragment;
import com.vibestudio.app.mcp.model.McpTool;
import com.vibestudio.app.mcp.model.McpToolResult;
import com.vibestudio.app.mcp.model.PropertyType;
import com.vibestudio.app.service.LogViewerService;
import com.vibestudio.app.tab.TabManager;
import com.vibestudio.app.tab.TabType;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class EditorMcpServer implements McpServer {

    private static final String TAG = "EditorMcpServer";
    private static final String NAME = "editor";
    private static final String DESCRIPTION = "Provides token-efficient single-file read and modification tools: line-range reading, targeted line replacements, search and replace, line insertion, opening file in tab, and saving changes.";

    private boolean mIsActive = false;
    private final List<McpTool> mTools;

    public EditorMcpServer() {
        mTools = new ArrayList<>();

        // Tool 1: editor_open
        mTools.add(McpTool.builder("editor_open", "Open a file in an Editor tab view within VibeStudio IDE.")
                .addProperty("path", PropertyType.STRING, "File path to open.", true)
                .build());

        // Tool 2: editor_read_file
        mTools.add(McpTool.builder("editor_read_file", "Read lines from a file with optional range filtering for token efficiency.")
                .addProperty("path", PropertyType.STRING, "File path to read.", true)
                .addProperty("start_line", PropertyType.INTEGER, "1-based start line number (default: 1).", false)
                .addProperty("end_line", PropertyType.INTEGER, "1-based end line number (default: end of file).", false)
                .addProperty("max_lines", PropertyType.INTEGER, "Maximum lines to return for token safety (default: 100).", false)
                .build());

        // Tool 3: editor_replace_lines
        mTools.add(McpTool.builder("editor_replace_lines", "Replace a target range of lines (start_line to end_line inclusive) with new text content.")
                .addProperty("path", PropertyType.STRING, "File path.", true)
                .addProperty("start_line", PropertyType.INTEGER, "1-based start line number.", true)
                .addProperty("end_line", PropertyType.INTEGER, "1-based end line number.", true)
                .addProperty("new_content", PropertyType.STRING, "New text content to insert.", true)
                .build());

        // Tool 4: editor_search_replace
        mTools.add(McpTool.builder("editor_search_replace", "Search for a string pattern in a file and replace it.")
                .addProperty("path", PropertyType.STRING, "File path.", true)
                .addProperty("search", PropertyType.STRING, "Substring to search for.", true)
                .addProperty("replace", PropertyType.STRING, "Replacement substring.", true)
                .build());

        // Tool 5: editor_insert_lines
        mTools.add(McpTool.builder("editor_insert_lines", "Insert text lines at a specified line index in a file.")
                .addProperty("path", PropertyType.STRING, "File path.", true)
                .addProperty("line", PropertyType.INTEGER, "1-based line number index before which content will be inserted.", true)
                .addProperty("content", PropertyType.STRING, "Text content to insert.", true)
                .build());

        // Tool 6: editor_save
        mTools.add(McpTool.builder("editor_save", "Persist current edits of a file to disk.")
                .addProperty("path", PropertyType.STRING, "File path to save.", true)
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
        LogViewerService.getInstance().i(TAG, "Editor MCP Server started.");
    }

    @Override
    public void stopServer() {
        mIsActive = false;
        LogViewerService.getInstance().i(TAG, "Editor MCP Server stopped.");
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
            if ("editor_open".equals(toolName)) {
                String pathArg = getStringArg(arguments, "path", "");
                if (pathArg.isEmpty()) return McpToolResult.error("Missing 'path' parameter.");

                File file = new File(pathArg);
                if (!file.exists()) return McpToolResult.error("File does not exist: " + pathArg);

                new Handler(Looper.getMainLooper()).post(() -> {
                    EditorFragment frag = EditorFragment.newInstance(file.getAbsolutePath());
                    TabManager.getInstance().openTab(TabType.EDITOR, file.getName(), "📝", frag, true);
                });

                return McpToolResult.success("Opened editor tab for: " + file.getAbsolutePath());

            } else if ("editor_read_file".equals(toolName)) {
                String pathArg = getStringArg(arguments, "path", "");
                if (pathArg.isEmpty()) return McpToolResult.error("Missing 'path' parameter.");

                File file = new File(pathArg);
                if (!file.exists()) return McpToolResult.error("File does not exist: " + pathArg);

                List<String> lines = readFileLines(file);
                int totalLines = lines.size();

                int startLine = getIntArg(arguments, "start_line", 1);
                int endLine = getIntArg(arguments, "end_line", totalLines);
                int maxLines = getIntArg(arguments, "max_lines", 100);

                if (startLine < 1) startLine = 1;
                if (endLine > totalLines) endLine = totalLines;
                if (endLine - startLine + 1 > maxLines) {
                    endLine = startLine + maxLines - 1;
                }

                StringBuilder sb = new StringBuilder();
                sb.append("File: ").append(file.getAbsolutePath()).append("\n");
                sb.append("Lines: ").append(startLine).append("-").append(endLine).append(" of ").append(totalLines).append("\n\n");

                for (int i = startLine - 1; i < endLine && i < totalLines; i++) {
                    sb.append(String.format("%4d | %s\n", (i + 1), lines.get(i)));
                }

                return McpToolResult.success(sb.toString());

            } else if ("editor_replace_lines".equals(toolName)) {
                String pathArg = getStringArg(arguments, "path", "");
                if (pathArg.isEmpty()) return McpToolResult.error("Missing 'path' parameter.");

                File file = new File(pathArg);
                if (!file.exists()) return McpToolResult.error("File does not exist: " + pathArg);

                int startLine = getIntArg(arguments, "start_line", -1);
                int endLine = getIntArg(arguments, "end_line", -1);
                String newContent = getStringArg(arguments, "new_content", "");

                if (startLine < 1 || endLine < 1 || endLine < startLine) {
                    return McpToolResult.error("Invalid start_line or end_line numbers.");
                }

                List<String> lines = readFileLines(file);
                int origLineCount = lines.size();

                int startIndex = Math.min(startLine - 1, origLineCount);
                int endIndex = Math.min(endLine, origLineCount);

                List<String> replacementLines = Arrays.asList(newContent.split("\r?\n", -1));
                int removedLines = Math.max(0, endIndex - startIndex);
                int addedLines = replacementLines.size();

                List<String> newLinesList = new ArrayList<>();
                for (int i = 0; i < startIndex; i++) {
                    newLinesList.add(lines.get(i));
                }
                newLinesList.addAll(replacementLines);
                for (int i = endIndex; i < origLineCount; i++) {
                    newLinesList.add(lines.get(i));
                }

                writeFileLines(file, newLinesList);

                JSONObject res = new JSONObject();
                res.put("status", "success");
                res.put("path", file.getAbsolutePath());
                res.put("start_line", startLine);
                res.put("end_line", endLine);
                res.put("added_lines", addedLines);
                res.put("removed_lines", removedLines);
                res.put("total_lines", newLinesList.size());
                return McpToolResult.success(res.toString(2));

            } else if ("editor_search_replace".equals(toolName)) {
                String pathArg = getStringArg(arguments, "path", "");
                String search = getStringArg(arguments, "search", "");
                String replace = getStringArg(arguments, "replace", "");

                if (pathArg.isEmpty() || search.isEmpty()) {
                    return McpToolResult.error("Missing 'path' or 'search' parameter.");
                }

                File file = new File(pathArg);
                if (!file.exists()) return McpToolResult.error("File does not exist: " + pathArg);

                List<String> lines = readFileLines(file);
                int replacementsCount = 0;
                List<String> updatedLines = new ArrayList<>();

                for (String line : lines) {
                    if (line.contains(search)) {
                        replacementsCount++;
                        updatedLines.add(line.replace(search, replace));
                    } else {
                        updatedLines.add(line);
                    }
                }

                if (replacementsCount > 0) {
                    writeFileLines(file, updatedLines);
                }

                JSONObject res = new JSONObject();
                res.put("status", "success");
                res.put("path", file.getAbsolutePath());
                res.put("replacements_made", replacementsCount);
                return McpToolResult.success(res.toString(2));

            } else if ("editor_insert_lines".equals(toolName)) {
                String pathArg = getStringArg(arguments, "path", "");
                int lineIndex = getIntArg(arguments, "line", 1);
                String content = getStringArg(arguments, "content", "");

                if (pathArg.isEmpty()) return McpToolResult.error("Missing 'path' parameter.");

                File file = new File(pathArg);
                if (!file.exists()) return McpToolResult.error("File does not exist: " + pathArg);

                List<String> lines = readFileLines(file);
                int targetIndex = Math.max(0, Math.min(lineIndex - 1, lines.size()));

                List<String> insertLinesList = Arrays.asList(content.split("\r?\n", -1));
                lines.addAll(targetIndex, insertLinesList);

                writeFileLines(file, lines);

                JSONObject res = new JSONObject();
                res.put("status", "success");
                res.put("path", file.getAbsolutePath());
                res.put("inserted_at_line", lineIndex);
                res.put("added_lines", insertLinesList.size());
                res.put("total_lines", lines.size());
                return McpToolResult.success(res.toString(2));

            } else if ("editor_save".equals(toolName)) {
                String pathArg = getStringArg(arguments, "path", "");
                if (pathArg.isEmpty()) return McpToolResult.error("Missing 'path' parameter.");

                File file = new File(pathArg);
                if (!file.exists()) return McpToolResult.error("File does not exist: " + pathArg);

                return McpToolResult.success("Saved file: " + file.getAbsolutePath());

            } else {
                return McpToolResult.error("Unknown tool: " + toolName);
            }
        } catch (Exception e) {
            LogViewerService.getInstance().e(TAG, "Error in Editor MCP tool " + toolName, e);
            return McpToolResult.error("Editor MCP Error: " + e.getMessage());
        }
    }

    private List<String> readFileLines(File file) throws Exception {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    private void writeFileLines(File file, List<String> lines) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            for (int i = 0; i < lines.size(); i++) {
                fos.write(lines.get(i).getBytes(StandardCharsets.UTF_8));
                if (i < lines.size() - 1) {
                    fos.write("\n".getBytes(StandardCharsets.UTF_8));
                }
            }
        }
    }

    private String getStringArg(Map<String, Object> args, String key, String defaultValue) {
        Object val = args.get(key);
        return (val != null && !"null".equalsIgnoreCase(val.toString())) ? String.valueOf(val) : defaultValue;
    }

    private int getIntArg(Map<String, Object> args, String key, int defaultValue) {
        Object val = args.get(key);
        if (val instanceof Number) return ((Number) val).intValue();
        if (val instanceof String) {
            try { return Integer.parseInt((String) val); } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }
}
