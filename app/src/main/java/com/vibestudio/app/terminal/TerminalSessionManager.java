package com.vibestudio.app.terminal;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.system.Os;

import com.termux.terminal.TerminalBuffer;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import com.termux.view.TerminalView;
import com.vibestudio.app.logging.CrashHandler;
import com.vibestudio.app.service.LogViewerService;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public class TerminalSessionManager {

    public interface SessionEventListener {
        void onFileEditRequested(String filePath);
    }

    private static final String TAG = "TerminalSessionManager";
    private static TerminalSessionManager sInstance;

    private final Map<TerminalSession, TerminalView> mViews = new WeakHashMap<>();
    private SessionEventListener mSessionEventListener;

    private TerminalSessionManager() {}

    public void setSessionEventListener(SessionEventListener listener) {
        mSessionEventListener = listener;
    }

    public static synchronized TerminalSessionManager getInstance() {
        if (sInstance == null) {
            sInstance = new TerminalSessionManager();
        }
        return sInstance;
    }

    public synchronized TerminalSession createSession(final Context context) {
        return createSession(context, null);
    }

    public synchronized TerminalSession createSession(final Context context, String initialCwd) {
        if (context == null) return null;

        try {
            File filesDir = context.getApplicationContext().getFilesDir();
            File usrDir = new File(filesDir, "usr");
            File homeDir = new File(filesDir, "home");
            if (!homeDir.exists()) homeDir.mkdirs();

            String[] execDirs = new String[]{"bin", "libexec", "lib/apt/methods", "lib/apt/solvers", "lib/apt/planners"};
            for (String execDirName : execDirs) {
                File dir = new File(usrDir, execDirName);
                if (dir.exists()) {
                    File[] files = dir.listFiles();
                    if (files != null) {
                        for (File f : files) {
                            try { Os.chmod(f.getAbsolutePath(), 0755); } catch (Throwable ignored) {}
                        }
                    }
                }
            }

            File pkgScript = new File(usrDir, "bin/pkg");
            if (pkgScript.exists()) {
                try { Os.chmod(pkgScript.getAbsolutePath(), 0755); } catch (Throwable ignored) {}
                try {
                    byte[] pkgBytes = Files.readAllBytes(pkgScript.toPath());
                    String content = new String(pkgBytes, StandardCharsets.UTF_8);
                    if (content.contains("/data/data/com.termux/files/usr")) {
                        content = content.replace("/data/data/com.termux/files/usr", usrDir.getAbsolutePath());
                        Files.write(pkgScript.toPath(), content.getBytes(StandardCharsets.UTF_8));
                    }
                } catch (Throwable ignored) {}
            }

            File bashFile = new File(usrDir, "bin/bash");
            String shellPath = bashFile.exists() ? bashFile.getAbsolutePath() : "/system/bin/sh";

            String[] envVars = new String[]{
                    "PREFIX=" + usrDir.getAbsolutePath(),
                    "HOME=" + homeDir.getAbsolutePath(),
                    "PATH=" + new File(usrDir, "bin").getAbsolutePath() + ":" + new File(usrDir, "bin/applets").getAbsolutePath() + ":/system/bin:/system/xbin",
                    "LD_LIBRARY_PATH=" + new File(usrDir, "lib").getAbsolutePath(),
                    "LD_PRELOAD=" + new File(usrDir, "lib/libtermux-exec.so").getAbsolutePath(),
                    "TMPDIR=" + new File(usrDir, "tmp").getAbsolutePath(),
                    "TERM=xterm-256color",
                    "LANG=en_US.UTF-8",
                    "APT_CONFIG=" + new File(usrDir, "etc/apt/apt.conf").getAbsolutePath(),
                    "DPKG_ADMINDIR=" + new File(usrDir, "var/lib/dpkg").getAbsolutePath(),
                    "TERMUX_APP_PACKAGE_MANAGER=apt",
                    "TERMUX_MAIN_PACKAGE_FORMAT=debian",
                    "TERMUX_PKG_NO_MIRROR_SELECT=1"
            };

            String cwd = (initialCwd != null && new File(initialCwd).isDirectory()) ? initialCwd : homeDir.getAbsolutePath();
            String[] args = bashFile.exists() ? new String[]{"-bash"} : new String[]{shellPath};

            TerminalSessionClient sessionClient = new TerminalSessionClient() {
                @Override
                public void setTerminalShellPid(TerminalSession session, int pid) {}

                @Override
                public void onTextChanged(TerminalSession changedSession) {
                    TerminalView view;
                    synchronized (TerminalSessionManager.this) {
                        view = mViews.get(changedSession);
                    }
                    if (view != null) {
                        view.post(view::onScreenUpdated);
                    }
                }

                @Override
                public void onTitleChanged(TerminalSession updatedSession) {}

                @Override
                public void onSessionFinished(TerminalSession finishedSession) {
                    LogViewerService.getInstance().w(TAG, "TerminalSession finished");
                    synchronized (TerminalSessionManager.this) {
                        mViews.remove(finishedSession);
                    }
                }

                @Override
                public void onCopyTextToClipboard(TerminalSession session, String text) {
                    if (text == null || text.isEmpty()) return;
                    ClipboardManager clipboard = (ClipboardManager)
                            context.getApplicationContext().getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null) {
                        clipboard.setPrimaryClip(ClipData.newPlainText("terminal", text));
                    }
                }

                @Override
                public void onPasteTextFromClipboard(TerminalSession session) {
                    ClipboardManager clipboard = (ClipboardManager)
                            context.getApplicationContext().getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard == null || !clipboard.hasPrimaryClip()) return;
                    ClipData clip = clipboard.getPrimaryClip();
                    if (clip == null || clip.getItemCount() == 0) return;
                    CharSequence pasted = clip.getItemAt(0).coerceToText(context.getApplicationContext());
                    if (pasted == null || pasted.length() == 0) return;
                    if (session != null && session.getEmulator() != null) {
                        session.getEmulator().paste(pasted.toString());
                    }
                }

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

            @Override
            public void onFileEditRequested(TerminalSession session, String filePath) {
                if (mSessionEventListener != null) {
                    mSessionEventListener.onFileEditRequested(filePath);
                }
            }
        };

            LogViewerService.getInstance().i(TAG, "Creating TerminalSession Shell: " + shellPath);
            TerminalSession session = new TerminalSession(
                    shellPath,
                    cwd,
                    args,
                    envVars,
                    10000,
                    sessionClient
            );
            session.updateSize(80, 24, 0, 0);

            return session;

        } catch (Throwable t) {
            LogViewerService.getInstance().e(TAG, "Failed to initialize TerminalSession", t);
            CrashHandler.getInstance().handleException(TAG, "TerminalSession init failed", t);
            return null;
        }
    }

    public synchronized void registerTerminalView(TerminalSession session, TerminalView view) {
        if (session != null && view != null) {
            mViews.put(session, view);
        }
    }

    public synchronized void unregisterTerminalView(TerminalSession session) {
        if (session != null) {
            mViews.remove(session);
        }
    }

    public synchronized Set<TerminalSession> getSessions() {
        return mViews.keySet();
    }

    public String getForegroundProcessName(TerminalSession session) {
        if (session == null || !session.isRunning()) return "Terminal";

        int pid = session.getPid();
        try {
            File statFile = new File("/proc/" + pid + "/stat");
            if (!statFile.exists()) return "Terminal";
            
            byte[] statBytes = Files.readAllBytes(statFile.toPath());
            String statStr = new String(statBytes, StandardCharsets.UTF_8);
            String[] parts = statStr.split("\\s+");
            if (parts.length >= 8) {
                String tpgid = parts[7];
                if (!"0".equals(tpgid) && !"-1".equals(tpgid)) {
                    File commFile = new File("/proc/" + tpgid + "/comm");
                    if (commFile.exists()) {
                        byte[] commBytes = Files.readAllBytes(commFile.toPath());
                        String commStr = new String(commBytes, StandardCharsets.UTF_8).trim();
                        if (!commStr.isEmpty()) {
                            return commStr;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return "bash";
    }

    public synchronized boolean isSessionAvailable(TerminalSession session) {
        return session != null && session.isRunning();
    }

    public synchronized boolean executeCommand(TerminalSession session, String command) {
        if (session == null || !session.isRunning()) {
            LogViewerService.getInstance().w(TAG, "Cannot execute command: TerminalSession is not active.");
            return false;
        }

        try {
            String cmdToExecute = command.endsWith("\n") ? command : command + "\n";
            session.write(cmdToExecute.getBytes(StandardCharsets.UTF_8), 0, cmdToExecute.getBytes(StandardCharsets.UTF_8).length);
            LogViewerService.getInstance().i(TAG, "Command written to TerminalSession: " + command.trim());
            return true;
        } catch (Exception e) {
            LogViewerService.getInstance().e(TAG, "Failed to write command to TerminalSession", e);
            return false;
        }
    }

    public synchronized boolean sendKeystroke(TerminalSession session, String keystroke) {
        if (session == null || !session.isRunning() || keystroke == null) {
            LogViewerService.getInstance().w(TAG, "Cannot send keystroke: TerminalSession is not active.");
            return false;
        }

        try {
            String dataToSend = keystroke;
            if ("CTRL+C".equalsIgnoreCase(dataToSend) || "CTRL_C".equalsIgnoreCase(dataToSend)) {
                dataToSend = "\u0003";
            } else if ("CTRL+D".equalsIgnoreCase(dataToSend) || "CTRL_D".equalsIgnoreCase(dataToSend)) {
                dataToSend = "\u0004";
            } else if ("CTRL+Z".equalsIgnoreCase(dataToSend) || "CTRL_Z".equalsIgnoreCase(dataToSend)) {
                dataToSend = "\u001A";
            } else if ("ENTER".equalsIgnoreCase(dataToSend) || "RETURN".equalsIgnoreCase(dataToSend)) {
                dataToSend = "\n";
            } else {
                dataToSend = dataToSend.replace("CTRL+C", "\u0003")
                                       .replace("ctrl+c", "\u0003")
                                       .replace("CTRL+D", "\u0004")
                                       .replace("ctrl+d", "\u0004")
                                       .replace("ENTER", "\n")
                                       .replace("enter", "\n");
            }

            if (dataToSend.matches("^(?i)(y|n|yes|no)$")) {
                dataToSend += "\n";
            }

            byte[] bytes = dataToSend.getBytes(StandardCharsets.UTF_8);
            session.write(bytes, 0, bytes.length);
            LogViewerService.getInstance().i(TAG, "Keystroke injected to TerminalSession: " + keystroke);
            return true;
        } catch (Exception e) {
            LogViewerService.getInstance().e(TAG, "Failed to inject keystroke to TerminalSession", e);
            return false;
        }
    }

    public synchronized String readTerminalOutput(TerminalSession session, int maxLines, int startLine, boolean tailOnly, String grepPattern) {
        if (session == null || !session.isRunning()) {
            return "Error: TerminalSession is not active.";
        }

        try {
            TerminalEmulator emulator = session.getEmulator();
            if (emulator == null) return "Error: TerminalEmulator instance is null.";

            TerminalBuffer buffer = emulator.getScreen();
            if (buffer == null) return "Error: TerminalBuffer instance is null.";

            String transcriptText = buffer.getTranscriptTextWithFullLinesJoined();
            if (transcriptText == null) transcriptText = "";

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

            return sb.toString();

        } catch (Exception e) {
            LogViewerService.getInstance().e(TAG, "Error reading terminal output buffer", e);
            return "Error reading terminal buffer: " + e.getMessage();
        }
    }

    public synchronized void closeSession(TerminalSession session) {
        if (session == null) return;
        mViews.remove(session);
        try {
            session.finishIfRunning();
        } catch (Exception ignored) {}
    }
}
