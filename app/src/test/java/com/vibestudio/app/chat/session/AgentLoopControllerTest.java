package com.vibestudio.app.chat.session;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AgentLoopControllerTest {

    @Test
    public void terminalToolsGetTheTerminalNudge() {
        assertEquals(AgentLoopController.TERMINAL_NUDGE_MESSAGE, AgentLoopController.nudgeFor("execute_command_async"));
        assertEquals(AgentLoopController.TERMINAL_NUDGE_MESSAGE, AgentLoopController.nudgeFor("read_terminal_output"));
        assertTrue(AgentLoopController.TERMINAL_NUDGE_MESSAGE.contains("read_terminal_output"));
    }

    @Test
    public void otherToolsAndNoToolGetTheGenericNudge() {
        assertEquals(AgentLoopController.NUDGE_MESSAGE, AgentLoopController.nudgeFor("file_list_directory"));
        assertEquals(AgentLoopController.NUDGE_MESSAGE, AgentLoopController.nudgeFor(null));
    }
}
