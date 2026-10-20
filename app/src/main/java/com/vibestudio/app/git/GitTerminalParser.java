package com.vibestudio.app.git;

import com.vibestudio.app.service.LogViewerService;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;

public class GitTerminalParser {

    private static final String TAG = "GitTerminalParser";

    public static String getRefreshRepositoryStateCommand() {
        return "status --porcelain && echo \"===NUMSTAT_STAGED===\" && git --no-pager diff --cached --numstat && echo \"===NUMSTAT_UNSTAGED===\" && git --no-pager diff --numstat";
    }

    public static GitRepositoryState parseRepositoryState(File repoDir, String output) {
        GitRepositoryState state = new GitRepositoryState(repoDir);
        if (output == null || output.trim().isEmpty()) {
            return state;
        }

        String cleaned = cleanTerminalOutput(output);
        String statusOutput = cleaned;
        String stagedNumstat = "";
        String unstagedNumstat = "";

        int idxStaged = cleaned.indexOf("===NUMSTAT_STAGED===");
        int idxUnstaged = cleaned.indexOf("===NUMSTAT_UNSTAGED===");

        if (idxStaged != -1) {
            statusOutput = cleaned.substring(0, idxStaged);
            if (idxUnstaged != -1 && idxUnstaged > idxStaged) {
                stagedNumstat = cleaned.substring(idxStaged + "===NUMSTAT_STAGED===".length(), idxUnstaged).trim();
                unstagedNumstat = cleaned.substring(idxUnstaged + "===NUMSTAT_UNSTAGED===".length()).trim();
            } else {
                stagedNumstat = cleaned.substring(idxStaged + "===NUMSTAT_STAGED===".length()).trim();
            }
        }

        if (!statusOutput.isEmpty()) {
            String[] lines = statusOutput.split("\n");
            for (String line : lines) {
                if (line == null || line.length() < 3) continue;

                // Ignore echoed commands or prompts that might bleed into output
                if (line.contains("echo ___CMD_") || line.contains("git status --porcelain")) {
                    continue;
                }

                String statusIndex = line.substring(0, 1);
                String statusWorktree = line.substring(1, 2);
                String filePath = line.substring(2).trim();

                if (filePath.contains(" -> ")) {
                    String[] parts = filePath.split(" -> ");
                    if (parts.length > 1) {
                        filePath = parts[1].trim();
                    }
                }

                if (filePath.startsWith("\"") && filePath.endsWith("\"") && filePath.length() > 2) {
                    filePath = filePath.substring(1, filePath.length() - 1);
                }

                if (!" ".equals(statusIndex) && !"?".equals(statusIndex)) {
                    state.stagedFiles.add(new GitStatusView.GitFileStatus(statusIndex, filePath, true));
                }
                if (!" ".equals(statusWorktree)) {
                    String s = "?".equals(statusWorktree) ? "??" : statusWorktree;
                    state.unstagedFiles.add(new GitStatusView.GitFileStatus(s, filePath, false));
                }
            }
        }

        Map<String, int[]> stagedMap = parseNumstat(stagedNumstat);
        Map<String, int[]> unstagedMap = parseNumstat(unstagedNumstat);

        java.util.Iterator<GitStatusView.GitFileStatus> stagedIter = state.stagedFiles.iterator();
        while (stagedIter.hasNext()) {
            GitStatusView.GitFileStatus file = stagedIter.next();
            int[] stat = stagedMap.get(file.filePath);
            if (stat != null) {
                file.additions = stat[0];
                file.deletions = stat[1];
            }
            state.totalAdditions += file.additions;
            state.totalDeletions += file.deletions;
        }

        java.util.Iterator<GitStatusView.GitFileStatus> unstagedIter = state.unstagedFiles.iterator();
        while (unstagedIter.hasNext()) {
            GitStatusView.GitFileStatus file = unstagedIter.next();
            int[] stat = unstagedMap.get(file.filePath);
            if (stat != null) {
                file.additions = stat[0];
                file.deletions = stat[1];
            } else if ("??".equals(file.status)) {
                File f = repoDir != null ? new File(repoDir, file.filePath) : null;
                file.additions = countFileLines(f);
                file.deletions = 0;
            }
            state.totalAdditions += file.additions;
            state.totalDeletions += file.deletions;
        }

        return state;
    }

    public static Map<String, int[]> parseNumstat(String output) {
        Map<String, int[]> map = new HashMap<>();
        if (output == null || output.trim().isEmpty()) return map;

        String[] lines = output.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.contains("echo ___CMD_")) continue;
            String[] parts = trimmed.split("\\s+");
            if (parts.length >= 3) {
                try {
                    int add = "-".equals(parts[0]) ? 0 : Integer.parseInt(parts[0]);
                    int del = "-".equals(parts[1]) ? 0 : Integer.parseInt(parts[1]);
                    String path = trimmed.substring(trimmed.indexOf(parts[2])).trim();
                    if (path.startsWith("\"") && path.endsWith("\"") && path.length() > 2) {
                        path = path.substring(1, path.length() - 1);
                    }
                    map.put(path, new int[]{add, del});
                } catch (Exception ignored) {}
            }
        }
        return map;
    }

    public static int countFileLines(File file) {
        if (file == null || !file.exists() || !file.isFile() || file.length() > 1_000_000) return 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            int lines = 0;
            while (reader.readLine() != null) lines++;
            return lines;
        } catch (Exception e) {
            return 0;
        }
    }

    public static String cleanTerminalOutput(String rawOutput) {
        if (rawOutput == null) return "";
        String[] lines = rawOutput.split("\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("___CMD_START_") || trimmed.startsWith("___CMD_END_")) {
                continue;
            }
            if (trimmed.contains("echo ___CMD_START_")) {
                continue;
            }
            sb.append(line).append("\n");
        }
        String res = sb.toString();
        while (res.startsWith("\n") || res.startsWith("\r")) {
            res = res.substring(1);
        }
        while (res.endsWith("\n") || res.endsWith("\r") || res.endsWith(" ") || res.endsWith("\t")) {
            res = res.substring(0, res.length() - 1);
        }
        return res;
    }
}
