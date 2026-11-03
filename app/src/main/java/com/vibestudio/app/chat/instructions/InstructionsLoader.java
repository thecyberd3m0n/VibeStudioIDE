package com.vibestudio.app.chat.instructions;

import android.util.Log;

import com.vibestudio.app.chat.skills.FrontmatterParser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Builds the workspace-instructions section of the system prompt from the files VS Code, Copilot and Claude
 * conventionally read from a repository: {@code .github/copilot-instructions.md}, {@code AGENTS.md},
 * {@code CLAUDE.md}, {@code .github/instructions/*.instructions.md} and {@code .claude/rules/*.md}.
 */
public class InstructionsLoader {
    private static final String TAG = "InstructionsLoader";

    static final int MAX_FILE_CHARS = 8000;
    static final int MAX_TOTAL_CHARS = 20000;
    private static final int MAX_SCAN_DEPTH = 4;

    private static final String[] ALWAYS_ON_FILES = {
            ".github/copilot-instructions.md",
            "AGENTS.md",
            "CLAUDE.md",
            ".claude/CLAUDE.md"
    };

    public static String buildInstructions(String workspacePath) {
        if (workspacePath == null || workspacePath.trim().isEmpty()) return "";
        File root;
        try {
            root = new File(workspacePath).getCanonicalFile();
        } catch (IOException e) {
            return "";
        }
        if (!root.isDirectory()) return "";

        Set<String> seen = new HashSet<>();
        StringBuilder always = new StringBuilder();
        StringBuilder scoped = new StringBuilder();

        for (String rel : ALWAYS_ON_FILES) {
            appendFile(root, new File(root, rel), always, seen);
        }
        for (File f : listMarkdown(new File(root, ".github/instructions"), ".instructions.md")) {
            appendRuleFile(root, f, "applyTo", always, scoped, seen);
        }
        for (File f : listMarkdown(new File(root, ".claude/rules"), ".md")) {
            appendRuleFile(root, f, "paths", always, scoped, seen);
        }

        StringBuilder out = new StringBuilder();
        if (always.length() > 0) {
            out.append("\n=== WORKSPACE INSTRUCTIONS (follow these for all work in this repository) ===\n").append(always);
        }
        if (scoped.length() > 0) {
            out.append("\n=== FILE-SCOPED INSTRUCTIONS (apply only when working on files matching the given pattern) ===\n").append(scoped);
        }
        if (out.length() > MAX_TOTAL_CHARS) {
            out.setLength(MAX_TOTAL_CHARS);
            out.append("\n[... workspace instructions truncated ...]\n");
        }
        return out.toString();
    }

    private static void appendRuleFile(File root, File file, String patternKey,
                                       StringBuilder always, StringBuilder scoped, Set<String> seen) {
        String raw = read(root, file);
        if (raw == null || !seen.add(canonical(file))) return;
        FrontmatterParser.Result parsed = FrontmatterParser.parse(raw);
        String body = parsed.getBody().trim();
        if (body.isEmpty()) return;

        List<String> patterns = parsed.getStringList(patternKey);
        boolean matchesEverything = patterns == null || patterns.isEmpty() || patterns.contains("**") || patterns.contains("**/*") || patterns.contains("*");
        StringBuilder target = matchesEverything ? always : scoped;
        target.append("\n--- ").append(relativePath(root, file));
        if (!matchesEverything) {
            target.append(" (applies to: ").append(join(patterns)).append(")");
        }
        target.append(" ---\n").append(clip(body)).append("\n");
    }

    private static void appendFile(File root, File file, StringBuilder target, Set<String> seen) {
        String raw = read(root, file);
        if (raw == null || !seen.add(canonical(file))) return;
        String body = FrontmatterParser.parse(raw).getBody().trim();
        if (body.isEmpty()) return;
        target.append("\n--- ").append(relativePath(root, file)).append(" ---\n").append(clip(body)).append("\n");
    }

    /** Reads a UTF-8 file only if it resolves (after symlinks) inside the workspace root. */
    private static String read(File root, File file) {
        if (!file.isFile()) return null;
        try {
            File real = file.getCanonicalFile();
            if (!real.getPath().startsWith(root.getPath() + File.separator)) {
                Log.w(TAG, "Ignoring instructions file outside workspace: " + file);
                return null;
            }
            return new String(Files.readAllBytes(real.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Log.w(TAG, "Failed to read " + file + ": " + e.getMessage());
            return null;
        }
    }

    private static List<File> listMarkdown(File dir, String suffix) {
        List<File> out = new ArrayList<>();
        collect(dir, suffix, 0, out);
        return out;
    }

    private static void collect(File dir, String suffix, int depth, List<File> out) {
        File[] files = dir.isDirectory() && depth <= MAX_SCAN_DEPTH ? dir.listFiles() : null;
        if (files == null) return;
        Arrays.sort(files);
        for (File f : files) {
            if (f.isDirectory()) {
                collect(f, suffix, depth + 1, out);
            } else if (f.getName().toLowerCase(Locale.ROOT).endsWith(suffix)) {
                out.add(f);
            }
        }
    }

    private static String clip(String body) {
        return body.length() > MAX_FILE_CHARS ? body.substring(0, MAX_FILE_CHARS) + "\n[... truncated ...]" : body;
    }

    private static String canonical(File f) {
        try {
            return f.getCanonicalPath();
        } catch (IOException e) {
            return f.getAbsolutePath();
        }
    }

    private static String relativePath(File root, File f) {
        String p = f.getAbsolutePath();
        String r = root.getAbsolutePath();
        return p.startsWith(r + File.separator) ? p.substring(r.length() + 1) : f.getName();
    }

    private static String join(List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (String s : items) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(s);
        }
        return sb.toString();
    }
}
