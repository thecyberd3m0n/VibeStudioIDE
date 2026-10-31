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
import java.nio.file.Files;

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
    public void testIsGitAuthRequiredError() {
        Assert.assertTrue(GitManager.isGitAuthRequiredError("fatal: could not read Username for 'https://github.com': No such device or address"));
        Assert.assertTrue(GitManager.isGitAuthRequiredError("fatal: Authentication failed for 'https://github.com/user/repo.git/'"));
        Assert.assertTrue(GitManager.isGitAuthRequiredError("Username for 'https://github.com':"));
        Assert.assertTrue(GitManager.isGitAuthRequiredError("Password for 'https://user@github.com':"));
        Assert.assertTrue(GitManager.isGitAuthRequiredError("fatal: prompt failed: terminal prompts disabled"));
        Assert.assertTrue(GitManager.isGitAuthRequiredError("HTTP 401 Unauthorized"));
        Assert.assertTrue(GitManager.isGitAuthRequiredError("HTTP 403 Forbidden"));

        Assert.assertFalse(GitManager.isGitAuthRequiredError("Cloning into 'repo'..."));
        Assert.assertFalse(GitManager.isGitAuthRequiredError("fatal: repository 'https://github.com/nonexistent/repo.git/' not found"));
        Assert.assertFalse(GitManager.isGitAuthRequiredError(null));
    }

    @Test
    public void testIsNoUpstreamError() {
        Assert.assertTrue(GitManager.isNoUpstreamError("fatal: The current branch test-branch has no upstream branch."));
        Assert.assertTrue(GitManager.isNoUpstreamError("fatal: No upstream configured for branch 'test-branch'"));
        Assert.assertTrue(GitManager.isNoUpstreamError("To push the current branch and set the remote as upstream, use\n  git push --set-upstream origin test-branch"));

        Assert.assertFalse(GitManager.isNoUpstreamError("Everything up-to-date"));
        Assert.assertFalse(GitManager.isNoUpstreamError(null));
    }

    @Test
    public void testIsGitUserConfiguredWithMockGitconfig() throws IOException {
        File homeDir = new File(context.getFilesDir(), "home");
        if (!homeDir.exists()) homeDir.mkdirs();
        File gitConfigFile = new File(homeDir, ".gitconfig");

        if (gitConfigFile.exists()) gitConfigFile.delete();

        Files.write(gitConfigFile.toPath(), "[user]\n\tname = Vibe Developer\n\temail = dev@vibestudio.app\n".getBytes());
        boolean configured = gitManager.isGitUserConfigured(context);
        Assert.assertTrue("isGitUserConfigured should be true when .gitconfig has name and email", configured);

        gitConfigFile.delete();
    }

    @Test
    public void testIsSshKeyConfiguredWithMockSshDir() throws IOException {
        File homeDir = new File(context.getFilesDir(), "home");
        File sshDir = new File(homeDir, ".ssh");
        if (sshDir.exists()) {
            File[] files = sshDir.listFiles();
            if (files != null) {
                for (File f : files) f.delete();
            }
            sshDir.delete();
        }

        Assert.assertFalse("isSshKeyConfigured should be false when .ssh directory does not exist", gitManager.isSshKeyConfigured(context));

        sshDir.mkdirs();
        File idRsa = new File(sshDir, "id_rsa");
        Files.write(idRsa.toPath(), "-----BEGIN OPENSSH PRIVATE KEY-----\n".getBytes());

        Assert.assertTrue("isSshKeyConfigured should be true when id_rsa exists", gitManager.isSshKeyConfigured(context));

        idRsa.delete();
        sshDir.delete();
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

    @Test
    public void testParseRepositoryStateWithExportLeakedEcho() {
        String leakedOutput = "___CMD_START_12345___\n" +
                "export GIT_TERMINAL_PROMPT=0 GIT_SSH_COMMAND=\"ssh -o StrictHostKeyChecking=no\" ; cd '/data/app' && (git branch --show-current 2>/dev/null || git symbolic-ref --short -q HEAD 2>/dev/null || git rev-parse --short -q HEAD 2>/dev/null || echo \"main\") ; echo \"===STATUS===\" ; git status --porcelain --ignored=no ; echo \"===NUMSTAT_STAGED===\" ; git --no-pager diff --cached --numstat ; echo \"===NUMSTAT_UNSTAGED===\" ; git --no-pager diff --numstat\n" +
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
    }

    @Test
    public void testParseRepositoryStateWithAheadBehind() {
        String leakedOutput = "___CMD_START_12345___\n" +
                "main\n" +
                "===AHEAD_BEHIND===\n" +
                "3\t1\n" +
                "===STATUS===\n" +
                " M README.md\n" +
                "===NUMSTAT_STAGED===\n" +
                "===NUMSTAT_UNSTAGED===\n" +
                "1\t1\tREADME.md\n" +
                "___CMD_END_12345___";

        GitRepositoryState state = GitManager.parseRepositoryState(new File("/tmp"), leakedOutput);
        Assert.assertNotNull(state);
        Assert.assertEquals("main", state.branchName);
        Assert.assertEquals(3, state.ahead);
        Assert.assertEquals(1, state.behind);
        Assert.assertEquals(0, state.stagedFiles.size());
        Assert.assertEquals(1, state.unstagedFiles.size());
    }
}
