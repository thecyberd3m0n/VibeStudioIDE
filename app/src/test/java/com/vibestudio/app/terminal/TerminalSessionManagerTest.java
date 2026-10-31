package com.vibestudio.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class TerminalSessionManagerTest {

    private static final String S = "___CMD_START_1_2___";
    private static final String E = "___CMD_END_1_2___";

    @Test
    public void extractIgnoresEchoedCommandLine() {
        String transcript = "$ echo " + S + " ; git commit -m 'it'\\''s' ; echo \"Exit code: $?\" ; echo " + E + "\n"
                + S + "\n[main abc] it's\nExit code: 0\n" + E + "\n$";
        String captured = TerminalSessionManager.extractBetweenMarkers(transcript, S, E);
        TerminalSessionManager.CommandResult result = TerminalSessionManager.parseCapturedOutput(captured);
        assertEquals(0, result.exitCode);
        assertEquals("[main abc] it's", result.output);
    }

    @Test
    public void extractReturnsNullUntilCommandFinishes() {
        assertNull(TerminalSessionManager.extractBetweenMarkers("$ echo " + S + " ; sleep 5 ; echo " + E + "\n" + S + "\nwork", S, E));
        assertNull(TerminalSessionManager.extractBetweenMarkers(null, S, E));
    }

    @Test
    public void parsesNonZeroExitCode() {
        String transcript = S + "\nfatal: nope\nExit code: 128\n" + E;
        TerminalSessionManager.CommandResult result =
                TerminalSessionManager.parseCapturedOutput(TerminalSessionManager.extractBetweenMarkers(transcript, S, E));
        assertEquals(128, result.exitCode);
        assertEquals("fatal: nope", result.output);
    }

    @Test
    public void extractHandlesPromptInFrontOfStartMarker() {
        String transcript = "$ set +o emacs\n$ echo " + S + " ; git status ; echo " + E + "\nuser@host:~$ " + S
                + "\nmain\nExit code: 0\n" + E + "\n$";
        String captured = TerminalSessionManager.extractBetweenMarkers(transcript, S, E);
        assertEquals("main", TerminalSessionManager.parseCapturedOutput(captured).output);
    }
}
