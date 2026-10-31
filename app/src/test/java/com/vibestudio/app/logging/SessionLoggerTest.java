package com.vibestudio.app.logging;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class SessionLoggerTest {

    private static void out(SessionLogger logger, String text) {
        byte[] b = text.getBytes(StandardCharsets.UTF_8);
        logger.onStdout(b, 0, b.length);
    }

    @Test
    public void stdoutIsAssembledIntoCleanLines() {
        SessionLogger logger = new SessionLogger("t");
        out(logger, "hel");
        out(logger, "lo\r\n\u001b[1;36mcolored\u001b[0m\r\nprogress 1%\rprogress 100%\r\n");
        List<String> entries = logger.getEntries();
        assertEquals(3, entries.size());
        assertTrue(entries.get(0).endsWith("OUT hello"));
        assertTrue(entries.get(1).endsWith("OUT colored"));
        assertTrue(entries.get(2).endsWith("OUT progress 100%"));
    }

    @Test
    public void partialLineIsEmittedOnFlush() {
        SessionLogger logger = new SessionLogger("t");
        out(logger, "$ ");
        out(logger, "prompt>");
        assertEquals(0, logger.getEntries().size());
        logger.flush();
        assertTrue(logger.getEntries().get(0).endsWith("OUT $ prompt>"));
    }

    @Test
    public void internalMarkerLinesAreDropped() {
        SessionLogger logger = new SessionLogger("t");
        out(logger, "echo ___CMD_START_1_2___ ; git status ; echo ___CMD_END_1_2___\r\n");
        out(logger, "___CMD_START_1_2___\r\non branch main\r\n");
        assertEquals(1, logger.getEntries().size());
        assertTrue(logger.getEntries().get(0).endsWith("OUT on branch main"));
    }

    @Test
    public void stdinIsLoggedAndRedacted() {
        SessionLogger logger = new SessionLogger("t");
        byte[] b = "git clone https://user:s3cret@github.com/a/b.git\n".getBytes(StandardCharsets.UTF_8);
        logger.onStdin(b, 0, b.length);
        String entry = logger.getEntries().get(0);
        assertTrue(entry.contains("IN "));
        assertFalse(entry.contains("s3cret"));
    }

    @Test
    public void redactHidesCredentials() {
        assertFalse(SessionLogger.redact("git clone https://u:pw@host/x").contains("pw@"));
        assertFalse(SessionLogger.redact("push ghp_abcdefghijklmnopqrstuvwxyz0123").contains("ghp_"));
        assertFalse(SessionLogger.redact("ssh-keygen -t ed25519 -N 'my pass' -f key").contains("my pass"));
        assertEquals("git status", SessionLogger.redact("git status"));
    }

    @Test
    public void bufferIsBoundedAndKeepsNewestEntries() {
        SessionLogger logger = new SessionLogger("t", 1024);
        for (int i = 0; i < 200; i++) logger.event("entry-" + i + "-xxxxxxxxxxxxxxxxxxxx");
        assertTrue(logger.getAllText().getBytes(StandardCharsets.UTF_8).length <= 1024);
        assertTrue(logger.getAllText().contains("entry-199-"));
        assertFalse(logger.getAllText().contains("entry-0-"));
    }

    @Test
    public void listenersAreNotifiedAndClearWorks() {
        SessionLogger logger = new SessionLogger("t");
        List<String> seen = new ArrayList<>();
        final boolean[] cleared = {false};
        logger.addListener(new SessionLogger.Listener() {
            @Override public void onEntry(String entry) { seen.add(entry); }
            @Override public void onCleared() { cleared[0] = true; }
        });
        logger.command("git status");
        logger.exit("exit=0");
        assertEquals(2, seen.size());
        assertTrue(seen.get(0).contains("CMD git status"));
        logger.clear();
        assertTrue(cleared[0]);
        assertEquals(0, logger.getEntries().size());
    }
}
