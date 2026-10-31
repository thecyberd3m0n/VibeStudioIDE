package com.vibestudio.app.terminal;

import com.vibestudio.app.db.DatabaseHelper;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.system.Os;

import com.termux.terminal.TerminalBuffer;
import com.termux.terminal.TerminalEmulator;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import com.termux.view.TerminalView;
import com.vibestudio.app.logging.CrashHandler;
import com.vibestudio.app.logging.SessionLogger;
import com.vibestudio.app.service.LogViewerService;
import com.vibestudio.app.service.TerminalService;
import java.util.Collections;
import java.util.HashSet;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class TerminalSessionManager {

    public interface SessionEventListener {
        void onFileEditRequested(String filePath);
    }

    public interface CommandCallback {
        void onResult(CommandResult result);
    }

    public static class CommandResult {
        public final int exitCode;
        public final String output;

        public CommandResult(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output != null ? output : "";
        }

        public boolean isSuccess() {
            return exitCode == 0;
        }
    }

    private static final String TAG = "TerminalSessionManager";
    private static TerminalSessionManager sInstance;

    private final Map<TerminalSession, ExecutorService> mSessionExecutors = new WeakHashMap<>();
    private final Map<TerminalSession, Object> mSessionLocks = new WeakHashMap<>();
    private final Map<TerminalSession, TerminalView> mViews = new WeakHashMap<>();
    private final Map<TerminalSession, SessionLogger> mLoggers = Collections.synchronizedMap(new WeakHashMap<>());

    private Object getSessionLock(TerminalSession session) {
        synchronized (mSessionLocks) {
            Object lock = mSessionLocks.get(session);
            if (lock == null) {
                lock = new Object();
                mSessionLocks.put(session, lock);
            }
            return lock;
        }
    }
    private final Set<TerminalSession> mActiveSessions = Collections.synchronizedSet(new HashSet<>());
    private final ExecutorService mCommandExecutor = Executors.newCachedThreadPool();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private Context mAppContext;
    private SessionEventListener mSessionEventListener;

    private TerminalSessionManager() {}

    public void setSessionEventListener(SessionEventListener listener) {
        mSessionEventListener = listener;
    }

    private ExecutorService getSessionExecutor(TerminalSession session) {
        synchronized (mSessionExecutors) {
            ExecutorService executor = mSessionExecutors.get(session);
            if (executor == null) {
                executor = Executors.newSingleThreadExecutor(r -> {
                    Thread t = new Thread(r);
                    t.setName("TerminalSessionCmd-" + session.mHandle);
                    return t;
                });
                mSessionExecutors.put(session, executor);
            }
            return executor;
        }
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
        return createSession(context, initialCwd, null);
    }

    /**
     * Creates a PTY session. When {@code logLabel} is not null the session is a managed (programmatic) session:
     * a {@link SessionLogger} with that label records its stdin/stdout so it can be read in the Log Viewer,
     * and the interactive helpers (readline editing, the {@code edit <file>} hook) are disabled.
     */
    public synchronized TerminalSession createSession(final Context context, String initialCwd, String logLabel) {
        if (context == null) return null;
        DatabaseHelper dbHelper = new DatabaseHelper(context.getApplicationContext());
        if (!dbHelper.isEnvInitialized()) {
            throw new IllegalStateException("Cannot create TerminalSession before environment initialization!");
        }

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
                    "GIT_TERMINAL_PROMPT=0",
                    "GIT_SSH_COMMAND=ssh -o StrictHostKeyChecking=no",
                    "GIT_EDITOR=true",
                    "GIT_MERGE_AUTOEDIT=no",
                    "GIT_SEQUENCE_EDITOR=true",
                    "APT_CONFIG=" + new File(usrDir, "etc/apt/apt.conf").getAbsolutePath(),
                    "DPKG_ADMINDIR=" + new File(usrDir, "var/lib/dpkg").getAbsolutePath(),
                    "TERMUX_APP_PACKAGE_MANAGER=apt",
                    "TERMUX_MAIN_PACKAGE_FORMAT=debian",
                    "TERMUX_PKG_NO_MIRROR_SELECT=1"
            };

            String cwd = (initialCwd != null && new File(initialCwd).isDirectory()) ? initialCwd : homeDir.getAbsolutePath();
            boolean managed = logLabel != null;
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
                    synchronized (changedSession) {
                        changedSession.notifyAll();
                    }
                }

                @Override
                public void onTitleChanged(TerminalSession updatedSession) {}

                @Override
                public void onSessionFinished(TerminalSession finishedSession) {
                    LogViewerService.getInstance().w(TAG, "TerminalSession finished");
                    SessionLogger finishedLogger = mLoggers.get(finishedSession);
                    if (finishedLogger != null) {
                        finishedLogger.exit("Session finished (exit status " + finishedSession.getExitStatus() + ")");
                    }
                    synchronized (TerminalSessionManager.this) {
                        mViews.remove(finishedSession);
                        mActiveSessions.remove(finishedSession);
                        if (mActiveSessions.isEmpty() && mAppContext != null) {
                            TerminalService.stop(mAppContext);
                        }
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
            if (managed) {
                SessionLogger logger = LogViewerService.getInstance().getOrCreateChannel(logLabel);
                mLoggers.put(session, logger);
                session.setEditHookEnabled(false);
                session.setIoTap(new TerminalSession.IoTap() {
                    @Override
                    public void onStdin(byte[] data, int offset, int count) {
                        logger.onStdin(data, offset, count);
                    }

                    @Override
                    public void onStdout(byte[] data, int offset, int count) {
                        logger.onStdout(data, offset, count);
                    }
                });
                logger.event("Starting session: shell=" + shellPath + ", cwd=" + cwd);
            }
            session.initializeEmulator(80, 24, 0, 0);
            SessionLogger startedLogger = mLoggers.get(session);
            if (startedLogger != null) {
                startedLogger.event("Session started (pid=" + session.getPid() + ")");
                // Plain tty echo instead of readline redraws, so the PTY log shows each command verbatim.
                byte[] noEditing = "set +o emacs +o vi\n".getBytes(StandardCharsets.UTF_8);
                session.write(noEditing, 0, noEditing.length);
            }

            mAppContext = context.getApplicationContext();
            mActiveSessions.add(session);
            TerminalService.start(mAppContext);

            return session;

        } catch (Throwable t) {
            LogViewerService.getInstance().e(TAG, "Failed to initialize TerminalSession", t);
            if (logLabel != null) {
                LogViewerService.getInstance().getOrCreateChannel(logLabel).event("Failed to start session", t);
            }
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
            LogViewerService.getInstance().i(TAG, "Command written to TerminalSession: " + SessionLogger.redact(command.trim()));
            return true;
        } catch (Exception e) {
            LogViewerService.getInstance().e(TAG, "Failed to write command to TerminalSession", e);
            return false;
        }
    }


    public synchronized boolean sendKeystroke(TerminalSession session, String keystroke, int keyCode) {
        if (session == null || !session.isRunning()) {
            LogViewerService.getInstance().w(TAG, "Cannot send keystroke: TerminalSession is not active.");
            return false;
        }

        try {
            byte[] bytesToSend = null;

            if (keyCode > 0) {
                bytesToSend = getBytesFromKeyCode(keyCode);
            }

            if ((bytesToSend == null || bytesToSend.length == 0) && keystroke != null && !keystroke.isEmpty()) {
                bytesToSend = convertKeystrokeToBytes(keystroke);
            }

            if (bytesToSend == null || bytesToSend.length == 0) {
                LogViewerService.getInstance().w(TAG, "Empty or unrecognized keystroke.");
                return false;
            }

            session.write(bytesToSend, 0, bytesToSend.length);
            LogViewerService.getInstance().i(TAG, "Keystroke injected to TerminalSession (len=" + bytesToSend.length + "): " + (keystroke != null ? keystroke : "code:" + keyCode));
            return true;
        } catch (Exception e) {
            LogViewerService.getInstance().e(TAG, "Failed to inject keystroke to TerminalSession", e);
            return false;
        }
    }

    private byte[] convertKeystrokeToBytes(String keystroke) {
        if (keystroke == null || keystroke.isEmpty()) return new byte[0];

        String trimmed = keystroke.trim();
        String upper = trimmed.toUpperCase(Locale.ROOT);

        switch (upper) {
            case "UP":
            case "KEY_UP":
            case "UP_ARROW":
            case "ARROW_UP":
                return "\u001B[A".getBytes(StandardCharsets.UTF_8);
            case "DOWN":
            case "KEY_DOWN":
            case "DOWN_ARROW":
            case "ARROW_DOWN":
                return "\u001B[B".getBytes(StandardCharsets.UTF_8);
            case "RIGHT":
            case "KEY_RIGHT":
            case "RIGHT_ARROW":
            case "ARROW_RIGHT":
                return "\u001B[C".getBytes(StandardCharsets.UTF_8);
            case "LEFT":
            case "KEY_LEFT":
            case "LEFT_ARROW":
            case "ARROW_LEFT":
                return "\u001B[D".getBytes(StandardCharsets.UTF_8);
            case "TAB":
            case "KEY_TAB":
                return "\t".getBytes(StandardCharsets.UTF_8);
            case "BACKSPACE":
            case "KEY_BACKSPACE":
            case "DEL":
            case "DELETE":
                return "\u007F".getBytes(StandardCharsets.UTF_8);
            case "ESC":
            case "ESCAPE":
            case "KEY_ESCAPE":
                return "\u001B".getBytes(StandardCharsets.UTF_8);
            case "ENTER":
            case "RETURN":
            case "KEY_ENTER":
                return "\r".getBytes(StandardCharsets.UTF_8);
            case "HOME":
            case "KEY_HOME":
                return "\u001B[H".getBytes(StandardCharsets.UTF_8);
            case "END":
            case "KEY_END":
                return "\u001B[F".getBytes(StandardCharsets.UTF_8);
            case "PAGE_UP":
            case "PAGEUP":
            case "PGUP":
            case "KEY_PAGE_UP":
                return "\u001B[5~".getBytes(StandardCharsets.UTF_8);
            case "PAGE_DOWN":
            case "PAGEDOWN":
            case "PGDN":
            case "KEY_PAGE_DOWN":
                return "\u001B[6~".getBytes(StandardCharsets.UTF_8);
            case "INSERT":
            case "KEY_INSERT":
                return "\u001B[2~".getBytes(StandardCharsets.UTF_8);
            case "SPACE":
                return " ".getBytes(StandardCharsets.UTF_8);
            case "F1": return "\u001BOP".getBytes(StandardCharsets.UTF_8);
            case "F2": return "\u001BOQ".getBytes(StandardCharsets.UTF_8);
            case "F3": return "\u001BOR".getBytes(StandardCharsets.UTF_8);
            case "F4": return "\u001BOS".getBytes(StandardCharsets.UTF_8);
            case "F5": return "\u001B[15~".getBytes(StandardCharsets.UTF_8);
            case "F6": return "\u001B[17~".getBytes(StandardCharsets.UTF_8);
            case "F7": return "\u001B[18~".getBytes(StandardCharsets.UTF_8);
            case "F8": return "\u001B[19~".getBytes(StandardCharsets.UTF_8);
            case "F9": return "\u001B[20~".getBytes(StandardCharsets.UTF_8);
            case "F10": return "\u001B[21~".getBytes(StandardCharsets.UTF_8);
            case "F11": return "\u001B[23~".getBytes(StandardCharsets.UTF_8);
            case "F12": return "\u001B[24~".getBytes(StandardCharsets.UTF_8);
        }

        if (upper.startsWith("CTRL+") || upper.startsWith("CTRL_") || upper.startsWith("^")) {
            String keyPart = upper.replace("CTRL+", "").replace("CTRL_", "").replace("^", "").trim();
            if (keyPart.length() == 1) {
                char ch = keyPart.charAt(0);
                if (ch >= 'A' && ch <= 'Z') {
                    byte ctrlByte = (byte) (ch - 'A' + 1);
                    return new byte[]{ctrlByte};
                }
            }
        }

        if (upper.startsWith("ALT+") || upper.startsWith("ALT_")) {
            String keyPart = keystroke.substring(4).trim();
            if (!keyPart.isEmpty()) {
                byte[] subBytes = convertKeystrokeToBytes(keyPart);
                byte[] result = new byte[1 + subBytes.length];
                result[0] = 0x1B;
                System.arraycopy(subBytes, 0, result, 1, subBytes.length);
                return result;
            }
        }

        String data = keystroke;
        data = data.replace("\\e", "\u001B")
                   .replace("\033", "\u001B")
                   .replace("CTRL+C", "\u0003").replace("ctrl+c", "\u0003")
                   .replace("CTRL+D", "\u0004").replace("ctrl+d", "\u0004")
                   .replace("CTRL+Z", "\u001A").replace("ctrl+z", "\u001A")
                   .replace("ENTER", "\r").replace("enter", "\r")
                   .replace("UP_ARROW", "\u001B[A").replace("DOWN_ARROW", "\u001B[B")
                   .replace("LEFT_ARROW", "\u001B[D").replace("RIGHT_ARROW", "\u001B[C")
                   .replace("TAB", "\t").replace("ESC", "\u001B");

        if (data.contains("\\u") || data.contains("\\U")) {
            data = unescapeJavaString(data);
        }

        if (data.matches("^(?i)(y|n|yes|no)$")) {
            data += "\n";
        }

        return data.getBytes(StandardCharsets.UTF_8);
    }

    private byte[] getBytesFromKeyCode(int keyCode) {
        switch (keyCode) {
            case 19: return "\u001B[A".getBytes(StandardCharsets.UTF_8);
            case 20: return "\u001B[B".getBytes(StandardCharsets.UTF_8);
            case 21: return "\u001B[D".getBytes(StandardCharsets.UTF_8);
            case 22: return "\u001B[C".getBytes(StandardCharsets.UTF_8);
            case 61: return "\t".getBytes(StandardCharsets.UTF_8);
            case 66: return "\r".getBytes(StandardCharsets.UTF_8);
            case 67: return "\u007F".getBytes(StandardCharsets.UTF_8);
            case 111: return "\u001B".getBytes(StandardCharsets.UTF_8);
            case 112: return "\u001B[3~".getBytes(StandardCharsets.UTF_8);
            case 92: return "\u001B[5~".getBytes(StandardCharsets.UTF_8);
            case 93: return "\u001B[6~".getBytes(StandardCharsets.UTF_8);
            case 122: return "\u001B[H".getBytes(StandardCharsets.UTF_8);
            case 123: return "\u001B[F".getBytes(StandardCharsets.UTF_8);
            case 124: return "\u001B[2~".getBytes(StandardCharsets.UTF_8);
            case 62: return " ".getBytes(StandardCharsets.UTF_8);
            default:
                if (keyCode >= 131 && keyCode <= 142) {
                    int fIndex = keyCode - 130;
                    return convertKeystrokeToBytes("F" + fIndex);
                }
                return null;
        }
    }

    private String unescapeJavaString(String str) {
        if (str == null) return null;
        StringBuilder sb = new StringBuilder();
        int len = str.length();
        for (int i = 0; i < len; i++) {
            char ch = str.charAt(i);
            if (ch == '\\' && i + 1 < len) {
                char next = str.charAt(i + 1);
                if ((next == 'u' || next == 'U') && i + 5 < len) {
                    try {
                        String hex = str.substring(i + 2, i + 6);
                        int codePoint = Integer.parseInt(hex, 16);
                        sb.append((char) codePoint);
                        i += 5;
                        continue;
                    } catch (Exception ignored) {}
                } else if (next == 'e' || next == 'E') {
                    sb.append('\u001B');
                    i++;
                    continue;
                } else if (next == 'n') {
                    sb.append('\n');
                    i++;
                    continue;
                } else if (next == 'r') {
                    sb.append('\r');
                    i++;
                    continue;
                } else if (next == 't') {
                    sb.append('\t');
                    i++;
                    continue;
                }
            }
            sb.append(ch);
        }
        return sb.toString();
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

    /** Runs the command on a background thread and delivers the result to the callback on the main thread. */
    public void runCommand(TerminalSession session, String command, int timeoutMs, CommandCallback callback) {
        mCommandExecutor.execute(() -> {
            CommandResult result = runCommandBlocking(session, command, timeoutMs);
            if (callback != null) {
                mMainHandler.post(() -> callback.onResult(result));
            }
        });
    }

    /**
     * Runs the command on the calling thread and waits for it to finish (or time out). Commands on the same
     * session are serialized. Never call this from a thread that the session output depends on.
     */
    public CommandResult runCommandBlocking(TerminalSession session, String command, int timeoutMs) {
        SessionLogger logger = session != null ? mLoggers.get(session) : null;
        String loggedCommand = SessionLogger.redact(command);

        if (session == null || !session.isRunning() || command == null || command.isEmpty()) {
            String reason = session == null ? "no session" : (!session.isRunning() ? "session not running" : "empty command");
            if (logger != null) {
                logger.event("Command rejected (" + reason + "): " + loggedCommand);
            } else {
                LogViewerService.getInstance().w(TAG, "runCommand rejected (" + reason + "): " + loggedCommand);
            }
            return new CommandResult(-1, "Invalid session or command");
        }

        int effectiveTimeout = timeoutMs > 0 ? timeoutMs : 10000;
        synchronized (getSessionLock(session)) {
            long startTime = System.currentTimeMillis();
            String startMarker = "___CMD_START_" + startTime + "_" + ((int) (Math.random() * 10000)) + "___";
            String endMarker = "___CMD_END_" + startTime + "_" + ((int) (Math.random() * 10000)) + "___";

            if (logger != null) logger.command(command);

            String execCmd = "echo " + startMarker + " ; " + command + " ; echo \"Exit code: $?\" ; echo " + endMarker + "\n";
            byte[] bytes = execCmd.getBytes(StandardCharsets.UTF_8);
            session.write(bytes, 0, bytes.length);

            String capturedOutput = null;
            while (System.currentTimeMillis() - startTime < effectiveTimeout) {
                synchronized (session) {
                    try {
                        session.wait(50);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                String transcript = null;
                try {
                    TerminalEmulator emulator = session.getEmulator();
                    TerminalBuffer buffer = emulator != null ? emulator.getScreen() : null;
                    if (buffer != null) transcript = buffer.getTranscriptTextWithFullLinesJoined();
                } catch (Exception ignored) {}
                capturedOutput = extractBetweenMarkers(transcript, startMarker, endMarker);
                if (capturedOutput != null) break;
            }

            long elapsed = System.currentTimeMillis() - startTime;
            if (capturedOutput == null) {
                byte[] ctrlC = new byte[]{3};
                session.write(ctrlC, 0, ctrlC.length);
                String message = "Command timed out after " + elapsed + "ms, sent Ctrl+C: " + loggedCommand;
                if (logger != null) logger.exit(message);
                LogViewerService.getInstance().w(TAG, message);
                return new CommandResult(-1, "Command timed out");
            }

            CommandResult result = parseCapturedOutput(capturedOutput);
            if (logger != null) logger.exit("exit=" + result.exitCode + " (" + elapsed + "ms)");
            return result;
        }
    }

    /**
     * Returns the lines printed between the start and end marker lines, or null if the command has not finished.
     * The echoed command line also contains the markers, so only the line the shell prints for the marker counts.
     */
    static String extractBetweenMarkers(String transcript, String startMarker, String endMarker) {
        if (transcript == null) return null;
        int firstStart = transcript.indexOf(startMarker);
        if (firstStart < 0 || transcript.indexOf(endMarker, firstStart) < 0) return null;

        String[] lines = transcript.substring(firstStart).split("\n");
        int startLineIdx = -1;
        int endLineIdx = -1;
        for (int i = 0; i < lines.length; i++) {
            String cleanLine = SessionLogger.stripAnsi(lines[i]).replaceAll("[\\r\\n]", "").trim();
            if (startLineIdx == -1) {
                // The shell may print its prompt on the same line as the marker output.
                if (cleanLine.endsWith(startMarker) && !cleanLine.contains("echo " + startMarker)) startLineIdx = i;
            } else if (cleanLine.endsWith(endMarker)) {
                endLineIdx = i;
                break;
            }
        }
        if (startLineIdx == -1 || endLineIdx == -1) return null;

        StringBuilder sb = new StringBuilder();
        for (int i = startLineIdx + 1; i < endLineIdx; i++) {
            sb.append(lines[i]);
            if (i < endLineIdx - 1) sb.append("\n");
        }
        return sb.toString();
    }

    static CommandResult parseCapturedOutput(String capturedOutput) {
        int exitCode = 0;
        StringBuilder cleanOutput = new StringBuilder();
        for (String line : capturedOutput.split("\n")) {
            String clean = SessionLogger.stripAnsi(line.trim()).replaceAll("[\\r\\n]", "").trim();
            if (clean.startsWith("Exit code:")) {
                try {
                    exitCode = Integer.parseInt(clean.substring("Exit code:".length()).trim());
                } catch (Exception ignored) {}
            } else {
                cleanOutput.append(line).append("\n");
            }
        }
        return new CommandResult(exitCode, cleanOutput.toString().trim());
    }

    public String getSessionOutput(TerminalSession session) {
        if (session == null || !session.isRunning()) {
            return "(Managed session is inactive)";
        }
        try {
            TerminalEmulator emulator = session.getEmulator();
            if (emulator == null) return "(TerminalEmulator is null)";
            TerminalBuffer buffer = emulator.getScreen();
            if (buffer == null) return "(TerminalBuffer is null)";
            String text = buffer.getTranscriptTextWithFullLinesJoined();
            return text != null && !text.trim().isEmpty() ? text.trim() : "(No stdin/stdout output recorded in PTY)";
        } catch (Exception e) {
            LogViewerService.getInstance().e(TAG, "Error reading session output buffer", e);
            return "Error reading session buffer: " + e.getMessage();
        }
    }

    public synchronized void closeAllSessions() {
        Set<TerminalSession> sessionsToClose = new HashSet<>(mActiveSessions);
        for (TerminalSession session : sessionsToClose) {
            closeSession(session);
        }
    }

    public synchronized void closeSession(TerminalSession session) {
        if (session == null) return;
        mViews.remove(session);
        mActiveSessions.remove(session);
        try {
            session.finishIfRunning();
        } catch (Exception ignored) {}
        if (mActiveSessions.isEmpty() && mAppContext != null) {
            TerminalService.stop(mAppContext);
        }
    }
}
