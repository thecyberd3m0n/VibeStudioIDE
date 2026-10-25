package com.vibestudio.app.chat.safety;

import com.vibestudio.app.service.LogViewerService;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class WorkspaceBoundaryGuard {

    private static final String TAG = "WorkspaceBoundaryGuard";
    private static final String[] PATH_KEYS = {
        "path", "src", "dst", "dest", "file", "file1", "file2", "file_path", "target_path", "path1", "path2"
    };

    /**
     * Canonicalizes target path relative to workspace directory if specified.
     */
    public static String canonicalizePath(String rawPath, String workspacePath) {
        if (rawPath == null || rawPath.trim().isEmpty()) {
            return workspacePath != null ? workspacePath : "";
        }

        File targetFile = new File(rawPath);
        if (!targetFile.isAbsolute() && workspacePath != null && !workspacePath.isEmpty()) {
            targetFile = new File(workspacePath, rawPath);
        }

        try {
            return targetFile.getCanonicalPath();
        } catch (IOException e) {
            return targetFile.getAbsolutePath();
        }
    }

    /**
     * Checks if targetPath is inside workspacePath.
     */
    public static boolean isWithinWorkspace(String targetPath, String workspacePath) {
        if (workspacePath == null || workspacePath.trim().isEmpty()) {
            // Unscoped session allows access anywhere unless restricted
            return true;
        }

        String canonicalWorkspace = canonicalizePath(workspacePath, null);
        String canonicalTarget = canonicalizePath(targetPath, workspacePath);

        if (canonicalTarget.equals(canonicalWorkspace)) {
            return true;
        }

        String prefix = canonicalWorkspace.endsWith(File.separator)
                ? canonicalWorkspace
                : canonicalWorkspace + File.separator;

        return canonicalTarget.startsWith(prefix);
    }

    /**
     * Inspects tool arguments and canonicalizes relative file paths relative to workspacePath.
     */
    public static void canonicalizeToolArgs(String toolName, JSONObject args, String workspacePath) {
        if (args == null || workspacePath == null || workspacePath.isEmpty()) return;

        try {
            if ("file_list_directory".equalsIgnoreCase(toolName)) {
                String currentPath = args.optString("path", "").trim();
                if (currentPath.isEmpty() || ".".equals(currentPath)) {
                    args.put("path", workspacePath);
                }
            }

            for (String key : PATH_KEYS) {
                if (args.has(key)) {
                    String originalPath = args.optString(key, "");
                    if (!originalPath.isEmpty()) {
                        String canonical = canonicalizePath(originalPath, workspacePath);
                        args.put(key, canonical);
                    }
                }
            }
        } catch (Exception e) {
            LogViewerService.getInstance().e(TAG, "Failed to canonicalize tool args for " + toolName, e);
        }
    }

    /**
     * Returns all target paths being accessed by a tool call.
     */
    public static List<String> getToolTargetPaths(String toolName, JSONObject args) {
        List<String> paths = new ArrayList<>();
        if (args == null) return paths;

        if ("file_list_directory".equalsIgnoreCase(toolName) && (!args.has("path") || args.optString("path", "").trim().isEmpty())) {
            paths.add(".");
        }

        for (String key : PATH_KEYS) {
            if (args.has(key)) {
                String val = args.optString(key, null);
                if (val != null && !val.trim().isEmpty()) {
                    paths.add(val.trim());
                }
            }
        }
        return paths;
    }

    /**
     * Returns the first target path being accessed by a tool call (for single-path callers).
     */
    public static String getToolTargetPath(String toolName, JSONObject args) {
        List<String> paths = getToolTargetPaths(toolName, args);
        return !paths.isEmpty() ? paths.get(0) : null;
    }
}
