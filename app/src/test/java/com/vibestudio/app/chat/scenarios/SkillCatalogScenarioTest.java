package com.vibestudio.app.chat.scenarios;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.vibestudio.app.mcp.McpClientManager;

import org.json.JSONArray;
import org.json.JSONObject;
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

/** What the model sees in the lazy-loading catalog, and what it gets back when it loads a skill or agent. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, shadows = {com.vibestudio.app.test.ShadowDatabaseHelper.class})
public class SkillCatalogScenarioTest {
    private File mWorkspace;
    private McpClientManager mManager;

    @Before
    public void setUp() {
        mWorkspace = new File(System.getProperty("java.io.tmpdir"), "vibe_catalog_" + System.nanoTime());
        assertTrue(mWorkspace.mkdirs());
        mManager = new McpClientManager();
        mManager.setWorkspacePath(mWorkspace.getAbsolutePath());
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

    private JSONObject catalogEntry(String name) throws Exception {
        JSONArray skills = mManager.getHighLevelCatalog().getJSONArray("available_skills");
        for (int i = 0; i < skills.length(); i++) {
            if (name.equals(skills.getJSONObject(i).optString("name"))) return skills.getJSONObject(i);
        }
        return null;
    }

    @Test
    public void catalogListsBuiltInServersCustomSkillsAndAgents() throws Exception {
        write(".github/skills/lint-fix/SKILL.md", "---\ndescription: Fix lint\n---\nRun linter.");
        write(".agents/skills/release/SKILL.md", "---\ndescription: Cut a release\n---\nTag it.");
        write(".github/agents/reviewer.agent.md", "---\ndescription: Strict reviewer\n---\nReview.");

        assertNotNull(catalogEntry("file"));
        assertEquals("Fix lint", catalogEntry("lint-fix").getString("description"));
        assertNotNull(catalogEntry("release"));

        JSONObject agent = catalogEntry("reviewer");
        assertNotNull(agent);
        assertEquals("agent", agent.getString("type"));
        assertEquals("Strict reviewer", agent.getString("description"));
    }

    @Test
    public void modelInvisibleSkillsAndAgentsAreOmittedFromCatalog() throws Exception {
        write(".github/skills/manual-only/SKILL.md", "---\ndisable-model-invocation: true\n---\nx");
        write(".github/agents/legacy-hidden.agent.md", "---\ninfer: false\n---\nx");
        write(".github/agents/new-hidden.agent.md", "---\ndisable-model-invocation: true\n---\nx");
        write(".github/agents/visible.agent.md", "---\ndescription: Visible\n---\nx");

        assertNull(catalogEntry("manual-only"));
        assertNull(catalogEntry("legacy-hidden"));
        assertNull(catalogEntry("new-hidden"));
        assertNotNull(catalogEntry("visible"));
    }

    @Test
    public void loadingAnAgentReturnsItsInstructionsAndMarksItLoaded() throws Exception {
        write(".github/agents/reviewer.agent.md", "---\ndescription: Strict reviewer\n---\nReview ruthlessly.");

        assertFalse(catalogEntry("reviewer").getBoolean("is_loaded"));
        JSONObject res = mManager.getSkillSchema(RuntimeEnvironment.getApplication(), "reviewer");

        assertEquals("success", res.getString("status"));
        JSONObject schema = res.getJSONObject("schema");
        assertEquals("agent", schema.getString("type"));
        assertEquals("Review ruthlessly.", schema.getString("guidelines"));
        assertTrue(catalogEntry("reviewer").getBoolean("is_loaded"));
    }

    @Test
    public void loadingAModelInvisibleAgentFailsAsUnknown() throws Exception {
        write(".github/agents/hidden.agent.md", "---\ninfer: false\n---\nSecret.");
        JSONObject res = mManager.getSkillSchema(RuntimeEnvironment.getApplication(), "hidden");
        assertEquals("error", res.getString("status"));
    }

    @Test
    public void loadingACustomSkillReturnsItsGuidelines() throws Exception {
        write(".github/skills/lint-fix/SKILL.md", "---\ndescription: Fix lint\n---\nRun linter.");
        JSONObject res = mManager.getSkillSchema(RuntimeEnvironment.getApplication(), "lint-fix");
        assertEquals("success", res.getString("status"));
        assertEquals("Run linter.", res.getJSONObject("schema").getString("guidelines"));
    }

    @Test
    public void loadedAgentInstructionsAreRepeatedInTheLoadedSkillsSection() throws Exception {
        write(".github/agents/reviewer.agent.md", "Review ruthlessly.");
        mManager.getSkillSchema(RuntimeEnvironment.getApplication(), "reviewer");
        String loaded = mManager.getLoadedSkillsInstructions(RuntimeEnvironment.getApplication());
        assertTrue(loaded.contains("REVIEWER"));
    }

    @Test
    public void unknownSkillNameReturnsError() throws Exception {
        JSONObject res = mManager.getSkillSchema(RuntimeEnvironment.getApplication(), "does-not-exist");
        assertEquals("error", res.getString("status"));
    }
}
