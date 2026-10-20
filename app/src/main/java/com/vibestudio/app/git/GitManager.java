package com.vibestudio.app.git;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.termux.terminal.TerminalSession;
import com.vibestudio.app.logging.CrashHandler;
import com.vibestudio.app.service.LogViewerService;
import com.vibestudio.app.terminal.TerminalSessionManager;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GitManager {

    private static final String TAG = "GitManager";
    private static GitManager sInstance;

    private TerminalSession mGitSession;
    private final Map<String, GitRepositoryState> mStateCache = new ConcurrentHashMap<>();
    private final List<GitStateChangeListener> mListeners = new CopyOnWriteArrayList<>();
    private final ExecutorService mGitExecutor = Executors.newSingleThreadExecutor();
    private final Set<String> mPendingRefreshes = ConcurrentHashMap.newKeySet();

    public interface GitCallback {
        void onResult(boolean success, String output);
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

    public GitRepositoryState getCachedState(File repoDir) {
        File root = findGitRoot(repoDir);
        if (root == null) return null;
        return mStateCache.get(root.getAbsolutePath());
    }

    public synchronized void ensureGitSession(Context context) {
        if (context == null) return;
        if (mGitSession == null || !mGitSession.isRunning()) {
            LogViewerService.getInstance().i(TAG, "Initializing dedicated background Git PTY session...");
            mGitSession = TerminalSessionManager.getInstance().createSession(context.getApplicationContext());
        }
    }

    public TerminalSession getGitSession() {
        return mGitSession;
    }

    public String runGitCommandSync(Context context, String gitArgs, File repoDir) {
        if (context == null || gitArgs == null) {
            return null;
        }

        ensureGitSession(context);

        String trimmed = gitArgs.trim();
        String gitCmd;
        if (trimmed.startsWith("git ")) {
            gitCmd = "git --no-pager " + trimmed.substring(4);
        } else {
            gitCmd = "git --no-pager " + trimmed;
        }

        String fullCommand = gitCmd;
        if (repoDir != null && repoDir.isDirectory()) {
            fullCommand = "cd " + quotePath(repoDir.getAbsolutePath()) + " && " + fullCommand;
        }

        LogViewerService.getInstance().i(TAG, "Executing Git PTY command: " + fullCommand);

        String output = null;
        if (mGitSession != null && mGitSession.isRunning()) {
            output = TerminalSessionManager.getInstance().runCommandOnSessionSync(mGitSession, fullCommand, 10000);
        }

        if (output == null) {
            LogViewerService.getInstance().w(TAG, "PTY session command returned null, falling back to direct runCommandSync: " + fullCommand);
            output = TerminalSessionManager.getInstance().runCommandSync(context, fullCommand, repoDir, 10000);
        }

        if (output != null) {
            LogViewerService.getInstance().i(TAG, "Git command output:\n" + output);
        } else {
            LogViewerService.getInstance().w(TAG, "Git command returned null output for: " + fullCommand);
        }

        return output;
    }

    public void runGitCommandAsync(Context context, String gitArgs, File repoDir, GitCallback callback) {
        mGitExecutor.execute(() -> {
            try {
                String output = runGitCommandSync(context, gitArgs, repoDir);
                boolean success = output != null && !output.contains("fatal:") && !output.contains("error:");
                if (callback != null) {
                    callback.onResult(success, output);
                }
            } catch (Exception e) {
                LogViewerService.getInstance().e(TAG, "Failed running git async command: git " + gitArgs, e);
                CrashHandler.getInstance().handleException(TAG, "Git command error", e);
                if (callback != null) {
                    callback.onResult(false, e.getMessage());
                }
            }
        });
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

        // Avoid duplicate concurrent refresh requests for the same repository
        if (mPendingRefreshes.contains(repoPath)) {
            LogViewerService.getInstance().d(TAG, "Git refresh already pending for: " + repoPath + ", skipping duplicate request.");
            GitRepositoryState cached = mStateCache.get(repoPath);
            if (callback != null) callback.onStateUpdated(cached);
            return;
        }

        mPendingRefreshes.add(repoPath);
        String gitCmd = GitTerminalParser.getRefreshRepositoryStateCommand();

        runGitCommandAsync(context, gitCmd, root, (success, output) -> {
            mPendingRefreshes.remove(repoPath);
            GitRepositoryState state = parseRepositoryState(root, success ? output : "");
            mStateCache.put(repoPath, state);

            Handler mainHandler = new Handler(Looper.getMainLooper());
            mainHandler.post(() -> {
                if (callback != null) {
                    callback.onStateUpdated(state);
                }
                for (GitStateChangeListener listener : mListeners) {
                    try {
                        listener.onGitStateChanged(state);
                    } catch (Exception e) {
                        LogViewerService.getInstance().e(TAG, "Error notifying git listener", e);
                    }
                }
            });
        });
    }

    public GitRepositoryState parseRepositoryState(File repoDir, String output) {
        return GitTerminalParser.parseRepositoryState(repoDir, output);
    }

    public void initRepository(Context context, File repoDir, GitCallback callback) {
        runGitCommandAsync(context, "init", repoDir, callback);
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

    private String quotePath(String path) {
        if (path == null) return "''";
        return "'" + path.replace("'", "'\\''") + "'";
    }
}
