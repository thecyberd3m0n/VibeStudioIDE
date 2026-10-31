package com.vibestudio.app.logging;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

/**
 * Bounded, thread-safe log channel for a single PTY session. Records what is written to the PTY (IN),
 * what the PTY prints (OUT), and structured command records (CMD / EXIT / EVENT). Secrets are redacted
 * before anything is stored.
 */
public class SessionLogger {

    public static final int DEFAULT_MAX_BYTES = 256 * 1024;
    private static final int MAX_PENDING_CHARS = 8 * 1024;
    private static final String INTERNAL_MARKER = "___CMD_";

    private static final Pattern URL_CREDENTIALS = Pattern.compile("https?://[^:@\\s]+:[^@\\s]+@");
    private static final Pattern TOKENS = Pattern.compile(
            "(gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|glpat-[A-Za-z0-9_\\-]{16,})");
    private static final Pattern SSH_PASSPHRASE = Pattern.compile("(ssh-keygen\\b[^\\n;&|]*?\\s-N\\s+)('[^']*'|\"[^\"]*\"|\\S+)");

    public interface Listener {
        void onEntry(String entry);

        void onCleared();
    }

    private final String mLabel;
    private final int mMaxBytes;
    private final Deque<String> mEntries = new ArrayDeque<>();
    private int mCurrentBytes = 0;
    private final List<Listener> mListeners = new CopyOnWriteArrayList<>();

    private final StreamAssembler mStdin = new StreamAssembler("IN ");
    private final StreamAssembler mStdout = new StreamAssembler("OUT");

    public SessionLogger(String label) {
        this(label, DEFAULT_MAX_BYTES);
    }

    public SessionLogger(String label, int maxBytes) {
        mLabel = label;
        mMaxBytes = maxBytes;
    }

    public String getLabel() {
        return mLabel;
    }

    public void addListener(Listener listener) {
        if (listener != null && !mListeners.contains(listener)) mListeners.add(listener);
    }

    public void removeListener(Listener listener) {
        mListeners.remove(listener);
    }

    // --- Raw PTY tap ---

    public void onStdin(byte[] data, int offset, int count) {
        mStdin.append(data, offset, count);
    }

    public void onStdout(byte[] data, int offset, int count) {
        mStdout.append(data, offset, count);
    }

    /** Emits any partially received line (e.g. a shell prompt that has no trailing newline). */
    public void flush() {
        mStdin.flush();
        mStdout.flush();
    }

    // --- Structured records ---

    public void command(String command) {
        add("CMD", redact(command));
    }

    public void exit(String message) {
        flush();
        add("EXIT", message);
    }

    public void event(String message) {
        add("EVT", message);
    }

    public void event(String message, Throwable t) {
        add("EVT", message + (t != null ? " (" + t + ")" : ""));
    }

    // --- Access ---

    public synchronized List<String> getEntries() {
        return new ArrayList<>(mEntries);
    }

    public synchronized String getAllText() {
        StringBuilder sb = new StringBuilder();
        for (String entry : mEntries) sb.append(entry).append('\n');
        return sb.toString();
    }

    public void clear() {
        synchronized (this) {
            mEntries.clear();
            mCurrentBytes = 0;
        }
        for (Listener l : mListeners) l.onCleared();
    }

    // --- Helpers ---

    public static String redact(String text) {
        if (text == null) return "";
        String s = URL_CREDENTIALS.matcher(text).replaceAll("******");
        s = TOKENS.matcher(s).replaceAll("******");
        s = SSH_PASSPHRASE.matcher(s).replaceAll("$1******");
        return s;
    }

    public static String stripAnsi(String text) {
        if (text == null) return "";
        String s = text.replaceAll("\033\\][^\007\033]*(\007|\033\\\\)", "");
        s = s.replaceAll("\033\\[[0-9;?]*[ -/]*[@-~]", "");
        s = s.replaceAll("\033[()][A-Z0-9]", "");
        s = s.replaceAll("\033[=>]", "");
        return s;
    }

    private void add(String kind, String text) {
        String entry = formatEntry(kind, text);
        synchronized (this) {
            int bytes = entry.getBytes(StandardCharsets.UTF_8).length + 1;
            while (!mEntries.isEmpty() && mCurrentBytes + bytes > mMaxBytes) {
                mCurrentBytes -= mEntries.removeFirst().getBytes(StandardCharsets.UTF_8).length + 1;
            }
            mEntries.addLast(entry);
            mCurrentBytes += bytes;
        }
        for (Listener l : mListeners) l.onEntry(entry);
    }

    private static String formatEntry(String kind, String text) {
        String ts = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date());
        return "[" + ts + "] " + kind + " " + text;
    }

    /** Turns a raw byte stream into clean, redacted text lines. */
    private final class StreamAssembler {
        private final String mKind;
        private final ByteArrayOutputStream mPending = new ByteArrayOutputStream();

        StreamAssembler(String kind) {
            mKind = kind;
        }

        synchronized void append(byte[] data, int offset, int count) {
            if (data == null || count <= 0) return;
            List<String> lines = new ArrayList<>();
            for (int i = offset; i < offset + count; i++) {
                byte b = data[i];
                if (b == '\n') {
                    lines.add(takePending());
                } else {
                    mPending.write(b);
                    if (mPending.size() >= MAX_PENDING_CHARS) lines.add(takePending());
                }
            }
            for (String line : lines) emit(line);
        }

        synchronized void flush() {
            if (mPending.size() > 0) emit(takePending());
        }

        private String takePending() {
            String s = new String(mPending.toByteArray(), StandardCharsets.UTF_8);
            mPending.reset();
            return s;
        }

        private void emit(String rawLine) {
            // A carriage return rewrites the line (progress output); keep the last non-empty segment.
            String line = "";
            for (String segment : rawLine.split("\r")) {
                if (!stripAnsi(segment).trim().isEmpty()) line = segment;
            }
            line = stripAnsi(line).replaceAll("[\\x00-\\x08\\x0B-\\x1F\\x7F]", "").trim();
            if (line.isEmpty() || line.contains(INTERNAL_MARKER)) return;
            add(mKind, redact(line));
        }
    }
}
