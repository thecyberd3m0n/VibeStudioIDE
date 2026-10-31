package com.vibestudio.app.git;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.termux.terminal.TerminalSession;
import com.vibestudio.app.logging.CrashHandler;
import com.vibestudio.app.logging.SessionLogger;
import com.vibestudio.app.service.LogViewerService;
import com.vibestudio.app.terminal.TerminalSessionManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class GitManager {

    private static final String TAG = "GitManager";
    public static final String GIT_SESSION_LABEL = "Git Session PTY Manager";
    private static GitManager sInstance;

    private volatile TerminalSession mGitSession;
    private final List<GitStateChangeListener> mListeners = new CopyOnWriteArrayList<>();
    /** Repo path to the callbacks waiting for a second refresh; null while nobody asked for one. Present = running. */
    private final Map<String, List<GitStateCallback>> mRefreshes = new HashMap<>();

    public interface GitCallback {
        void onResult(boolean success, String output);
    }

    public interface GitBranchesCallback {
        void onResult(boolean success, List<String> branches);
    }

    public interface GitStateChangeListener {
        void onGitStateChanged(GitRepositoryState state);
    }

    public interface GitStateCallback {
        void onStateUpdated(GitRepositoryState state);
    }

    private GitManager() {}

    public static synchronized GitManager getInstance() {
        if (sInstance == null) {
            sInstance = new GitManager();
        }
        return sInstance;
    }

    public void addStateChangeListener(GitStateChangeListener listener) {
        if (listener != null && !mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    public void removeStateChangeListener(GitStateChangeListener listener) {
        if (listener != null) {
            mListeners.remove(listener);
        }
    }

    public synchronized void ensureGitSession(Context context) {
        if (context == null) return;
        if (mGitSession == null || !mGitSession.isRunning()) {
            LogViewerService.getInstance().i(TAG, "Initializing dedicated background Git PTY session...");
            try {
                mGitSession = TerminalSessionManager.getInstance().createSession(
                        context.getApplicationContext(), null, GIT_SESSION_LABEL);
            } catch (RuntimeException e) {
                mGitSession = null;
                getGitLogger().event("Cannot create Git PTY session", e);
                LogViewerService.getInstance().e(TAG, "Cannot create Git PTY session", e);
            }
            if (mGitSession == null) {
                getGitLogger().event("Git PTY session is unavailable");
            }
        }
    }

    public synchronized TerminalSession getGitSession() {
        return mGitSession;
    }

    /** The log channel (visible in the Log Viewer) that records every Git PTY command. */
    public SessionLogger getGitLogger() {
        return LogViewerService.getInstance().getOrCreateChannel(GIT_SESSION_LABEL);
    }

    private static int effectiveTimeout(int timeoutMs) {
        return timeoutMs > 0 ? timeoutMs : 10000;
    }

    /**
     * Single entry point for every Git command: runs it on the Git PTY session and blocks until it finishes.
     * Returns null when the session could not be created.
     */
    private TerminalSessionManager.CommandResult execute(Context context, String gitArgs, File repoDir, int timeoutMs) {
        ensureGitSession(context);
        String fullCommand = formatFullCommand(gitArgs, repoDir);
        TerminalSession session = getGitSession();
        if (session == null) {
            LogViewerService.getInstance().e(TAG, "Git command skipped, no PTY session: " + sanitizeForLogging(fullCommand));
            return null;
        }
        return TerminalSessionManager.getInstance().runCommandBlocking(session, fullCommand, effectiveTimeout(timeoutMs));
    }

    /** Asynchronous variant of {@link #execute}; the callback runs on the main thread. */
    private void executeAsync(Context context, String gitArgs, File repoDir, int timeoutMs,
                              TerminalSessionManager.CommandCallback callback) {
        ensureGitSession(context);
        String fullCommand = formatFullCommand(gitArgs, repoDir);
        TerminalSession session = getGitSession();
        if (session == null) {
            LogViewerService.getInstance().e(TAG, "Git command skipped, no PTY session: " + sanitizeForLogging(fullCommand));
            new Handler(Looper.getMainLooper()).post(() ->
                    callback.onResult(new TerminalSessionManager.CommandResult(-1, "Git PTY session is unavailable")));
            return;
        }
        TerminalSessionManager.getInstance().runCommand(session, fullCommand, effectiveTimeout(timeoutMs), callback);
    }

    public String runGitCommandSync(Context context, String gitArgs, File repoDir) {
        return runGitCommandSync(context, gitArgs, repoDir, 10000);
    }

    public String runGitCommandSync(Context context, String gitArgs, File repoDir, int timeoutMs) {
        TerminalSessionManager.CommandResult res = execute(context, gitArgs, repoDir, timeoutMs);
        return res != null && res.output != null ? cleanTerminalOutput(res.output) : null;
    }

    public String runGitRawCommandSync(Context context, String gitArgs, File repoDir) {
        return runGitRawCommandSync(context, gitArgs, repoDir, 10000);
    }

    public String runGitRawCommandSync(Context context, String gitArgs, File repoDir, int timeoutMs) {
        TerminalSessionManager.CommandResult res = execute(context, gitArgs, repoDir, timeoutMs);
        return res != null && res.output != null ? cleanRawFileOutput(res.output) : null;
    }

    public void runGitRawCommandAsync(Context context, String gitArgs, File repoDir, GitCallback callback) {
        runGitRawCommandAsync(context, gitArgs, repoDir, 10000, callback);
    }

    public void runGitRawCommandAsync(Context context, String gitArgs, File repoDir, int timeoutMs, GitCallback callback) {
        executeAsync(context, gitArgs, repoDir, timeoutMs, res -> deliver(res, true, callback));
    }

    public void runGitCommandAsync(Context context, String gitArgs, File repoDir, GitCallback callback) {
        runGitCommandAsync(context, gitArgs, repoDir, 10000, callback);
    }

    public void runGitCommandAsync(Context context, String gitArgs, File repoDir, int timeoutMs, GitCallback callback) {
        executeAsync(context, gitArgs, repoDir, timeoutMs, res -> deliver(res, false, callback));
    }

    private void deliver(TerminalSessionManager.CommandResult res, boolean raw, GitCallback callback) {
        String rawOutput = res != null ? res.output : null;
        String output = rawOutput != null ? (raw ? cleanRawFileOutput(rawOutput) : cleanTerminalOutput(rawOutput)) : null;
        boolean success = res != null && res.isSuccess() && output != null && !output.contains("fatal:") && !output.contains("error:");
        if (!success) {
            LogViewerService.getInstance().w(TAG, "Git command failed or returned error output (exitCode="
                    + (res != null ? res.exitCode : -1) + "):\n" + sanitizeForLogging(output != null ? output : "null output"));
        }
        if (callback != null) {
            callback.onResult(success, output != null && !output.isEmpty() ? output : (rawOutput != null ? rawOutput : ""));
        }
    }

    // --- Domain Git API ---

    /** Local branches first, then remote-tracking ones as "remote/<remote>/<branch>" (remote HEAD aliases are skipped). */
    public void fetchBranches(Context context, File repoDir, GitBranchesCallback callback) {
        String cmd = "for-each-ref --format=\"%(refname)\" refs/heads refs/remotes";
        runGitCommandAsync(context, cmd, repoDir, (success, output) -> {
            List<String> branches = new ArrayList<>();
            if (success && output != null) {
                for (String line : output.split("\n")) {
                    String ref = line.trim();
                    if (ref.startsWith("refs/heads/")) {
                        branches.add(ref.substring("refs/heads/".length()));
                    } else if (ref.startsWith("refs/remotes/") && !ref.endsWith("/HEAD")) {
                        branches.add("remote/" + ref.substring("refs/remotes/".length()));
                    }
                }
            }
            if (callback != null) {
                callback.onResult(success, branches);
            }
        });
    }

    /** Deletes a local branch; without {@code force} git refuses branches that are not fully merged. */
    public void deleteBranch(Context context, File repoDir, String branchName, boolean force, GitCallback callback) {
        runGitCommandAsync(context, "branch " + (force ? "-D " : "-d ") + quotePath(branchName), repoDir, callback);
    }

    public void checkoutBranch(Context context, File repoDir, String branchName, GitCallback callback) {
        String cmd = "checkout " + quotePath(branchName);
        runGitCommandAsync(context, cmd, repoDir, callback);
    }

    public void createAndCheckoutBranch(Context context, File repoDir, String branchName, GitCallback callback) {
        String cmd = "checkout -b " + quotePath(branchName);
        runGitCommandAsync(context, cmd, repoDir, callback);
    }

    public void mergeBranch(Context context, File repoDir, String branchName, GitCallback callback) {
        String cmd = "merge --no-edit " + quotePath(branchName);
        runGitCommandAsync(context, cmd, repoDir, callback);
    }

    public void rebaseBranch(Context context, File repoDir, String branchName, GitCallback callback) {
        String cmd = "rebase " + quotePath(branchName);
        runGitCommandAsync(context, cmd, repoDir, callback);
    }

    public void cherryPickCommit(Context context, File repoDir, String commitHash, GitCallback callback) {
        String cmd = "cherry-pick --no-edit " + quotePath(commitHash);
        runGitCommandAsync(context, cmd, repoDir, callback);
    }

    public void getFileAtHead(Context context, File repoDir, String filePath, GitCallback callback) {
        String cmd = "show HEAD:" + filePath;
        runGitRawCommandAsync(context, cmd, repoDir, callback);
    }

    public void stageFile(Context context, File repoDir, String filePath, GitCallback callback) {
        String cmd = "add -- " + quotePath(filePath);
        runGitCommandAsync(context, cmd, repoDir, callback);
    }

    public void unstageFile(Context context, File repoDir, String filePath, GitCallback callback) {
        String cmd = "reset HEAD -- " + quotePath(filePath);
        runGitCommandAsync(context, cmd, repoDir, callback);
    }

    public void commit(Context context, File repoDir, String message, GitCallback callback) {
        String cmd = "commit -m " + quotePath(message);
        runGitCommandAsync(context, cmd, repoDir, callback);
    }

    public void revertFile(Context context, File repoDir, String filePath, String fileStatus, boolean isStaged, GitCallback callback) {
        if ("??".equals(fileStatus)) {
            String cmd = "clean -f -- " + quotePath(filePath);
            runGitCommandAsync(context, cmd, repoDir, callback);
        } else if (isStaged) {
            String cmd = "restore --staged --worktree -- " + quotePath(filePath);
            runGitCommandAsync(context, cmd, repoDir, callback);
        } else {
            String cmd = "checkout HEAD -- " + quotePath(filePath);
            runGitCommandAsync(context, cmd, repoDir, callback);
        }
    }

    public void refreshRepositoryState(Context context, File repoDir, GitStateCallback callback) {
        if (repoDir == null) {
            if (callback != null) callback.onStateUpdated(null);
            return;
        }

        File root = findGitRoot(repoDir);
        if (root == null) {
            if (callback != null) callback.onStateUpdated(null);
            return;
        }

        String repoPath = root.getAbsolutePath();

        synchronized (mRefreshes) {
            if (mRefreshes.containsKey(repoPath)) {
                // The running refresh may predate the change that triggered this request: run again once it ends.
                List<GitStateCallback> waiting = mRefreshes.computeIfAbsent(repoPath, k -> new ArrayList<>());
                if (callback != null) waiting.add(callback);
                return;
            }
            mRefreshes.put(repoPath, null);
        }

        runGitCommandAsync(context, getRefreshRepositoryStateCommand(), root, (success, output) -> {
            GitRepositoryState state = parseRepositoryState(root, success ? output : "");
            List<GitStateCallback> waiting;
            synchronized (mRefreshes) {
                waiting = mRefreshes.remove(repoPath);
            }

            if (callback != null) callback.onStateUpdated(state);
            for (GitStateChangeListener listener : mListeners) {
                try {
                    listener.onGitStateChanged(state);
                } catch (Exception e) {
                    LogViewerService.getInstance().e(TAG, "Error notifying git listener", e);
                }
            }
            if (waiting != null) {
                refreshRepositoryState(context, root, fresh -> {
                    for (GitStateCallback waiter : waiting) waiter.onStateUpdated(fresh);
                });
            }
        });
    }

    public void initRepository(Context context, File repoDir, GitCallback callback) {
        runGitCommandAsync(context, "init", repoDir, callback);
    }

    public void cloneRepository(Context context, String repoUrl, File targetDir, GitCallback callback) {
        String cmd = "git clone -- " + quotePath(repoUrl);
        runGitCommandAsync(context, cmd, targetDir, 180000, callback);
    }

    public void syncRepository(Context context, File repoDir, GitCallback callback) {
        String cmd = "git pull --no-edit && git --no-pager push";
        runGitCommandAsync(context, cmd, repoDir, callback);
    }

    public void pushSetUpstream(Context context, File repoDir, String branchName, GitCallback callback) {
        String bName = (branchName != null && !branchName.isEmpty()) ? branchName : "HEAD";
        String cmd = "push --set-upstream origin " + quotePath(bName);
        runGitCommandAsync(context, cmd, repoDir, callback);
    }

    public void setGitCredentials(Context context, String name, String email, File repoDir, GitCallback callback) {
        if (context != null && name != null && email != null) {
            try {
                File homeDir = new File(context.getFilesDir(), "home");
                if (!homeDir.exists()) homeDir.mkdirs();
                File gitConfig = new File(homeDir, ".gitconfig");

                StringBuilder sb = new StringBuilder();
                boolean inUserSection = false;
                boolean writtenUserSection = false;

                if (gitConfig.exists()) {
                    try (BufferedReader br = new BufferedReader(new FileReader(gitConfig))) {
                        String line;
                        while ((line = br.readLine()) != null) {
                            String trimmed = line.trim();
                            if (trimmed.equalsIgnoreCase("[user]")) {
                                inUserSection = true;
                                writtenUserSection = true;
                                sb.append("[user]\n");
                                sb.append("\tname = ").append(name).append("\n");
                                sb.append("\temail = ").append(email).append("\n");
                            } else if (inUserSection && (trimmed.startsWith("name") || trimmed.startsWith("email"))) {
                                // skip existing name/email lines inside [user]
                            } else {
                                if (trimmed.startsWith("[")) {
                                    inUserSection = false;
                                }
                                sb.append(line).append("\n");
                            }
                        }
                    }
                }

                if (!writtenUserSection) {
                    sb.append("[user]\n");
                    sb.append("\tname = ").append(name).append("\n");
                    sb.append("\temail = ").append(email).append("\n");
                }

                try (FileOutputStream out = new FileOutputStream(gitConfig)) {
                    out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
                }
            } catch (Exception e) {
                LogViewerService.getInstance().w(TAG, "Failed to write .gitconfig directly", e);
            }
        }

        String cmd = "git config --global user.name " + quotePath(name) + " && git config --global user.email " + quotePath(email);
        runGitCommandAsync(context, cmd, repoDir, callback);
    }

    
    private List<File> getGitConfigCandidateFiles(Context context) {
        List<File> candidates = new ArrayList<>();
        if (context == null) return candidates;
        candidates.add(new File(context.getFilesDir(), "home/.gitconfig"));
        candidates.add(new File("/data/data/" + context.getPackageName() + "/files/home/.gitconfig"));
        candidates.add(new File("/data/user/0/" + context.getPackageName() + "/files/home/.gitconfig"));
        return candidates;
    }

    private List<File> getSshCandidateDirs(Context context) {
        List<File> candidates = new ArrayList<>();
        if (context == null) return candidates;
        candidates.add(new File(context.getFilesDir(), "home/.ssh"));
        candidates.add(new File("/data/data/" + context.getPackageName() + "/files/home/.ssh"));
        candidates.add(new File("/data/user/0/" + context.getPackageName() + "/files/home/.ssh"));
        return candidates;
    }

    public boolean isGitUserConfigured(Context context) {
        if (context == null) return false;
        for (File gitConfig : getGitConfigCandidateFiles(context)) {
            if (gitConfig.exists()) {
                try (BufferedReader br = new BufferedReader(new FileReader(gitConfig))) {
                    String line;
                    boolean hasName = false;
                    boolean hasEmail = false;
                    while ((line = br.readLine()) != null) {
                        String trimmed = line.trim();
                        if (trimmed.startsWith("name =") || trimmed.startsWith("name=")) {
                            String val = trimmed.substring(trimmed.indexOf("=") + 1).trim();
                            if (!val.isEmpty()) hasName = true;
                        }
                        if (trimmed.startsWith("email =") || trimmed.startsWith("email=")) {
                            String val = trimmed.substring(trimmed.indexOf("=") + 1).trim();
                            if (!val.isEmpty()) hasEmail = true;
                        }
                    }
                    if (hasName && hasEmail) return true;
                } catch (Exception ignored) {}
            }
        }
        String name = runGitCommandSync(context, "config --global user.name", null);
        if (name == null || name.trim().isEmpty() || name.contains("error") || name.contains("fatal")) {
            return false;
        }
        String email = runGitCommandSync(context, "config --global user.email", null);
        return email != null && !email.trim().isEmpty() && !email.contains("error") && !email.contains("fatal");
    }

    public String getSshPublicKey(Context context) {
        if (context == null) return null;
        for (File sshDir : getSshCandidateDirs(context)) {
            if (sshDir.exists() && sshDir.isDirectory()) {
                String[] pubKeys = new String[]{"id_ed25519.pub", "id_rsa.pub", "id_ecdsa.pub", "id_dsa.pub"};
                for (String pub : pubKeys) {
                    File k = new File(sshDir, pub);
                    if (k.exists() && k.length() > 0) {
                        try (BufferedReader reader = new BufferedReader(new FileReader(k))) {
                            StringBuilder sb = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) {
                                sb.append(line).append("\n");
                            }
                            String keyStr = sb.toString().trim();
                            if (!keyStr.isEmpty()) {
                                return keyStr;
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
        }
        return null;
    }

    public boolean isSshKeyConfigured(Context context) {
        if (context == null) return false;
        for (File sshDir : getSshCandidateDirs(context)) {
            if (sshDir.exists() && sshDir.isDirectory()) {
                String[] keys = new String[]{"id_rsa", "id_ed25519", "id_ecdsa", "id_dsa"};
                for (String key : keys) {
                    File k = new File(sshDir, key);
                    if (k.exists() && k.length() > 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public boolean isGitRoot(File dir) {
        if (dir == null || !dir.isDirectory()) return false;
        File gitFolder = new File(dir, ".git");
        return gitFolder.exists();
    }

    public File findGitRoot(File dir) {
        if (dir == null) return null;
        File current = dir.isDirectory() ? dir : dir.getParentFile();
        while (current != null) {
            File gitFolder = new File(current, ".git");
            if (gitFolder.exists()) {
                return current;
            }
            current = current.getParentFile();
        }
        return null;
    }

    public boolean isGitRepository(File dir) {
        return findGitRoot(dir) != null;
    }

    public static boolean isAuthorIdentityError(String output) {
        if (output == null) return false;
        String lower = output.toLowerCase();
        return lower.contains("author identity unknown")
            || lower.contains("please tell me who you are")
            || lower.contains("unable to auto-detect email address")
            || lower.contains("user.name")
            || lower.contains("user.email");
    }

    public static boolean isGitAuthRequiredError(String output) {
        if (output == null) return false;
        String lower = output.toLowerCase();
        return lower.contains("authentication failed")
            || lower.contains("could not read username")
            || lower.contains("username for")
            || lower.contains("password for")
            || lower.contains("terminal prompts disabled")
            || lower.contains("prompt failed")
            || lower.contains("401")
            || lower.contains("403")
            || lower.contains("access denied")
            || lower.contains("permission denied")
            || lower.contains("invalid username or password")
            || lower.contains("auth failed");
    }

    public static boolean isNoUpstreamError(String output) {
        if (output == null) return false;
        String lower = output.toLowerCase();
        return lower.contains("no upstream branch")
            || lower.contains("has no upstream branch")
            || lower.contains("no upstream configured")
            || lower.contains("set-upstream")
            || lower.contains("set the remote as upstream");
    }

    public static String sanitizeForLogging(String text) {
        return SessionLogger.redact(text);
    }

    // --- Private Helper Methods (Implementation Details) ---

    public String formatFullCommand(String gitArgs, File repoDir) {
        if (gitArgs == null) return "";
        String trimmed = gitArgs.trim();
        String gitCmd;
        if (trimmed.startsWith("git --no-pager ")) {
            gitCmd = trimmed;
        } else if (trimmed.startsWith("git ")) {
            gitCmd = "git --no-pager " + trimmed.substring(4);
        } else if (trimmed.startsWith("(") || trimmed.startsWith("mkdir") || trimmed.startsWith("chmod") 
                || trimmed.startsWith("rm") || trimmed.startsWith("ssh-keygen") || trimmed.startsWith("echo")
                || trimmed.contains("||") || trimmed.contains("&&") || trimmed.contains(";")) {
            gitCmd = trimmed;
        } else {
            gitCmd = "git --no-pager " + trimmed;
        }

        if (repoDir != null && repoDir.isDirectory()) {
            return "cd " + quotePath(repoDir.getAbsolutePath()) + " && " + gitCmd;
        } else {
            return gitCmd;
        }
    }

    private String executeCommandInternal(Context context, String gitArgs, File repoDir, int timeoutMs) {
        return runGitCommandSync(context, gitArgs, repoDir, timeoutMs);
    }

    private static String getRefreshRepositoryStateCommand() {
        // Read-only: without this, git status rewrites .git/index, which the Source Control view watches for changes.
        return "( export GIT_OPTIONAL_LOCKS=0 ; (git branch --show-current 2>/dev/null || git symbolic-ref --short -q HEAD 2>/dev/null || git rev-parse --short -q HEAD 2>/dev/null || echo \"main\") ; " +
               "echo \"===AHEAD_BEHIND===\" ; " +
               "UB=\"$(git rev-parse --abbrev-ref --symbolic-full-name @{u} 2>/dev/null || echo \"\")\" ; " +
               "if [ -z \"$UB\" ]; then " +
               "  CURB=\"$(git branch --show-current 2>/dev/null)\" ; " +
               "  if git rev-parse --verify \"origin/$CURB\" >/dev/null 2>&1; then UB=\"origin/$CURB\" ; " +
               "  elif [ \"$CURB\" != \"main\" ] && git rev-parse --verify \"main\" >/dev/null 2>&1; then UB=\"main\" ; " +
               "  elif [ \"$CURB\" != \"master\" ] && git rev-parse --verify \"master\" >/dev/null 2>&1; then UB=\"master\" ; " +
               "  elif git rev-parse --verify \"origin/main\" >/dev/null 2>&1; then UB=\"origin/main\" ; " +
               "  elif git rev-parse --verify \"origin/master\" >/dev/null 2>&1; then UB=\"origin/master\" ; " +
               "  fi ; " +
               "fi ; " +
               "if [ -n \"$UB\" ]; then git rev-list --left-right --count \"HEAD...$UB\" 2>/dev/null || echo \"0 0\" ; else echo \"0 0\" ; fi ; " +
               "echo \"===STATUS===\" ; git status --porcelain --ignored=no ; " +
               "echo \"===NUMSTAT_STAGED===\" ; git --no-pager diff --cached --numstat ; " +
               "echo \"===NUMSTAT_UNSTAGED===\" ; git --no-pager diff --numstat )";
    }

    public static boolean isMergeConflictOutput(String output) {
        if (output == null) return false;
        String lower = output.toLowerCase();
        return lower.contains("conflict (") || lower.contains("automatic merge failed")
                || lower.contains("fix conflicts") || lower.contains("could not apply")
                || lower.contains("resolve all conflicts");
    }

    public void abortOperation(Context context, File repoDir, String operation, GitCallback callback) {
        runGitCommandAsync(context, operation + " --abort", repoDir, callback);
    }

    /** Drops the commit the operation is stuck on. Merges have nothing to skip. */
    public void skipOperation(Context context, File repoDir, String operation, GitCallback callback) {
        runGitCommandAsync(context, operation + " --skip", repoDir, callback);
    }

    public void continueOperation(Context context, File repoDir, String operation, GitCallback callback) {
        String cmd = "merge".equals(operation) ? "commit --no-edit" : operation + " --continue";
        runGitCommandAsync(context, cmd, repoDir, callback);
    }

    public static GitRepositoryState parseRepositoryState(File repoDir, String output) {
        GitRepositoryState state = new GitRepositoryState(repoDir);
        File gitDir = new File(repoDir, ".git");
        if (new File(gitDir, "MERGE_HEAD").exists()) state.operationInProgress = "merge";
        else if (new File(gitDir, "rebase-merge").isDirectory() || new File(gitDir, "rebase-apply").isDirectory()) state.operationInProgress = "rebase";
        else if (new File(gitDir, "CHERRY_PICK_HEAD").exists()) state.operationInProgress = "cherry-pick";
        else if (new File(gitDir, "REVERT_HEAD").exists()) state.operationInProgress = "revert";
        if (output == null || output.trim().isEmpty()) {
            return state;
        }

        String cleaned = cleanTerminalOutput(output);

        int idxAheadBehind = cleaned.indexOf("===AHEAD_BEHIND===");
        int idxStatus = cleaned.indexOf("===STATUS===");

        if (idxAheadBehind != -1 && idxStatus != -1 && idxStatus > idxAheadBehind) {
            String branchRaw = cleaned.substring(0, idxAheadBehind).trim();
            if (!branchRaw.isEmpty()) {
                String[] branchLines = branchRaw.split("\n");
                for (String line : branchLines) {
                    String lineTrimmed = line.trim();
                    if (lineTrimmed.isEmpty()) continue;
                    if (lineTrimmed.contains("___CMD_") || lineTrimmed.contains("git --no-pager") || lineTrimmed.startsWith("cd ")) {
                        continue;
                    }
                    state.branchName = lineTrimmed;
                    break;
                }
            }

            String abRaw = cleaned.substring(idxAheadBehind + "===AHEAD_BEHIND===".length(), idxStatus).trim();
            if (!abRaw.isEmpty()) {
                String[] abParts = abRaw.split("\\s+");
                if (abParts.length >= 2) {
                    try {
                        state.ahead = Integer.parseInt(abParts[0]);
                        state.behind = Integer.parseInt(abParts[1]);
                    } catch (Exception ignored) {}
                }
            }

            cleaned = cleaned.substring(idxStatus + "===STATUS===".length());
        } else if (idxStatus != -1) {
            String branchRaw = cleaned.substring(0, idxStatus).trim();
            if (!branchRaw.isEmpty()) {
                String[] branchLines = branchRaw.split("\n");
                for (String line : branchLines) {
                    String lineTrimmed = line.trim();
                    if (lineTrimmed.isEmpty()) continue;
                    if (lineTrimmed.contains("___CMD_") || lineTrimmed.contains("git --no-pager") || lineTrimmed.startsWith("cd ")) {
                        continue;
                    }
                    state.branchName = lineTrimmed;
                    break;
                }
            }
            cleaned = cleaned.substring(idxStatus + "===STATUS===".length());
        }

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

                if (line.contains("echo ___CMD_") || line.contains("git status --porcelain")) {
                    continue;
                }

                String statusIndex = line.substring(0, 1);
                String statusWorktree = line.substring(1, 2);

                if ("!".equals(statusIndex) || "!".equals(statusWorktree)) {
                    continue;
                }
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

                // Porcelain codes git uses for unmerged paths: DD AA AU UA DU UD UU.
                if ((statusIndex + statusWorktree).matches("DD|AA|[ADU]U|U[ADU]")) {
                    state.conflictedFiles.add(new GitStatusView.GitFileStatus(statusIndex + statusWorktree, filePath, false));
                    continue;
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

        Iterator<GitStatusView.GitFileStatus> stagedIter = state.stagedFiles.iterator();
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

        Iterator<GitStatusView.GitFileStatus> unstagedIter = state.unstagedFiles.iterator();
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

    private static Map<String, int[]> parseNumstat(String output) {
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

    private static int countFileLines(File file) {
        if (file == null || !file.exists() || !file.isFile() || file.length() > 1_000_000) return 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            int lines = 0;
            while (reader.readLine() != null) lines++;
            return lines;
        } catch (Exception e) {
            return 0;
        }
    }

    private static String cleanTerminalOutput(String rawOutput) {
        if (rawOutput == null) return "";
        String[] lines = rawOutput.split("\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("___CMD_START_") || trimmed.startsWith("___CMD_END_")) {
                continue;
            }
            if (trimmed.contains("echo ___CMD_START_") || trimmed.contains("echo ___CMD_END_")) {
                continue;
            }
            if (trimmed.startsWith("cd ") && (trimmed.contains("git") || trimmed.contains("&&"))) {
                continue;
            }
            if (trimmed.contains("echo \"===STATUS===\"") || trimmed.contains("echo \"===NUMSTAT_")) {
                continue;
            }
            if (trimmed.startsWith("git --no-pager") || trimmed.startsWith("(git branch") || trimmed.startsWith("GIT_TERMINAL_PROMPT") || trimmed.startsWith("GIT_SSH_COMMAND") || trimmed.startsWith("export GIT_")) {
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

    private static String cleanRawFileOutput(String rawOutput) {
        if (rawOutput == null) return "";
        String[] lines = rawOutput.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            if (trimmed.startsWith("___CMD_START_") || trimmed.startsWith("___CMD_END_")) {
                continue;
            }
            if (trimmed.contains("echo ___CMD_START_")) {
                continue;
            }
            sb.append(line);
            if (i < lines.length - 1) {
                sb.append("\n");
            }
        }
        String res = sb.toString();
        if (res.startsWith("\n") || res.startsWith("\r")) {
            int start = 0;
            while (start < res.length() && (res.charAt(start) == '\n' || res.charAt(start) == '\r')) {
                start++;
            }
            res = res.substring(start);
        }
        return res;
    }

    private String quotePath(String path) {
        if (path == null) return "''";
        return "'" + path.replace("'", "'\\''") + "'";
    }

    public String getGitUserName(Context context) {
        if (context == null) return "";
        for (File gitConfig : getGitConfigCandidateFiles(context)) {
            if (gitConfig.exists()) {
                try (BufferedReader br = new BufferedReader(new FileReader(gitConfig))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        String trimmed = line.trim();
                        if (trimmed.startsWith("name =") || trimmed.startsWith("name=")) {
                            return trimmed.substring(trimmed.indexOf("=") + 1).trim();
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
        String name = runGitCommandSync(context, "config --global user.name", null);
        if (name != null && !name.trim().isEmpty() && !name.contains("error") && !name.contains("fatal")) {
            return name.trim();
        }
        return "";
    }

    public String getGitUserEmail(Context context) {
        if (context == null) return "";
        for (File gitConfig : getGitConfigCandidateFiles(context)) {
            if (gitConfig.exists()) {
                try (BufferedReader br = new BufferedReader(new FileReader(gitConfig))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        String trimmed = line.trim();
                        if (trimmed.startsWith("email =") || trimmed.startsWith("email=")) {
                            return trimmed.substring(trimmed.indexOf("=") + 1).trim();
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
        String email = runGitCommandSync(context, "config --global user.email", null);
        if (email != null && !email.trim().isEmpty() && !email.contains("error") && !email.contains("fatal")) {
            return email.trim();
        }
        return "";
    }

    public void generateSshKey(Context context, GitCallback callback) {
        generateSshKey(context, null, null, callback);
    }

    public void generateSshKey(Context context, String passphrase, GitCallback callback) {
        generateSshKey(context, passphrase, null, callback);
    }

    public void generateSshKey(Context context, String passphrase, String comment, GitCallback callback) {
        String pass = (passphrase != null) ? passphrase : "";
        String comm = (comment != null && !comment.trim().isEmpty()) ? comment.trim() : "vibestudio@termux";
        String cmd = "mkdir -p ~/.ssh && chmod 700 ~/.ssh && rm -f ~/.ssh/id_ed25519 ~/.ssh/id_ed25519.pub && ssh-keygen -t ed25519 -C " + quotePath(comm) + " -N " + quotePath(pass) + " -f ~/.ssh/id_ed25519";
        runGitCommandAsync(context, cmd, null, callback);
    }

}
