package com.vibestudio.app.git;

import android.content.Context;

import com.termux.terminal.TerminalSession;
import com.vibestudio.app.logging.CrashHandler;
import com.vibestudio.app.service.LogViewerService;
import com.vibestudio.app.terminal.TerminalSessionManager;

import java.io.File;

public class GitManager {

    private static final String TAG = "GitManager";
    private static GitManager sInstance;

    private TerminalSession mGitSession;

    public interface GitCallback {
        void onResult(boolean success, String output);
    }

    private GitManager() {}

    public static synchronized GitManager getInstance() {
        if (sInstance == null) {
            sInstance = new GitManager();
        }
        return sInstance;
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

        String fullCommand = "git " + gitArgs;
        if (repoDir != null && repoDir.isDirectory()) {
            fullCommand = "cd " + quotePath(repoDir.getAbsolutePath()) + " && " + fullCommand;
        }

        return TerminalSessionManager.getInstance().runCommandSync(context, fullCommand, repoDir, 30000);
    }

    public void runGitCommandAsync(Context context, String gitArgs, File repoDir, GitCallback callback) {
        new Thread(() -> {
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
        }).start();
    }

    public void initRepository(Context context, File repoDir, GitCallback callback) {
        runGitCommandAsync(context, "init", repoDir, callback);
    }

    public boolean isGitRepository(File dir) {
        if (dir == null || !dir.isDirectory()) return false;
        File gitFolder = new File(dir, ".git");
        return gitFolder.exists();
    }

    private String quotePath(String path) {
        if (path == null) return "''";
        return "'" + path.replace("'", "'\\''") + "'";
    }
}
