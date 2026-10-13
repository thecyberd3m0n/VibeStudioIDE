package com.vibestudio.app.files;

import com.vibestudio.app.files.action.FileAction;
import com.vibestudio.app.files.action.FileActionRegistry;
import com.vibestudio.app.files.model.DirectoryEntry;
import com.vibestudio.app.files.model.FileEntry;
import com.vibestudio.app.files.model.RegularFileEntry;
import com.vibestudio.app.files.model.UpDirEntry;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class FileActionRegistryTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void testFileActionListForRegularFile() throws IOException {
        File file = temporaryFolder.newFile("test.txt");
        FileEntry entry = new RegularFileEntry(file);

        List<FileAction> actions = FileActionRegistry.getAvailableActionsFor(entry);

        Assert.assertFalse(actions.isEmpty());
        Assert.assertEquals("Share", actions.get(0).getTitle());
    }

    @Test
    public void testFileActionListForDirectory() throws IOException {
        File dir = temporaryFolder.newFolder("testDir");
        FileEntry entry = new DirectoryEntry(dir);

        List<FileAction> actions = FileActionRegistry.getAvailableActionsFor(entry);

        Assert.assertFalse(actions.isEmpty());
        Assert.assertNotEquals("Share", actions.get(0).getTitle());
        for (FileAction action : actions) {
            Assert.assertNotEquals("Share", action.getTitle());
        }
    }

    @Test
    public void testFileActionListForUpDirEntry() throws IOException {
        File dir = temporaryFolder.newFolder("parentDir");
        FileEntry entry = new UpDirEntry(dir);

        List<FileAction> actions = FileActionRegistry.getAvailableActionsFor(entry);

        Assert.assertTrue(actions.isEmpty());
    }
}
