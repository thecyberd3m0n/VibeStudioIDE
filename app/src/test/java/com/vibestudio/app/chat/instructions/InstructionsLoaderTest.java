package com.vibestudio.app.chat.instructions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class InstructionsLoaderTest {
    private File mWorkspace;

    @Before
    public void setUp() {
        mWorkspace = new File(System.getProperty("java.io.tmpdir"), "vibe_instr_test_" + System.nanoTime());
        assertTrue(mWorkspace.mkdirs());
    }

    @After
    public void tearDown() {
        delete(mWorkspace);
    }

    private static void delete(File f) {
        File[] children = f.listFiles();
        if (children != null) for (File c : children) delete(c);
        f.delete();
    }

    private File write(String rel, String content) throws IOException {
        File f = new File(mWorkspace, rel);
        assertTrue(f.getParentFile().isDirectory() || f.getParentFile().mkdirs());
        try (FileWriter w = new FileWriter(f)) {
            w.write(content);
        }
        return f;
    }

    @Test
    public void emptyWhenNothingPresentOrNoWorkspace() {
        assertEquals("", InstructionsLoader.buildInstructions(null));
        assertEquals("", InstructionsLoader.buildInstructions(mWorkspace.getAbsolutePath()));
    }

    @Test
    public void loadsAlwaysOnFiles() throws IOException {
        write(".github/copilot-instructions.md", "Use tabs.");
        write("AGENTS.md", "Run ./test.sh before committing.");
        String out = InstructionsLoader.buildInstructions(mWorkspace.getAbsolutePath());
        assertTrue(out.contains("WORKSPACE INSTRUCTIONS"));
        assertTrue(out.contains("Use tabs."));
        assertTrue(out.contains("Run ./test.sh before committing."));
        assertFalse(out.contains("FILE-SCOPED"));
    }

    @Test
    public void applyToSplitsAlwaysOnFromScoped() throws IOException {
        write(".github/instructions/all.instructions.md", "---\napplyTo: \"**\"\n---\nGlobal rule.");
        write(".github/instructions/java.instructions.md", "---\napplyTo: \"**/*.java\"\n---\nJava rule.");
        String out = InstructionsLoader.buildInstructions(mWorkspace.getAbsolutePath());
        int scopedIdx = out.indexOf("FILE-SCOPED");
        assertTrue(scopedIdx > 0);
        assertTrue(out.indexOf("Global rule.") < scopedIdx);
        assertTrue(out.indexOf("Java rule.") > scopedIdx);
        assertTrue(out.contains("applies to: **/*.java"));
    }

    @Test
    public void claudeRulesUsePathsKey() throws IOException {
        write(".claude/rules/web.md", "---\npaths:\n  - \"src/web/**\"\n---\nWeb rule.");
        String out = InstructionsLoader.buildInstructions(mWorkspace.getAbsolutePath());
        assertTrue(out.contains("applies to: src/web/**"));
    }

    @Test
    public void duplicateSymlinkedFileIsIncludedOnce() throws IOException {
        File agents = write("AGENTS.md", "Shared rules.");
        Files.createSymbolicLink(new File(mWorkspace, "CLAUDE.md").toPath(), agents.toPath());
        String out = InstructionsLoader.buildInstructions(mWorkspace.getAbsolutePath());
        assertEquals(out.indexOf("Shared rules."), out.lastIndexOf("Shared rules."));
    }

    @Test
    public void symlinkEscapingWorkspaceIsIgnored() throws IOException {
        File outside = File.createTempFile("outside", ".md");
        try {
            try (FileWriter w = new FileWriter(outside)) {
                w.write("SECRET");
            }
            Files.createSymbolicLink(new File(mWorkspace, "AGENTS.md").toPath(), outside.toPath());
            assertFalse(InstructionsLoader.buildInstructions(mWorkspace.getAbsolutePath()).contains("SECRET"));
        } finally {
            outside.delete();
        }
    }

    @Test
    public void oversizedFilesAreTruncated() throws IOException {
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < InstructionsLoader.MAX_FILE_CHARS * 2; i++) big.append('x');
        write("AGENTS.md", big.toString());
        String out = InstructionsLoader.buildInstructions(mWorkspace.getAbsolutePath());
        assertTrue(out.length() < InstructionsLoader.MAX_FILE_CHARS + 500);
        assertTrue(out.contains("[... truncated ...]"));
    }
}
