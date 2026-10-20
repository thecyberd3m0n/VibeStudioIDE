package com.vibestudio.app.git;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;

public class GitTerminalParserTest {

    @Test
    public void testParseUnstagedModification() {
        String output = " M README.md\n===NUMSTAT_STAGED===\n===NUMSTAT_UNSTAGED===\n1\t1\tREADME.md\n";
        GitRepositoryState state = GitTerminalParser.parseRepositoryState(new File("/tmp"), output);

        Assert.assertNotNull(state);
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
    public void testParseStagedModification() {
        String output = "M  README.md\n===NUMSTAT_STAGED===\n5\t2\tREADME.md\n===NUMSTAT_UNSTAGED===\n";
        GitRepositoryState state = GitTerminalParser.parseRepositoryState(new File("/tmp"), output);

        Assert.assertNotNull(state);
        Assert.assertEquals(1, state.stagedFiles.size());
        Assert.assertEquals(0, state.unstagedFiles.size());

        GitStatusView.GitFileStatus status = state.stagedFiles.get(0);
        Assert.assertEquals("M", status.status);
        Assert.assertEquals("README.md", status.filePath);
        Assert.assertEquals(5, status.additions);
        Assert.assertEquals(2, status.deletions);
        Assert.assertEquals(5, state.totalAdditions);
        Assert.assertEquals(2, state.totalDeletions);
    }

    @Test
    public void testCleanTerminalOutput() {
        String raw = "___CMD_START_12345___\n M README.md\n___CMD_END_12345___";
        String cleaned = GitTerminalParser.cleanTerminalOutput(raw);
        Assert.assertEquals("M README.md", cleaned.trim());
    }
}
