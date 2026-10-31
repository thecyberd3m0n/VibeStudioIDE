package com.vibestudio.app.git;

import static org.junit.assertEquals;
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
        assertNull(GitManager.parseRepositoryState(repo, "").operationInProgress);

        assertTrue(new File(gitDir, "MERGE_HEAD").createNewFile());
        assertEquals("merge", GitManager.parseRepositoryState(repo, "").operationInProgress);
        assertTrue(new File(gitDir, "MERGE_HEAD").delete());

        assertTrue(new File(gitDir, "rebase-merge").mkdir());
        assertEquals("rebase", GitManager.parseRepositoryState(repo, "").operationInProgress);
    }

    @Test
    public void onlyUnmergedStatusCodesAreConflicts() throws IOException {
        File repo = temporaryFolder.newFolder("repo3");
        for (String xy : new String[]{"DD", "AU", "UD", "UA", "DU", "AA", "UU"}) {
            GitRepositoryState state = GitManager.parseRepositoryState(repo, "main\n===AHEAD_BEHIND===\n0 0\n===STATUS===\n" + xy + " f.txt\n");
            assertEquals(xy, 1, state.conflictedFiles.size());
        }
        for (String xy : new String[]{" M", "M ", "A ", "??", "D ", " D", "R "}) {
            GitRepositoryState state = GitManager.parseRepositoryState(repo, "main\n===AHEAD_BEHIND===\n0 0\n===STATUS===\n" + xy + " f.txt\n");
            assertEquals(xy, 0, state.conflictedFiles.size());
        }
    }

    @Test
    public void recognizesMergeConflictOutput() {
        assertTrue(GitManager.isMergeConflictOutput("CONFLICT (content): Merge conflict in f.txt\nAutomatic merge failed; fix conflicts and then commit the result."));
        assertFalse(GitManager.isMergeConflictOutput("Already up to date."));
        assertFalse(GitManager.isMergeConflictOutput(null));
    }
}
