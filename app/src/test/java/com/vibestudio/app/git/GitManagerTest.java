package com.vibestudio.app.git;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.IOException;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class GitManagerTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private Context context;
    private GitManager gitManager;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        gitManager = GitManager.getInstance();
    }

    @Test
    public void testIsGitRepository() throws IOException {
        File normalDir = temporaryFolder.newFolder("normal_dir");
        Assert.assertFalse(gitManager.isGitRepository(normalDir));

        File gitDir = temporaryFolder.newFolder("git_dir");
        File dotGit = new File(gitDir, ".git");
        Assert.assertTrue(dotGit.mkdir());

        Assert.assertTrue(gitManager.isGitRepository(gitDir));
    }

    @Test
    public void testGitManagerSingleton() {
        GitManager instance1 = GitManager.getInstance();
        GitManager instance2 = GitManager.getInstance();
        Assert.assertNotNull(instance1);
        Assert.assertSame(instance1, instance2);
    }
}
