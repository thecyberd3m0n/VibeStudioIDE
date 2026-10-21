package com.vibestudio.app.chat.safety;

import android.content.Context;
import android.text.TextUtils;

import com.vibestudio.app.service.LogViewerService;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

public class WorkspaceBoundaryGuard {

    private static final String TAG = "WorkspaceBoundaryGuard";

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
            if (args.has("path")) {
                String originalPath = args.optString("path", "");
                String canonical = canonicalizePath(originalPath, workspacePath);
                args.put("path", canonical);
            }
            if (args.has("src")) {
                String originalSrc = args.optString("src", "");
                String canonicalSrc = canonicalizePath(originalSrc, workspacePath);
                args.put("src", canonicalSrc);
            }
            if (args.has("dst")) {
                String originalDst = args.optString("dst", "");
                String canonicalDst = canonicalizePath(originalDst, workspacePath);
                args.put("dst", canonicalDst);
            }
            if (args.has("file")) {
                String originalFile = args.optString("file", "");
                String canonicalFile = canonicalizePath(originalFile, workspacePath);
                args.put("file", canonicalFile);
            }
        } catch (Exception e) {
            LogViewerService.getInstance().e(TAG, "Failed to canonicalize tool args for " + toolName, e);
        }
    }

    /**
     * Returns the target path being accessed by a tool call, if applicable.
     */
    public static String getToolTargetPath(String toolName, JSONObject args) {
        if (args == null) return null;
        if (args.has("path")) return args.optString("path", null);
        if (args.has("src")) return args.optString("src", null);
        if (args.has("file")) return args.optString("file", null);
        return null;
    }
}
