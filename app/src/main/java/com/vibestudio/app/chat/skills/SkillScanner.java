package com.vibestudio.app.chat.skills;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SkillScanner {
    private static final String TAG = "SkillScanner";

    private static final String[] SKILL_DIR_REL_PATHS = new String[] {
            ".github/work",
            ".github/skills",
            ".github/prompts",
            ".claude/skills",
            ".claude/commands"
    };

    private static final Pattern FRONTMATTER_PATTERN = Pattern.compile("^---\\s*\\n([\\s\\S]*?)\\n---\\s*\\n?([\\s\\S]*)");
    private static final Pattern NAME_KEY_PATTERN = Pattern.compile("(?m)^name:\\s*[\"']?([^\"'\\r\\n]+)[\"']?");
    private static final Pattern DESC_KEY_PATTERN = Pattern.compile("(?m)^description:\\s*[\"']?([^\"'\\r\\n]+)[\"']?");

    public static List<Skill> scanSkills(String workspacePath) {
        Map<String, Skill> skillMap = new LinkedHashMap<>();

        // 1. First add Built-in standard skills
        for (Skill defaultSkill : getDefaultSkills()) {
            skillMap.put(defaultSkill.getName().toLowerCase(), defaultSkill);
        }

        // 2. Scan Workspace directories
        if (workspacePath != null && !workspacePath.trim().isEmpty()) {
            File wsDir = new File(workspacePath);
            if (wsDir.exists() && wsDir.isDirectory()) {
                for (String relPath : SKILL_DIR_REL_PATHS) {
                    File targetDir = new File(wsDir, relPath);
                    if (targetDir.exists() && targetDir.isDirectory()) {
                        scanDirectoryRecursive(targetDir, skillMap, 0);
                    }
                }
            }
        }

        // Also check process current directory as fallback if workspacePath wasn't set or yielded no custom skills
        File pwdDir = new File(".");
        for (String relPath : SKILL_DIR_REL_PATHS) {
            File targetDir = new File(pwdDir, relPath);
            if (targetDir.exists() && targetDir.isDirectory()) {
                scanDirectoryRecursive(targetDir, skillMap, 0);
            }
        }

        return new ArrayList<>(skillMap.values());
    }

    private static void scanDirectoryRecursive(File dir, Map<String, Skill> skillMap, int depth) {
        if (depth > 5 || dir == null || !dir.isDirectory()) return;

        File[] files = dir.listFiles();
        if (files == null) return;

        for (File f : files) {
            if (f.isDirectory()) {
                scanDirectoryRecursive(f, skillMap, depth + 1);
            } else if (f.isFile() && f.getName().toLowerCase().endsWith(".md")) {
                Skill skill = parseSkillFile(f);
                if (skill != null && !skill.getName().isEmpty()) {
                    // Custom skills scanned from disk override built-ins or earlier entries
                    skillMap.put(skill.getName().toLowerCase(), skill);
                }
            }
        }
    }

    public static Skill parseSkillFile(File file) {
        if (file == null || !file.exists() || !file.isFile()) return null;

        String rawContent = readFileToString(file);
        if (rawContent == null || rawContent.trim().isEmpty()) return null;

        String name = null;
        String description = null;
        String content = rawContent;

        Matcher fmMatcher = FRONTMATTER_PATTERN.matcher(rawContent);
        if (fmMatcher.find()) {
            String frontmatter = fmMatcher.group(1);
            content = fmMatcher.group(2);

            Matcher nameMatcher = NAME_KEY_PATTERN.matcher(frontmatter);
            if (nameMatcher.find()) {
                name = nameMatcher.group(1).trim();
            }

            Matcher descMatcher = DESC_KEY_PATTERN.matcher(frontmatter);
            if (descMatcher.find()) {
                description = descMatcher.group(1).trim();
            }
        }

        // Infer name if not found in frontmatter
        if (name == null || name.isEmpty()) {
            String fileName = file.getName();
            if (fileName.equalsIgnoreCase("SKILL.md") && file.getParentFile() != null) {
                name = file.getParentFile().getName();
            } else if (fileName.toLowerCase().endsWith(".md")) {
                name = fileName.substring(0, fileName.length() - 3);
            } else {
                name = fileName;
            }
        }

        // Clean up skill name (lowercase, replace spaces/underscores with hyphens)
        name = name.toLowerCase().replaceAll("[^a-z0-9_-]", "").replace('_', '-');

        // Infer description if not found in frontmatter
        if (description == null || description.isEmpty()) {
            description = extractFirstHeaderOrLine(content, name);
        }

        return new Skill(name, description, content, file.getAbsolutePath());
    }

    private static String extractFirstHeaderOrLine(String text, String fallbackName) {
        if (text == null) return "Skill: " + fallbackName;
        String[] lines = text.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                return trimmed.replaceAll("^#+\\s*", "").trim();
            } else if (!trimmed.isEmpty() && !trimmed.startsWith("---")) {
                if (trimmed.length() > 80) {
                    return trimmed.substring(0, 77) + "...";
                }
                return trimmed;
            }
        }
        return "Skill command /" + fallbackName;
    }

    private static String readFileToString(File file) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        } catch (IOException e) {
            Log.e(TAG, "Failed to read skill file: " + file.getAbsolutePath(), e);
            return null;
        }
    }

    public static List<Skill> getDefaultSkills() {
        List<Skill> list = new ArrayList<>();
        list.add(new Skill("explain", "Explain code structure, logic, and implementation details",
                "Please explain the selected code or architecture in detail. Focus on key functions, logic flow, and potential edge cases.", "built-in"));

        list.add(new Skill("refactor", "Refactor code to improve readability, performance, and structure",
                "Please refactor the provided code to enhance readability, performance, and maintainability while preserving existing behavior.", "built-in"));

        list.add(new Skill("fix", "Identify and fix bugs, syntax errors, or logical issues",
                "Please analyze the provided code, identify any bugs, syntax errors, or logical flaws, and provide a corrected implementation.", "built-in"));

        list.add(new Skill("test", "Generate unit tests and coverage for selected code",
                "Please generate comprehensive unit tests for the provided code, covering standard execution paths as well as edge cases.", "built-in"));

        list.add(new Skill("commit", "Analyze staged changes and generate git commit message",
                "Please review current workspace changes and generate a clear, concise Conventional Commit message.", "built-in"));

        return list;
    }
}
