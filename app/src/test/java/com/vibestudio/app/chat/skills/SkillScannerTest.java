package com.vibestudio.app.chat.skills;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class SkillScannerTest {

    private File mTempWorkspace;

    @Before
    public void setUp() throws Exception {
        mTempWorkspace = new File(System.getProperty("java.io.tmpdir"), "vibe_skill_test_" + System.currentTimeMillis());
        assertTrue(mTempWorkspace.mkdirs());
    }

    @After
    public void tearDown() throws Exception {
        deleteRecursive(mTempWorkspace);
    }

    private void deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] files = f.listFiles();
            if (files != null) {
                for (File child : files) {
                    deleteRecursive(child);
                }
            }
        }
        f.delete();
    }

    @Test
    public void testGetDefaultSkills() {
        List<Skill> defaultSkills = SkillScanner.getDefaultSkills();
        assertNotNull(defaultSkills);
        assertTrue(defaultSkills.size() >= 5);

        boolean foundExplain = false;
        for (Skill s : defaultSkills) {
            if ("explain".equalsIgnoreCase(s.getName())) {
                foundExplain = true;
                assertEquals("/explain", s.getSlashCommand());
                assertNotNull(s.getDescription());
                assertNotNull(s.getContent());
            }
        }
        assertTrue("Default skill 'explain' should be present", foundExplain);
    }

    @Test
    public void testScanSkillsFromGithubWorkDirectory() throws IOException {
        File workDir = new File(mTempWorkspace, ".github/work");
        assertTrue(workDir.mkdirs());

        // Custom skill file 1 (with YAML frontmatter)
        File customSkill1 = new File(workDir, "security-audit.md");
        try (FileWriter writer = new FileWriter(customSkill1)) {
            writer.write("---\n" +
                    "name: sec-audit\n" +
                    "description: Perform OWASP top 10 security audit\n" +
                    "---\n" +
                    "Analyze code for SQL injection, XSS, and security vulnerabilities.");
        }

        // Custom skill file 2 (without frontmatter)
        File customSkill2 = new File(workDir, "optimize-perf.md");
        try (FileWriter writer = new FileWriter(customSkill2)) {
            writer.write("# Optimize Performance\n" +
                    "Refactor algorithm to reduce time and space complexity.");
        }

        List<Skill> skills = SkillScanner.scanSkills(mTempWorkspace.getAbsolutePath());
        assertNotNull(skills);

        Skill secAudit = null;
        Skill optPerf = null;
        for (Skill s : skills) {
            if ("sec-audit".equalsIgnoreCase(s.getName())) {
                secAudit = s;
            } else if ("optimize-perf".equalsIgnoreCase(s.getName())) {
                optPerf = s;
            }
        }

        assertNotNull("Skill 'sec-audit' should be scanned from .github/work", secAudit);
        assertEquals("/sec-audit", secAudit.getSlashCommand());
        assertEquals("Perform OWASP top 10 security audit", secAudit.getDescription());
        assertTrue(secAudit.getContent().contains("SQL injection"));

        assertNotNull("Skill 'optimize-perf' should be scanned from .github/work", optPerf);
        assertEquals("/optimize-perf", optPerf.getSlashCommand());
        assertTrue(optPerf.getDescription().contains("Optimize Performance"));
        assertTrue(optPerf.getContent().contains("Refactor algorithm"));
    }

    @Test
    public void testParseSkillFileDirectorySkillMd() throws IOException {
        File workDir = new File(mTempWorkspace, ".github/work/code-review");
        assertTrue(workDir.mkdirs());

        File skillMd = new File(workDir, "SKILL.md");
        try (FileWriter writer = new FileWriter(skillMd)) {
            writer.write("Check code style and consistency across PR.");
        }

        Skill skill = SkillScanner.parseSkillFile(skillMd);
        assertNotNull(skill);
        assertEquals("code-review", skill.getName());
        assertEquals("/code-review", skill.getSlashCommand());
        assertTrue(skill.getContent().contains("code style"));
    }

}
