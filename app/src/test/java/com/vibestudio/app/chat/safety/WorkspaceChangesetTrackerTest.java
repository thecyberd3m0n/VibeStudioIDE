package com.vibestudio.app.chat.safety;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class WorkspaceChangesetTrackerTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private WorkspaceChangesetTracker mTracker;
    private File mCacheDir;

    @Before
    public void setUp() throws IOException {
        mCacheDir = tempFolder.newFolder("cache");
        mTracker = new WorkspaceChangesetTracker(mCacheDir);
    }

    @Test
    public void testAutoCleanupWhenFileRestoredToBackupContent() throws IOException {
        File file = tempFolder.newFile("test.txt");
        try (FileWriter fw = new FileWriter(file)) {
            fw.write("Initial content\n");
        }

        // Record pre-edit state
        mTracker.recordPreEditState(file.getAbsolutePath());

        // Modify file content
        try (FileWriter fw = new FileWriter(file)) {
            fw.write("Modified content\n");
        }

        List<WorkspaceChangesetTracker.FileChangeRecord> changes = mTracker.getChanges();
        Assert.assertEquals(1, changes.size());

        // Revert file content manually back to original
        try (FileWriter fw = new FileWriter(file)) {
            fw.write("Initial content\n");
        }

        // Fetching changes should automatically refresh state and prune resolved changes
        changes = mTracker.getChanges();
        Assert.assertTrue("Tracker should auto-cleanup change when file content matches base state", changes.isEmpty());
    }

    @Test
    public void testAutoCleanupWhenCreatedFileIsDeleted() throws IOException {
        File newFile = new File(tempFolder.getRoot(), "new_file.txt");
        mTracker.recordPreEditState(newFile.getAbsolutePath());

        // File created
        try (FileWriter fw = new FileWriter(file_path(newFile))) {
            fw.write("New content\n");
        }

        List<WorkspaceChangesetTracker.FileChangeRecord> changes = mTracker.getChanges();
        Assert.assertEquals(1, changes.size());

        // Delete the created file
        newFile.delete();

        changes = mTracker.getChanges();
        Assert.assertTrue("Tracker should auto-cleanup created change when file is deleted", changes.isEmpty());
    }

    private String file_path(File file) {
        return file.getAbsolutePath();
    }
}
