package com.vibestudio.app.terminal;

import android.content.Context;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import com.vibestudio.app.logging.CrashHandler;
import com.vibestudio.app.service.LogViewerService;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TerminalSessionManager {

    private static final String TAG = "TerminalSessionManager";
    private static TerminalSessionManager sInstance;
    private TerminalSession mTerminalSession;

    private TerminalSessionManager() {}

    public static synchronized TerminalSessionManager getInstance() {
        if (sInstance == null) {
            sInstance = new TerminalSessionManager();
        }
        return sInstance;
    }

    public synchronized void ensureSessionStarted(final Context context) {
        if (context == null) return;
        if (isSessionAvailable()) {
            LogViewerService.getInstance().d(TAG, "Terminal session already running.");
            return;
        }

        try {
            File filesDir = context.getApplicationContext().getFilesDir();
            File usrDir = new File(filesDir, "libtermux/usr");
            File homeDir = new File(filesDir, "libtermux/home");
            if (!homeDir.exists()) homeDir.mkdirs();

            File bashFile = new File(usrDir, "bin/bash");
            String shellPath = bashFile.exists() ? bashFile.getAbsolutePath() : "/system/bin/sh";

            String[] envVars = new String[]{
                    "PREFIX=" + usrDir.getAbsolutePath(),
                    "HOME=" + homeDir.getAbsolutePath(),
                    "PATH=" + new File(usrDir, "bin").getAbsolutePath() + ":" + new File(usrDir, "bin/applets").getAbsolutePath() + ":/system/bin:/system/xbin",
                    "LD_LIBRARY_PATH=" + new File(usrDir, "lib").getAbsolutePath(),
                    "TMPDIR=" + new File(usrDir, "tmp").getAbsolutePath(),
                    "TERM=xterm-256color",
                    "LANG=en_US.UTF-8",
                    "APT_CONFIG=" + new File(usrDir, "etc/apt/apt.conf").getAbsolutePath(),
                    "DPKG_ADMINDIR=" + new File(usrDir, "var/lib/dpkg").getAbsolutePath(),
                    "TERMUX_APP_PACKAGE_MANAGER=apt",
                    "TERMUX_MAIN_PACKAGE_FORMAT=debian",
                    "TERMUX_PKG_NO_MIRROR_SELECT=1"
            };

            String cwd = homeDir.getAbsolutePath();
            String[] args = bashFile.exists() ? new String[]{"-bash"} : new String[]{shellPath};

            TerminalSessionClient sessionClient = new TerminalSessionClient() {
                @Override
                public void setTerminalShellPid(TerminalSession session, int pid) {}

                @Override
                public void onTextChanged(TerminalSession changedSession) {}

                @Override
                public void onTitleChanged(TerminalSession updatedSession) {}

                @Override
                public void onSessionFinished(TerminalSession finishedSession) {
                    LogViewerService.getInstance().w(TAG, "Shared TerminalSession finished.");
                    synchronized (TerminalSessionManager.this) {
                        if (mTerminalSession == finishedSession) {
                            mTerminalSession = null;
                        }
                    }
                }

                @Override
                public void onCopyTextToClipboard(TerminalSession session, String text) {}

                @Override
                public void onPasteTextFromClipboard(TerminalSession session) {}

                @Override
                public void onBell(TerminalSession session) {}

                @Override
                public void onColorsChanged(TerminalSession session) {}

                @Override
                public void onTerminalCursorStateChange(boolean state) {}

                @Override
                public Integer getTerminalCursorStyle() { return null; }

                @Override
                public void logVerbose(String tag, String message) { LogViewerService.getInstance().d(tag, message); }

                @Override
                public void logDebug(String tag, String message) { LogViewerService.getInstance().d(tag, message); }

                @Override
                public void logInfo(String tag, String message) { LogViewerService.getInstance().i(tag, message); }

                @Override
                public void logWarn(String tag, String message) { LogViewerService.getInstance().w(TAG, message); }

                @Override
                public void logError(String tag, String message) { LogViewerService.getInstance().e(TAG, message); }

                @Override
                public void logStackTraceWithMessage(String tag, String message, Exception e) { LogViewerService.getInstance().e(TAG, message, e); }

                @Override
                public void logStackTrace(String tag, Exception e) { LogViewerService.getInstance().e(TAG, "Terminal error", e); }
            };

            LogViewerService.getInstance().i(TAG, "Starting global persistent TerminalSession from TerminalSessionManager - Shell: " + shellPath + ", CWD: " + cwd);
            mTerminalSession = new TerminalSession(
                    shellPath,
                    cwd,
                    args,
                    envVars,
                    10000,
                    sessionClient
            );
            // Eagerly initialize TerminalEmulator so background execution/reading works prior to UI attachment
            mTerminalSession.updateSize(80, 24, 0, 0);
            // Immediately initialize emulator with standard 80x24 terminal dimensions
            mTerminalSession.updateSize(80, 24, 0, 0);

        } catch (Throwable t) {
            LogViewerService.getInstance().e(TAG, "Failed to initialize global TerminalSession", t);
            CrashHandler.getInstance().handleException(TAG, "Global TerminalSession init failed", t);
        }
    }

    public synchronized void setTerminalSession(TerminalSession session) {
        this.mTerminalSession = session;
        LogViewerService.getInstance().i(TAG, "Shared TerminalSession registered in TerminalSessionManager.");
    }

    public synchronized TerminalSession getTerminalSession() {
        return mTerminalSession;
    }

    public synchronized boolean isSessionAvailable() {
        return mTerminalSession != null && mTerminalSession.isRunning();
    }

    public synchronized boolean executeCommand(String command) {
        if (!isSessionAvailable()) {
            LogViewerService.getInstance().w(TAG, "Cannot execute command: TerminalSession is not active.");
            return false;
        }

        try {
            String cmdToExecute = command.endsWith("\n") ? command : command + "\n";
            mTerminalSession.write(cmdToExecute.getBytes(StandardCharsets.UTF_8), 0, cmdToExecute.getBytes(StandardCharsets.UTF_8).length);
            LogViewerService.getInstance().i(TAG, "Command written to shared TerminalSession: " + command.trim());
            return true;
        } catch (Exception e) {
            LogViewerService.getInstance().e(TAG, "Failed to write command to TerminalSession", e);
            return false;
        }
    }

    public synchronized String readTerminalOutput(int maxLines, int startLine, boolean tailOnly, String grepPattern) {
        if (!isSessionAvailable()) {
            LogViewerService.getInstance().w(TAG, "Cannot read terminal output: TerminalSession is not active.");
            return "Error: TerminalSession is not active.";
        }

        try {
            com.termux.terminal.TerminalEmulator emulator = mTerminalSession.getEmulator();
            if (emulator == null) {
                return "Error: TerminalEmulator instance is null.";
            }

            com.termux.terminal.TerminalBuffer buffer = emulator.getScreen();
            if (buffer == null) {
                return "Error: TerminalBuffer instance is null.";
            }

            String transcriptText = buffer.getTranscriptTextWithFullLinesJoined();
            if (transcriptText == null) {
                transcriptText = "";
            }

            String[] linesArray = transcriptText.split("\n");
            List<String> rawLines = Arrays.asList(linesArray);

            List<String> filteredLines = new ArrayList<>();
            for (String line : rawLines) {
                String trimmedLine = line.trim();
                if (grepPattern == null || grepPattern.trim().isEmpty() || trimmedLine.contains(grepPattern)) {
                    filteredLines.add(trimmedLine);
                }
            }

            int totalFiltered = filteredLines.size();
            int fromIndex = 0;
            int toIndex = totalFiltered;

            if (tailOnly) {
                fromIndex = Math.max(0, totalFiltered - Math.min(maxLines > 0 ? maxLines : 50, totalFiltered));
            } else {
                if (startLine > 0 && startLine < totalFiltered) {
                    fromIndex = startLine;
                }
                if (maxLines > 0) {
                    toIndex = Math.min(totalFiltered, fromIndex + maxLines);
                }
            }

            List<String> resultSubList = filteredLines.subList(fromIndex, Math.min(fromIndex + (toIndex - fromIndex), totalFiltered));
            StringBuilder sb = new StringBuilder();
            sb.append("[Terminal Buffer Fragment - Total Lines: ").append(totalFiltered)
              .append(", Returned Lines: ").append(resultSubList.size()).append("]\n");

            for (String line : resultSubList) {
                sb.append(line).append("\n");
            }

            LogViewerService.getInstance().d(TAG, "Read terminal output fragment: " + resultSubList.size() + " lines.");
            return sb.toString();

        } catch (Exception e) {
            LogViewerService.getInstance().e(TAG, "Error reading terminal output buffer", e);
            return "Error reading terminal buffer: " + e.getMessage();
        }
    }
}
