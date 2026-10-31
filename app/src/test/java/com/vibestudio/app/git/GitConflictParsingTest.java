package com.vibestudio.app.git;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;

public class GitConflictParsingTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static final String OUTPUT = "main\n===AHEAD_BEHIND===\n0 0\n===STATUS===\n"
            + "UU f.txt\nAA both.txt\nDU gone.txt\n M edited.txt\nM  staged.txt\n?? new.txt\n"
            + "===NUMSTAT_STAGED===\n===NUMSTAT_UNSTAGED===\n";

    @Test
    public void conflictedFilesAreSeparatedFromNormalChanges() throws IOException {
        File repo = temporaryFolder.newFolder("repo");
        GitRepositoryState state = GitManager.parseRepositoryState(repo, OUTPUT);

        assertTrue(state.hasConflicts());
        assertEquals(3, state.conflictedFiles.size());
        assertEquals("UU", state.conflictedFiles.get(0).status);
        assertEquals("f.txt", state.conflictedFiles.get(0).filePath);
        assertEquals(1, state.stagedFiles.size());
        assertEquals(2, state.unstagedFiles.size());
    }

    @Test
    public void detectsOperationInProgress() throws IOException {
        File repo = temporaryFolder.newFolder("repo2");
        File gitDir = new File(repo, ".git");
        assertTrue(gitDir.mkdir());
        assertNull(GitManager.detectOperationInProgress(repo));

        assertTrue(new File(gitDir, "MERGE_HEAD").createNewFile());
        assertEquals("merge", GitManager.detectOperationInProgress(repo));
        assertTrue(new File(gitDir, "MERGE_HEAD").delete());

        assertTrue(new File(gitDir, "rebase-merge").mkdir());
        assertEquals("rebase", GitManager.detectOperationInProgress(repo));

        GitRepositoryState state = GitManager.parseRepositoryState(repo, "");
        assertEquals("rebase", state.operationInProgress);
    }

    @Test
    public void conflictStatusCodes() {
        for (String xy : new String[]{"DD", "AU", "UD", "UA", "DU", "AA", "UU"}) {
            assertTrue(xy, GitManager.isConflictStatus(xy.substring(0, 1), xy.substring(1)));
        }
        for (String xy : new String[]{" M", "M ", "A ", "??", "D ", " D", "R "}) {
            assertFalse(xy, GitManager.isConflictStatus(xy.substring(0, 1), xy.substring(1)));
        }
    }

    @Test
    public void recognizesMergeConflictOutput() {
        assertTrue(GitManager.isMergeConflictOutput("CONFLICT (content): Merge conflict in f.txt\nAutomatic merge failed; fix conflicts and then commit the result."));
        assertFalse(GitManager.isMergeConflictOutput("Already up to date."));
        assertFalse(GitManager.isMergeConflictOutput(null));
    }
}
