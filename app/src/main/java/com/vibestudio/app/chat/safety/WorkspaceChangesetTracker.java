package com.vibestudio.app.chat.safety;

import android.content.Context;

import com.vibestudio.app.mcp.PathResolver;
import com.vibestudio.app.service.LogViewerService;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class WorkspaceChangesetTracker {

    private static final String TAG = "WorkspaceChangesetTracker";

    public enum ChangeType {
        CREATED,
        MODIFIED,
        DELETED
    }

    public static class FileChangeRecord {
        private final String filePath;
        private final ChangeType changeType;
        private final File backupFile;
        private int[] mCachedLineStats;

        public FileChangeRecord(String filePath, ChangeType changeType, File backupFile) {
            this.filePath = filePath;
            this.changeType = changeType;
            this.backupFile = backupFile;
        }

        public String getFilePath() {
            return filePath;
        }

        public ChangeType getChangeType() {
            return changeType;
        }

        public File getBackupFile() {
            return backupFile;
        }

        public synchronized int[] getLineStats() {
            if (mCachedLineStats == null) {
                mCachedLineStats = WorkspaceChangesetTracker.getLineStats(this);
            }
            return mCachedLineStats;
        }
    }

    private final Context mContext;
    private final Map<String, FileChangeRecord> mTrackedChanges = Collections.synchronizedMap(new LinkedHashMap<>());
    private final File mBackupDir;

    public WorkspaceChangesetTracker(Context context) {
        this(context, context != null ? context.getCacheDir() : null);
    }

    public WorkspaceChangesetTracker(Context context, File cacheDir) {
        mContext = context != null ? context.getApplicationContext() : null;
        File baseCache = cacheDir != null ? cacheDir : (context != null ? context.getCacheDir() : new File("."));
        mBackupDir = new File(baseCache, "agent_backups");
        if (!mBackupDir.exists()) {
            mBackupDir.mkdirs();
        }
    }

    public WorkspaceChangesetTracker(File cacheDir) {
        this(null, cacheDir);
    }

    public boolean recordState(String rawPath, boolean isDelete) {
        String path = normalizePath(rawPath);
        if (path.isEmpty()) return false;
        File targetFile = new File(path);

        if (!isDelete && !targetFile.exists()) {
            if (mTrackedChanges.containsKey(path)) return true;
            File emptyFile = createEmptyTempFile();
            if (emptyFile == null) return false;
            mTrackedChanges.put(path, new FileChangeRecord(path, ChangeType.CREATED, emptyFile));
            return true;
        }

        if (!targetFile.exists()) return false;

        if (targetFile.isDirectory()) {
            File[] files = targetFile.listFiles();
            if (files == null) return true;
            boolean ok = true;
            for (File child : files) {
                if (!recordState(child.getAbsolutePath(), isDelete)) ok = false;
            }
            return ok;
        }

        if (mTrackedChanges.containsKey(path)) return true;

        File backup = createBackup(targetFile);
        if (backup == null) {
            LogViewerService.getInstance().e(TAG, "Failed to create backup for " + path);
            return false;
        }

        ChangeType type = isDelete ? ChangeType.DELETED : ChangeType.MODIFIED;
        mTrackedChanges.put(path, new FileChangeRecord(path, type, backup));
        return true;
    }

    public boolean recordPreEditState(String rawPath) {
        return recordState(rawPath, false);
    }

    public boolean recordPreDeleteState(String rawPath) {
        return recordState(rawPath, true);
    }

    public List<FileChangeRecord> getChanges() {
        refreshState();
        synchronized (mTrackedChanges) {
            return new ArrayList<>(mTrackedChanges.values());
        }
    }

    public static int[] getLineStats(FileChangeRecord record) {
        if (record == null) return new int[]{0, 0};
        File currentFile = new File(record.getFilePath());
        File backupFile = record.getBackupFile();

        if (record.getChangeType() == ChangeType.CREATED) {
            int lines = countLines(currentFile);
            return new int[]{lines, 0};
        } else if (record.getChangeType() == ChangeType.DELETED) {
            int lines = countLines(backupFile);
            return new int[]{0, lines};
        } else {
            List<String> lines1 = readLines(backupFile);
            List<String> lines2 = readLines(currentFile);
            return computeDiffStats(lines1, lines2);
        }
    }

    /**
     * Validates and cleans up stale/resolved tracked file changes.
     * Uses strict byte-for-byte comparison as the sole check for MODIFIED/DELETED resolution.
     */
    public void refreshState() {
        synchronized (mTrackedChanges) {
            List<String> keys = new ArrayList<>(mTrackedChanges.keySet());
            for (String key : keys) {
                FileChangeRecord record = mTrackedChanges.get(key);
                if (record == null) continue;

                File currentFile = new File(record.getFilePath());
                File backupFile = record.getBackupFile();

                boolean shouldRemove = false;

                switch (record.getChangeType()) {
                    case CREATED:
                        if (!currentFile.exists()) {
                            shouldRemove = true;
                        }
                        break;
                    case DELETED:
                    case MODIFIED:
                        if (currentFile.exists() && backupFile != null && backupFile.exists()) {
                            if (filesAreEqual(currentFile, backupFile)) {
                                shouldRemove = true;
                            }
                        }
                        break;
                }

                if (shouldRemove) {
                    if (backupFile != null && backupFile.exists()) {
                        backupFile.delete();
                    }
                    mTrackedChanges.remove(key);
                }
            }
        }
    }

    public boolean rollback(FileChangeRecord record) {
        if (record == null) return false;
        boolean success = true;
        synchronized (mTrackedChanges) {
            File target = new File(record.getFilePath());
            switch (record.getChangeType()) {
                case CREATED:
                    if (target.exists()) {
                        success = deleteRecursively(target);
                    }
                    break;
                case MODIFIED:
                case DELETED:
                    File backup = record.getBackupFile();
                    if (backup != null && backup.exists()) {
                        try {
                            File parent = target.getParentFile();
                            if (parent != null && !parent.exists()) {
                                parent.mkdirs();
                            }
                            copyFile(backup, target);
                        } catch (IOException e) {
                            LogViewerService.getInstance().e(TAG, "Failed to restore backup for " + record.getFilePath(), e);
                            success = false;
                        }
                    } else {
                        success = false;
                    }
                    break;
            }
            if (record.getBackupFile() != null && record.getBackupFile().exists()) {
                deleteRecursively(record.getBackupFile());
            }
            mTrackedChanges.remove(record.getFilePath());
        }
        return success;
    }

    public boolean rollbackSingle(FileChangeRecord record) {
        return rollback(record);
    }

    public boolean rollbackAll() {
        boolean success = true;
        synchronized (mTrackedChanges) {
            List<FileChangeRecord> list = new ArrayList<>(mTrackedChanges.values());
            for (FileChangeRecord record : list) {
                if (!rollback(record)) {
                    success = false;
                }
            }
            clear();
        }
        return success;
    }

    public void clear() {
        synchronized (mTrackedChanges) {
            for (FileChangeRecord record : mTrackedChanges.values()) {
                if (record.getBackupFile() != null && record.getBackupFile().exists()) {
                    deleteRecursively(record.getBackupFile());
                }
            }
            mTrackedChanges.clear();
        }
    }

    private String normalizePath(String rawPath) {
        if (rawPath == null || rawPath.trim().isEmpty()) return "";
        File file = PathResolver.resolveFile(rawPath, mContext);
        try {
            return file.getCanonicalPath();
        } catch (IOException e) {
            return file.getAbsolutePath();
        }
    }

    private File createEmptyTempFile() {
        try {
            File emptyFile = File.createTempFile("empty_", ".tmp", mBackupDir);
            try (FileOutputStream fos = new FileOutputStream(emptyFile)) {
                fos.write(new byte[0]);
            }
            return emptyFile;
        } catch (IOException e) {
            LogViewerService.getInstance().e(TAG, "Failed to create empty temp file", e);
            return null;
        }
    }

    private File createBackup(File targetFile) {
        if (targetFile == null || !targetFile.exists() || targetFile.isDirectory()) return null;
        try {
            File backupFile = File.createTempFile("bak_", ".tmp", mBackupDir);
            copyFile(targetFile, backupFile);
            return backupFile;
        } catch (IOException e) {
            LogViewerService.getInstance().e(TAG, "Failed to create backup for " + targetFile.getAbsolutePath(), e);
            return null;
        }
    }

    private static int countLines(File file) {
        if (file == null || !file.exists()) return 0;
        int count = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            while (reader.readLine() != null) {
                count++;
            }
        } catch (IOException e) {
            // ignore
        }
        return count;
    }

    private static List<String> readLines(File file) {
        if (file == null || !file.exists()) return Collections.emptyList();
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException e) {
            // ignore
        }
        return lines;
    }

    public static int[] computeDiffStats(List<String> lines1, List<String> lines2) {
        if (lines1 == null) lines1 = Collections.emptyList();
        if (lines2 == null) lines2 = Collections.emptyList();

        int n = lines1.size();
        int m = lines2.size();

        int start = 0;
        while (start < n && start < m && lines1.get(start).equals(lines2.get(start))) {
            start++;
        }

        int end1 = n - 1;
        int end2 = m - 1;
        while (end1 >= start && end2 >= start && lines1.get(end1).equals(lines2.get(end2))) {
            end1--;
            end2--;
        }

        int subN = end1 - start + 1;
        int subM = end2 - start + 1;

        if (subN <= 0 && subM <= 0) {
            return new int[]{0, 0};
        }
        if (subN <= 0) {
            return new int[]{subM, 0};
        }
        if (subM <= 0) {
            return new int[]{0, subN};
        }

        if (subN > 5000 || subM > 5000) {
            return new int[]{subM, subN};
        }

        int[] prev = new int[subM + 1];
        int[] curr = new int[subM + 1];

        for (int i = 0; i < subN; i++) {
            String s1 = lines1.get(start + i);
            for (int j = 0; j < subM; j++) {
                if (s1.equals(lines2.get(start + j))) {
                    curr[j + 1] = prev[j] + 1;
                } else {
                    curr[j + 1] = Math.max(curr[j], prev[j + 1]);
                }
            }
            System.arraycopy(curr, 0, prev, 0, subM + 1);
        }

        int lcsLength = prev[subM];
        int additions = subM - lcsLength;
        int deletions = subN - lcsLength;

        return new int[]{additions, deletions};
    }

    private static boolean filesAreEqual(File f1, File f2) {
        if (!f1.exists() || !f2.exists()) return false;
        if (f1.length() != f2.length()) return false;

        try (FileInputStream fis1 = new FileInputStream(f1);
             FileInputStream fis2 = new FileInputStream(f2)) {
            byte[] buf1 = new byte[8192];
            byte[] buf2 = new byte[8192];
            int r1, r2;
            while ((r1 = fis1.read(buf1)) != -1) {
                r2 = fis2.read(buf2);
                if (r1 != r2) return false;
                for (int i = 0; i < r1; i++) {
                    if (buf1[i] != buf2[i]) return false;
                }
            }
            return fis2.read(buf2) == -1;
        } catch (IOException e) {
            return false;
        }
    }

    private static boolean deleteRecursively(File fileOrDir) {
        if (fileOrDir == null || !fileOrDir.exists()) return true;
        if (fileOrDir.isDirectory()) {
            File[] children = fileOrDir.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        return fileOrDir.delete();
    }

    private static void copyFile(File src, File dst) throws IOException {
        try (FileChannel inChannel = new FileInputStream(src).getChannel();
             FileChannel outChannel = new FileOutputStream(dst).getChannel()) {
            inChannel.transferTo(0, inChannel.size(), outChannel);
        }
    }
}
