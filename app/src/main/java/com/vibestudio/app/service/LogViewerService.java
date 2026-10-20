package com.vibestudio.app.service;

import android.os.Process;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

public class LogViewerService {

    private static final String TAG = "LogViewerService";
    private static final int MAX_LOG_SIZE_BYTES = 10 * 1024; // 10 KB limit

    private static LogViewerService sInstance;

    private final Deque<String> mLogBuffer = new ArrayDeque<>();
    private int mCurrentSizeBytes = 0;
    private final List<OnLogListener> mListeners = new ArrayList<>();

    private Thread mLogcatThread;
    private volatile boolean mIsLogcatRunning = false;

    public interface OnLogListener {
        void onLogAdded(String logEntry);
        void onLogsCleared();
    }

    private LogViewerService() {}

    public static synchronized LogViewerService getInstance() {
        if (sInstance == null) {
            sInstance = new LogViewerService();
        }
        return sInstance;
    }

    public synchronized void startLogcatCapture() {
        if (mIsLogcatRunning) {
            return;
        }
        mIsLogcatRunning = true;

        mLogcatThread = new Thread(() -> {
            BufferedReader reader = null;
            java.lang.Process process = null;
            try {
                int pid = Process.myPid();
                // Filter logcat by app process ID
                String[] command = new String[] { "logcat", "-v", "time", "--pid=" + pid };
                process = Runtime.getRuntime().exec(command);
                reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

                String line;
                while (mIsLogcatRunning && (line = reader.readLine()) != null) {
                    appendRawLog(line);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error reading logcat", e);
            } finally {
                if (reader != null) {
                    try { reader.close(); } catch (Exception ignored) {}
                }
                if (process != null) {
                    process.destroy();
                }
            }
        }, "LogcatCaptureThread");

        mLogcatThread.setDaemon(true);
        mLogcatThread.start();
    }

    public synchronized void stopLogcatCapture() {
        mIsLogcatRunning = false;
        if (mLogcatThread != null) {
            mLogcatThread.interrupt();
            mLogcatThread = null;
        }
    }

    private synchronized void appendRawLog(String entry) {
        int entryBytes = entry.getBytes().length + 1; // including newline

        while (!mLogBuffer.isEmpty() && (mCurrentSizeBytes + entryBytes > MAX_LOG_SIZE_BYTES)) {
            String removed = mLogBuffer.removeFirst();
            mCurrentSizeBytes -= (removed.getBytes().length + 1);
        }

        mLogBuffer.addLast(entry);
        mCurrentSizeBytes += entryBytes;

        notifyLogAdded(entry);
    }

    public synchronized void log(String level, String tag, String message) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);
        String timestamp = sdf.format(new Date());
        String entry = String.format("[%s] [%s/%s]: %s", timestamp, level, tag, message);

        Log.println(getPriority(level), tag, message);

        // If logcat capture isn't running, append directly to buffer so logs aren't lost
        if (!mIsLogcatRunning) {
            appendRawLog(entry);
        }
    }

    public synchronized void i(String tag, String message) {
        log("INFO", tag, message);
    }

    public synchronized void d(String tag, String message) {
        log("DEBUG", tag, message);
    }

    public synchronized void w(String tag, String message) {
        log("WARN", tag, message);
    }

    public synchronized void w(String tag, String message, Throwable t) {
        String msg = message + (t != null ? "\n" + Log.getStackTraceString(t) : "");
        log("WARN", tag, msg);
    }

    public synchronized void e(String tag, String message) {
        log("ERROR", tag, message);
    }

    public synchronized void e(String tag, String message, Throwable t) {
        String msg = message + (t != null ? "\n" + Log.getStackTraceString(t) : "");
        log("ERROR", tag, msg);
    }

    public synchronized String getAllLogs() {
        StringBuilder sb = new StringBuilder();
        for (String entry : mLogBuffer) {
            sb.append(entry).append("\n");
        }
        return sb.toString();
    }

    public synchronized void clear() {
        mLogBuffer.clear();
        mCurrentSizeBytes = 0;
        // Also clear logcat buffer for process
        try {
            Runtime.getRuntime().exec("logcat -c");
        } catch (Exception ignored) {}
        notifyLogsCleared();
    }

    public synchronized void addListener(OnLogListener listener) {
        if (listener != null && !mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }

    public synchronized void removeListener(OnLogListener listener) {
        mListeners.remove(listener);
    }

    private void notifyLogAdded(String entry) {
        for (OnLogListener listener : new ArrayList<>(mListeners)) {
            listener.onLogAdded(entry);
        }
    }

    private void notifyLogsCleared() {
        for (OnLogListener listener : new ArrayList<>(mListeners)) {
            listener.onLogsCleared();
        }
    }

    private int getPriority(String level) {
        switch (level) {
            case "DEBUG": return Log.DEBUG;
            case "INFO": return Log.INFO;
            case "WARN": return Log.WARN;
            case "ERROR": return Log.ERROR;
            default: return Log.VERBOSE;
        }
    }
}
