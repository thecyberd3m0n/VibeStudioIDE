package com.vibestudio.app.files;

import com.vibestudio.app.R;
import com.vibestudio.app.files.model.DirectoryEntry;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

public class DirectoryEntryTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void testRegularDirectoryIconAndDetails() throws IOException {
        File dir = temporaryFolder.newFolder("regularDir");
        DirectoryEntry entry = new DirectoryEntry(dir);

        Assert.assertEquals("📁", entry.getIconSymbol());
        Assert.assertNull(entry.getIconResId());
        Assert.assertTrue(entry.getDetailsText(null).contains("0 items"));
        Assert.assertFalse(entry.getDetailsText(null).contains("Git Repository"));
    }

    @Test
    public void testGitRepositoryIconAndDetails() throws IOException {
        File repoDir = temporaryFolder.newFolder("gitRepo");
        File gitDir = new File(repoDir, ".git");
        gitDir.mkdirs();

        DirectoryEntry entry = new DirectoryEntry(repoDir);

        Assert.assertNull(entry.getIconSymbol());
        Assert.assertEquals(Integer.valueOf(R.drawable.ic_git_repo), entry.getIconResId());
        Assert.assertTrue(entry.getDetailsText(null).contains("Git Repository"));
    }
}
