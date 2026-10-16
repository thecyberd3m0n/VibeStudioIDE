package com.vibestudio.app.mcp;

import android.content.Context;
import java.io.File;

public class PathResolver {

    /**
     * Resolves path arguments consistently across all tools.
     * Absolute paths (starting with /) are preserved.
     * Tilde paths (starting with ~) are expanded to workspace home directory.
     * Relative paths are resolved relative to the workspace home directory ($HOME or /data/data/com.termux/files/home).
     */
    public static File resolveFile(String pathArg, Context context) {
        String homePath = null;
        try {
            homePath = System.getenv("HOME");
        } catch (Exception ignored) {}

        if (homePath == null || homePath.trim().isEmpty()) {
            if (context != null) {
                homePath = new File(context.getFilesDir(), "home").getAbsolutePath();
            } else {
                homePath = "/data/data/com.termux/files/home";
            }
        }

        if (pathArg == null || pathArg.trim().isEmpty()) {
            return new File(homePath);
        }

        String trimmed = pathArg.trim();
        if (trimmed.startsWith("~")) {
            trimmed = homePath + trimmed.substring(1);
        }

        File f = new File(trimmed);
        if (!f.isAbsolute()) {
            f = new File(homePath, trimmed);
        }
        return f;
    }
}
