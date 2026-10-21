package com.vibestudio.app.chat.safety;

import com.vibestudio.app.service.LogViewerService;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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
    }

    private final Map<String, FileChangeRecord> mTrackedChanges = Collections.synchronizedMap(new HashMap<>());
    private final File mBackupDir;

    public WorkspaceChangesetTracker(File cacheDir) {
        mBackupDir = new File(cacheDir, "agent_backups");
        if (!mBackupDir.exists()) {
            mBackupDir.mkdirs();
        }
    }

    public void recordPreEditState(String path) {
        if (path == null || path.trim().isEmpty()) return;
        File targetFile = new File(path);

        if (mTrackedChanges.containsKey(path)) {
            // Pre-edit state already captured for this changeset round
            return;
        }

        if (!targetFile.exists()) {
            mTrackedChanges.put(path, new FileChangeRecord(path, ChangeType.CREATED, null));
        } else {
            File backup = createBackup(targetFile);
            mTrackedChanges.put(path, new FileChangeRecord(path, ChangeType.MODIFIED, backup));
        }
    }

    public void recordPreDeleteState(String path) {
        if (path == null || path.trim().isEmpty()) return;
        File targetFile = new File(path);
        if (!targetFile.exists() || mTrackedChanges.containsKey(path)) return;

        File backup = createBackup(targetFile);
        mTrackedChanges.put(path, new FileChangeRecord(path, ChangeType.DELETED, backup));
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
        synchronized (mTrackedChanges) {
            return new ArrayList<>(mTrackedChanges.values());
        }
    }

    public boolean rollbackAll() {
        boolean success = true;
        synchronized (mTrackedChanges) {
            for (FileChangeRecord record : mTrackedChanges.values()) {
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
