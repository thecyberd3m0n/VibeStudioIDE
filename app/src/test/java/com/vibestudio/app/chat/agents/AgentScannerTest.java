package com.vibestudio.app.chat.agents;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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
import java.util.Arrays;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class AgentScannerTest {
    private File mWorkspace;

    @Before
    public void setUp() {
        mWorkspace = new File(System.getProperty("java.io.tmpdir"), "vibe_agent_test_" + System.nanoTime());
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

    private void write(String rel, String content) throws IOException {
        File f = new File(mWorkspace, rel);
        assertTrue(f.getParentFile().isDirectory() || f.getParentFile().mkdirs());
        try (FileWriter w = new FileWriter(f)) {
            w.write(content);
        }
    }

    @Test
    public void scansGithubAgentsWithFrontmatter() throws IOException {
        write(".github/agents/reviewer.agent.md",
                "---\ndescription: Reviews code\ntools: ['read', 'search']\nmodel: [gpt-x, gpt-y]\n---\nBe strict.");
        List<Agent> agents = AgentScanner.scanAgents(mWorkspace.getAbsolutePath());
        assertEquals(1, agents.size());
        Agent a = agents.get(0);
        assertEquals("reviewer", a.getName());
        assertEquals("Reviews code", a.getDescription());
        assertEquals("Be strict.", a.getInstructions());
        assertEquals(Arrays.asList("read", "search"), a.getTools());
        assertEquals("gpt-x", a.getModel());
        assertTrue(a.isUserInvocable());
        assertTrue(a.isModelInvocable());
    }

    @Test
    public void scansClaudeAgentsAndPlainMdAndCommaSeparatedTools() throws IOException {
        write(".claude/agents/Test Writer.md", "---\nname: Test Writer\ntools: Read, Edit\n---\nWrite tests.");
        Agent a = AgentScanner.findAgent(mWorkspace.getAbsolutePath(), "test-writer");
        assertNotNull(a);
        assertEquals(Arrays.asList("Read", "Edit"), a.getTools());
    }

    @Test
    public void githubAgentOverridesClaudeAgentWithSameName() throws IOException {
        write(".claude/agents/dup.md", "From claude");
        write(".github/agents/dup.agent.md", "From github");
        assertEquals("From github", AgentScanner.findAgent(mWorkspace.getAbsolutePath(), "dup").getInstructions());
    }

    @Test
    public void visibilityFlagsFollowVsCodeRules() throws IOException {
        write(".github/agents/a.agent.md", "---\ninfer: false\nuser-invocable: false\n---\nx");
        write(".github/agents/b.agent.md", "---\ndisable-model-invocation: true\n---\nx");
        Agent a = AgentScanner.findAgent(mWorkspace.getAbsolutePath(), "a");
        Agent b = AgentScanner.findAgent(mWorkspace.getAbsolutePath(), "b");
        assertFalse(a.isModelInvocable());
        assertFalse(a.isUserInvocable());
        assertFalse(b.isModelInvocable());
        assertTrue(b.isUserInvocable());
    }

    @Test
    public void noWorkspaceOrNoAgentsYieldsEmpty() {
        assertTrue(AgentScanner.scanAgents(null).isEmpty());
        assertTrue(AgentScanner.scanAgents(mWorkspace.getAbsolutePath()).isEmpty());
        assertNull(AgentScanner.findAgent(mWorkspace.getAbsolutePath(), "missing"));
    }
}
