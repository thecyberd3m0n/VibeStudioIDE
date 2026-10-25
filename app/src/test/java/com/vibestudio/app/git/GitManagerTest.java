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

    @Test
    public void testIsAuthorIdentityError() {
        Assert.assertTrue(GitManager.isAuthorIdentityError("*** Please tell me who you are.\nRun\n  git config --global user.email 'you@example.com'"));
        Assert.assertTrue(GitManager.isAuthorIdentityError("fatal: Author identity unknown"));
        Assert.assertTrue(GitManager.isAuthorIdentityError("fatal: unable to auto-detect email address (got 'user@localhost')"));
        Assert.assertTrue(GitManager.isAuthorIdentityError("Please set your user.name and user.email"));

        Assert.assertFalse(GitManager.isAuthorIdentityError("[main 1234567] Commit successful"));
        Assert.assertFalse(GitManager.isAuthorIdentityError("fatal: pathspec 'nonexistent' did not match any files"));
        Assert.assertFalse(GitManager.isAuthorIdentityError(null));
    }

    @Test
    public void testParseRepositoryStateWithLeakedEcho() {
        String leakedOutput = "___CMD_START_12345___\n" +
                "cd '/data/app' && git --no-pager (git branch --show-current 2>/dev/null || git symbolic-ref --short -q HEAD 2>/dev/null || git rev-parse --short -q HEAD 2>/dev/null || echo \"main\") ; echo \"===STATUS===\" ; git status --porcelain ; echo \"===NUMSTAT_STAGED===\" ; git --no-pager diff --cached --numstat ; echo \"===NUMSTAT_UNSTAGED===\" ; git --no-pager diff --numstat\n" +
                "main\n" +
                "===STATUS===\n" +
                " M README.md\n" +
                "===NUMSTAT_STAGED===\n" +
                "===NUMSTAT_UNSTAGED===\n" +
                "1\t1\tREADME.md\n" +
                "___CMD_END_12345___";

        GitRepositoryState state = GitManager.parseRepositoryState(new File("/tmp"), leakedOutput);
        Assert.assertNotNull(state);
        Assert.assertEquals("main", state.branchName);
        Assert.assertEquals(0, state.stagedFiles.size());
        Assert.assertEquals(1, state.unstagedFiles.size());

        GitStatusView.GitFileStatus status = state.unstagedFiles.get(0);
        Assert.assertEquals("M", status.status);
        Assert.assertEquals("README.md", status.filePath);
        Assert.assertEquals(1, status.additions);
        Assert.assertEquals(1, status.deletions);
        Assert.assertEquals(1, state.totalAdditions);
        Assert.assertEquals(1, state.totalDeletions);
    }
}
