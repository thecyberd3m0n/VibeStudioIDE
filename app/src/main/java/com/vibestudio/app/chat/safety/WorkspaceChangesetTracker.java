package com.vibestudio.app.chat.safety;

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

        public int[] getLineStats() {
            return WorkspaceChangesetTracker.getLineStats(this);
        }
    }

    private final Map<String, FileChangeRecord> mTrackedChanges = Collections.synchronizedMap(new LinkedHashMap<>());
    private final File mBackupDir;

    public WorkspaceChangesetTracker(File cacheDir) {
        mBackupDir = new File(cacheDir, "agent_backups");
        if (!mBackupDir.exists()) {
            mBackupDir.mkdirs();
        }
    }

    private String normalizePath(String rawPath) {
        if (rawPath == null || rawPath.trim().isEmpty()) return "";
        File file = new File(rawPath);
        try {
            return file.getCanonicalPath();
        } catch (IOException e) {
            return file.getAbsolutePath();
        }
    }

    public void recordPreEditState(String rawPath) {
        String path = normalizePath(rawPath);
        if (path.isEmpty()) return;
        File targetFile = new File(path);

        if (mTrackedChanges.containsKey(path)) {
            // Pre-edit state already captured for this changeset round
            return;
        }

        if (!targetFile.exists()) {
            mTrackedChanges.put(path, new FileChangeRecord(path, ChangeType.CREATED, createEmptyTempFile()));
        } else {
            File backup = createBackup(targetFile);
            mTrackedChanges.put(path, new FileChangeRecord(path, ChangeType.MODIFIED, backup));
        }
    }

    public void recordPreDeleteState(String rawPath) {
        String path = normalizePath(rawPath);
        if (path.isEmpty()) return;
        File targetFile = new File(path);
        if (!targetFile.exists() || mTrackedChanges.containsKey(path)) return;

        File backup = createBackup(targetFile);
        mTrackedChanges.put(path, new FileChangeRecord(path, ChangeType.DELETED, backup));
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
        try {
            File backup = File.createTempFile("bak_", ".tmp", mBackupDir);
            copyFile(targetFile, backup);
            return backup;
        } catch (IOException e) {
            LogViewerService.getInstance().e(TAG, "Failed to create backup for " + targetFile.getAbsolutePath(), e);
            return null;
        }
    }

    public List<FileChangeRecord> getChanges() {
        refreshState();
        synchronized (mTrackedChanges) {
            return new ArrayList<>(mTrackedChanges.values());
        }
    }

    /**
     * Validates and cleans up stale/resolved tracked file changes.
     * Checks if current file content matches the backup content (in MODIFIED case),
     * or if CREATED file was removed, or DELETED file was restored.
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
                        // If file no longer exists, created change is resolved
                        if (!currentFile.exists()) {
                            shouldRemove = true;
                        }
                        break;
                    case DELETED:
                        // If file now exists and matches backup, deleted change is restored/resolved
                        if (currentFile.exists() && backupFile != null && backupFile.exists() && filesAreEqual(currentFile, backupFile)) {
                            shouldRemove = true;
                        }
                        break;
                    case MODIFIED:
                        // If current file matches backup content, modification has been resolved/reverted/accepted to match base
                        if (!currentFile.exists() && (backupFile == null || !backupFile.exists())) {
                            shouldRemove = true;
                        } else if (currentFile.exists() && backupFile != null && backupFile.exists() && filesAreEqual(currentFile, backupFile)) {
                            shouldRemove = true;
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

        int[][] lcs = new int[subN + 1][subM + 1];
        for (int i = 0; i < subN; i++) {
            for (int j = 0; j < subM; j++) {
                if (lines1.get(start + i).equals(lines2.get(start + j))) {
                    lcs[i + 1][j + 1] = lcs[i][j] + 1;
                } else {
                    lcs[i + 1][j + 1] = Math.max(lcs[i + 1][j], lcs[i][j + 1]);
                }
            }
        }

        int i = subN, j = subM;
        int additions = 0;
        int deletions = 0;
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && lines1.get(start + i - 1).equals(lines2.get(start + j - 1))) {
                i--;
                j--;
            } else if (j > 0 && (i == 0 || lcs[i][j - 1] >= lcs[i - 1][j])) {
                additions++;
                j--;
            } else if (i > 0 && (j == 0 || lcs[i][j - 1] < lcs[i - 1][j])) {
                deletions++;
                i--;
            }
        }

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

    public boolean rollbackSingle(FileChangeRecord record) {
        if (record == null) return false;
        boolean success = true;
        synchronized (mTrackedChanges) {
            File target = new File(record.getFilePath());
            switch (record.getChangeType()) {
                case CREATED:
                    if (target.exists()) {
                        if (!target.delete()) success = false;
                    }
                    break;
                case MODIFIED:
                case DELETED:
                    if (record.getBackupFile() != null && record.getBackupFile().exists()) {
                        try {
                            copyFile(record.getBackupFile(), target);
                        } catch (IOException e) {
                            LogViewerService.getInstance().e(TAG, "Failed to restore backup for " + record.getFilePath(), e);
                            success = false;
                        }
                    }
                    break;
            }
            if (record.getBackupFile() != null && record.getBackupFile().exists()) {
                record.getBackupFile().delete();
            }
            mTrackedChanges.remove(record.getFilePath());
        }
        return success;
    }

    public boolean rollbackAll() {
        boolean success = true;
        synchronized (mTrackedChanges) {
            List<FileChangeRecord> list = new ArrayList<>(mTrackedChanges.values());
            for (FileChangeRecord record : list) {
                if (!rollbackSingle(record)) {
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
                    record.getBackupFile().delete();
                }
            }
            mTrackedChanges.clear();
        }
    }

    private static void copyFile(File src, File dst) throws IOException {
        try (FileChannel inChannel = new FileInputStream(src).getChannel();
             FileChannel outChannel = new FileOutputStream(dst).getChannel()) {
            inChannel.transferTo(0, inChannel.size(), outChannel);
        }
    }
}
