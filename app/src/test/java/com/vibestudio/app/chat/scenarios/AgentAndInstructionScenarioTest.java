package com.vibestudio.app.chat.scenarios;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.os.Looper;

import com.vibestudio.app.chat.model.ChatMessage;
import com.vibestudio.app.chat.session.ChatSession;
import com.vibestudio.app.chat.skills.Skill;
import com.vibestudio.app.db.DatabaseHelper;
import com.vibestudio.app.test.ShadowDatabaseHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/**
 * End-to-end scenarios driving a real {@link ChatSession} against a scripted AI provider:
 * workspace instructions, agents invoked by slash command, and the agent tool allow-list.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, shadows = {ShadowDatabaseHelper.class})
public class AgentAndInstructionScenarioTest {
    private static final long TIMEOUT_MS = 15000;

    private Context mContext;
    private File mWorkspace;
    private ScriptedProvider mProvider;
    private ChatSession mSession;

    @Before
    public void setUp() {
        ShadowDatabaseHelper.reset();
        mContext = RuntimeEnvironment.getApplication();
        new DatabaseHelper(mContext).saveApiKey("Gemini", "test-key");

        mWorkspace = new File(System.getProperty("java.io.tmpdir"), "vibe_scenario_" + System.nanoTime());
        assertTrue(mWorkspace.mkdirs());

        mProvider = new ScriptedProvider();
        mSession = new ChatSession(mWorkspace.getAbsolutePath(), ScriptedProvider.newRouter(mProvider));
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

    /** Sends a message and pumps the main looper until the whole agent loop has finished. */
    private void send(String text) throws InterruptedException {
        mSession.addUserMessage(mContext, text);
        long deadline = System.currentTimeMillis() + TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle();
            if (!mSession.isLoading()) return;
            Thread.sleep(20);
        }
        throw new AssertionError("Chat turn did not finish within " + TIMEOUT_MS + "ms");
    }

    private ChatMessage lastMessage() {
        List<ChatMessage> messages = mSession.getMessages();
        return messages.get(messages.size() - 1);
    }

    private boolean historyContains(List<ChatMessage> history, String needle) {
        for (ChatMessage m : history) {
            if (m.getText() != null && m.getText().contains(needle)) return true;
        }
        return false;
    }

    // ---- Workspace instructions ----

    @Test
    public void workspaceInstructionsReachTheSystemPrompt() throws Exception {
        write("AGENTS.md", "Always run ./test.sh before committing.");
        write(".github/instructions/java.instructions.md", "---\napplyTo: \"**/*.java\"\n---\nUse 4-space indentation.");
        mProvider.reply("ok");

        send("hello");

        String sys = mProvider.lastSystemInstruction();
        assertTrue(sys.startsWith("BASE-INSTRUCTION"));
        assertTrue(sys.contains(mWorkspace.getAbsolutePath()));
        assertTrue(sys.contains("Always run ./test.sh before committing."));
        assertTrue(sys.contains("FILE-SCOPED INSTRUCTIONS"));
        assertTrue(sys.contains("Use 4-space indentation."));
        assertEquals("ok", lastMessage().getText());
    }

    @Test
    public void editingInstructionsTakesEffectOnTheNextMessage() throws Exception {
        write("AGENTS.md", "Rule v1.");
        mProvider.reply("a").reply("b");

        send("first");
        write("AGENTS.md", "Rule v2.");
        send("second");

        assertTrue(mProvider.systemInstruction(0).contains("Rule v1."));
        assertTrue(mProvider.systemInstruction(1).contains("Rule v2."));
        assertFalse(mProvider.systemInstruction(1).contains("Rule v1."));
    }

    @Test
    public void workspaceWithoutInstructionFilesAddsNoInstructionSection() throws Exception {
        mProvider.reply("ok");
        send("hello");
        assertFalse(mProvider.lastSystemInstruction().contains("WORKSPACE INSTRUCTIONS"));
    }

    // ---- Agents via slash command ----

    @Test
    public void slashCommandActivatesAgentForThatTurnOnly() throws Exception {
        write(".github/agents/reviewer.agent.md", "---\ndescription: Strict reviewer\n---\nReview ruthlessly.");
        mProvider.reply("first").reply("second");

        send("/reviewer check the diff");
        send("thanks");

        String withAgent = mProvider.systemInstruction(0);
        assertTrue(withAgent.contains("ACTIVE AGENT: reviewer"));
        assertTrue(withAgent.contains("Review ruthlessly."));
        assertTrue(withAgent.contains("Strict reviewer"));

        assertFalse(mProvider.systemInstruction(1).contains("ACTIVE AGENT"));
        assertNull(mSession.getActiveAgent());
    }

    @Test
    public void userMessageIsStoredVerbatimWhenInvokingAnAgent() throws Exception {
        write(".github/agents/reviewer.agent.md", "Review.");
        mProvider.reply("ok");
        send("/reviewer check the diff");

        boolean found = false;
        for (ChatMessage m : mSession.getMessages()) {
            if (m.isUser() && "/reviewer check the diff".equals(m.getText())) found = true;
        }
        assertTrue(found);
    }

    @Test
    public void agentsAppearInSlashAutocompleteWithAgentMarker() throws Exception {
        write(".github/agents/reviewer.agent.md", "---\ndescription: Strict reviewer\n---\nx");
        write(".claude/agents/planner.md", "---\ndescription: Plans work\n---\nx");

        Skill reviewer = null;
        Skill planner = null;
        for (Skill s : mSession.getAvailableSkills()) {
            if (s.getName().equals("reviewer")) reviewer = s;
            if (s.getName().equals("planner")) planner = s;
        }
        assertNotNull(reviewer);
        assertNotNull(planner);
        assertEquals("[agent] Strict reviewer", reviewer.getDescription());
        assertEquals("/planner", planner.getSlashCommand());
    }

    @Test
    public void nonUserInvocableAgentsAndSkillsAreHiddenFromAutocomplete() throws Exception {
        write(".github/agents/internal.agent.md", "---\nuser-invocable: false\n---\nx");
        write(".github/skills/secret/SKILL.md", "---\nuser-invocable: false\n---\nx");

        for (Skill s : mSession.getAvailableSkills()) {
            assertFalse(s.getName().equals("internal"));
            assertFalse(s.getName().equals("secret"));
        }

        mProvider.reply("ok");
        send("/internal do it");
        assertNull(mSession.getActiveAgent());
        assertFalse(mProvider.lastSystemInstruction().contains("ACTIVE AGENT"));
    }

    @Test
    public void skillWithSameNameWinsOverAgent() throws Exception {
        write(".github/agents/deploy.agent.md", "Agent body.");
        write(".github/skills/deploy/SKILL.md", "Skill body.");
        mProvider.reply("ok");

        send("/deploy now");

        assertNull(mSession.getActiveAgent());
        String sys = mProvider.lastSystemInstruction();
        assertFalse(sys.contains("ACTIVE AGENT"));
        assertTrue(sys.contains("Skill body."));
    }

    @Test
    public void unknownSlashCommandDoesNotActivateAnything() throws Exception {
        mProvider.reply("ok");
        send("/nope something");
        assertNull(mSession.getActiveAgent());
        assertFalse(mProvider.lastSystemInstruction().contains("ACTIVE AGENT"));
    }

    @Test
    public void agentStaysActiveAcrossToolLoopIterations() throws Exception {
        write(".github/agents/lister.agent.md", "---\ntools: [read]\n---\nList things.");
        write("note.txt", "x");
        mProvider
                .reply("{\"tool\":\"file_list_directory\",\"args\":{\"path\":\"" + mWorkspace.getAbsolutePath() + "\"}}")
                .reply("done");

        send("/lister go");

        assertEquals(2, mProvider.requestCount());
        assertTrue(mProvider.systemInstruction(0).contains("ACTIVE AGENT: lister"));
        assertTrue(mProvider.systemInstruction(1).contains("ACTIVE AGENT: lister"));
        assertEquals("done", lastMessage().getText());
    }

    // ---- Agent tool allow-list ----

    @Test
    public void toolOutsideAgentAllowListIsBlockedAndModelIsToldWhy() throws Exception {
        write(".github/agents/readonly.agent.md", "---\ntools: [read, search]\n---\nRead only.");
        File victim = new File(mWorkspace, "victim.txt");
        assertTrue(victim.createNewFile());
        mProvider
                .reply("{\"tool\":\"file_delete\",\"args\":{\"path\":\"" + victim.getAbsolutePath() + "\"}}")
                .reply("I could not delete it.");

        send("/readonly delete victim.txt");

        assertTrue("blocked tool must not run", victim.exists());
        assertEquals(2, mProvider.requestCount());
        assertTrue(historyContains(mProvider.history(1), "not permitted for agent 'readonly'"));
        assertEquals("I could not delete it.", lastMessage().getText());
    }

    @Test
    public void toolInsideAgentAllowListRunsNormally() throws Exception {
        write(".github/agents/readonly.agent.md", "---\ntools: [read]\n---\nRead only.");
        write("visible-file.txt", "x");
        mProvider
                .reply("{\"tool\":\"file_list_directory\",\"args\":{\"path\":\"" + mWorkspace.getAbsolutePath() + "\"}}")
                .reply("listed");

        send("/readonly list files");

        assertEquals(2, mProvider.requestCount());
        assertFalse(historyContains(mProvider.history(1), "not permitted"));
        assertTrue(historyContains(mProvider.history(1), "visible-file.txt"));
    }

    @Test
    public void agentWithoutToolsListCanUseAnyTool() throws Exception {
        write(".github/agents/free.agent.md", "Anything goes.");
        File victim = new File(mWorkspace, "victim.txt");
        assertTrue(victim.createNewFile());
        mProvider
                .reply("{\"tool\":\"file_delete\",\"args\":{\"path\":\"" + victim.getAbsolutePath() + "\"}}")
                .reply("deleted");

        send("/free delete victim.txt");

        assertFalse(historyContains(mProvider.history(1), "not permitted"));
        assertFalse(victim.exists());
    }

    @Test
    public void allowListNoLongerAppliesOnceAgentIsDeactivated() throws Exception {
        write(".github/agents/readonly.agent.md", "---\ntools: [read]\n---\nRead only.");
        File victim = new File(mWorkspace, "victim.txt");
        assertTrue(victim.createNewFile());
        mProvider.reply("hi")
                .reply("{\"tool\":\"file_delete\",\"args\":{\"path\":\"" + victim.getAbsolutePath() + "\"}}")
                .reply("deleted");

        send("/readonly hello");
        send("now delete victim.txt");

        assertFalse(victim.exists());
    }

    @Test
    public void blockedToolDoesNotBlockTheSkillCatalogMetaTool() throws Exception {
        write(".github/agents/readonly.agent.md", "---\ntools: []\n---\nNo tools.");
        mProvider
                .reply("{\"tool\":\"get_skill_schema\",\"args\":{\"skill_name\":\"file\"}}")
                .reply("ok");

        send("/readonly hi");

        assertFalse(historyContains(mProvider.history(1), "not permitted"));
    }

    // ---- Error handling ----

    @Test
    public void missingApiKeySurfacesAnErrorMessage() throws Exception {
        ShadowDatabaseHelper.reset();
        send("hello");
        ChatMessage last = lastMessage();
        assertTrue(last.isError());
        assertTrue(last.getText().contains("API Key is not configured"));
        assertEquals(0, mProvider.requestCount());
    }
}
