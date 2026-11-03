package com.vibestudio.app.chat.agents;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class AgentToolPolicyTest {
    @Test
    public void noToolsDeclaredAllowsEverything() {
        AgentToolPolicy p = new AgentToolPolicy(null);
        assertFalse(p.isEnforced());
        assertTrue(p.isAllowed("execute_command"));
    }

    @Test
    public void readOnlyAgentCannotEditOrExecute() {
        AgentToolPolicy p = new AgentToolPolicy(Arrays.asList("read", "search"));
        assertTrue(p.isEnforced());
        assertTrue(p.isAllowed("editor_read_file"));
        assertTrue(p.isAllowed("file_list_directory"));
        assertFalse(p.isAllowed("editor_replace_lines"));
        assertFalse(p.isAllowed("execute_command"));
        assertFalse(p.isAllowed("browser_navigate"));
    }

    @Test
    public void editImpliesReadAndWebAllowsBrowser() {
        AgentToolPolicy p = new AgentToolPolicy(Arrays.asList("Edit", "web"));
        assertTrue(p.isAllowed("editor_read_file"));
        assertTrue(p.isAllowed("file_delete"));
        assertTrue(p.isAllowed("browser_click"));
        assertFalse(p.isAllowed("execute_command_async"));
    }

    @Test
    public void emptyListBlocksAllTools() {
        AgentToolPolicy p = new AgentToolPolicy(Collections.<String>emptyList());
        assertTrue(p.isEnforced());
        assertFalse(p.isAllowed("editor_read_file"));
    }

    @Test
    public void wildcardAndUnknownOnlyListsAreNotEnforced() {
        assertFalse(new AgentToolPolicy(Arrays.asList("*")).isEnforced());
        assertFalse(new AgentToolPolicy(Arrays.asList("some-mcp/*", "githubRepo")).isEnforced());
    }

    @Test
    public void exactBuiltInToolNameIsAccepted() {
        AgentToolPolicy p = new AgentToolPolicy(Arrays.asList("execute/execute_command"));
        assertTrue(p.isAllowed("execute_command"));
        assertFalse(p.isAllowed("send_keystroke"));
    }
}
