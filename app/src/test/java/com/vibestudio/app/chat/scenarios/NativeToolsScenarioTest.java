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
import com.vibestudio.app.chat.router.AiRouter;
import com.vibestudio.app.chat.session.ChatSession;
import com.vibestudio.app.db.DatabaseHelper;
import com.vibestudio.app.mcp.model.McpTool;
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
import java.util.ArrayList;
import java.util.List;

/** Native tool calling, parallel calls and the fallback to the text protocol, driven through a real ChatSession. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, shadows = {ShadowDatabaseHelper.class})
public class NativeToolsScenarioTest {
    private static final long TIMEOUT_MS = 15000;

    private Context mContext;
    private File mWorkspace;
    private ScriptedProvider mProvider;
    private AiRouter mRouter;
    private ChatSession mSession;

    @Before
    public void setUp() {
        ShadowDatabaseHelper.reset();
        mContext = RuntimeEnvironment.getApplication();
        new DatabaseHelper(mContext).saveApiKey("Gemini", "test-key");

        mWorkspace = new File(System.getProperty("java.io.tmpdir"), "vibe_native_" + System.nanoTime());
        assertTrue(mWorkspace.mkdirs());

        mProvider = new ScriptedProvider();
        mRouter = ScriptedProvider.newRouter(mProvider);
        mSession = new ChatSession(mWorkspace.getAbsolutePath(), mRouter);
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

    private String listCall(String path) {
        return "{\"planned_delay_ms\":0,\"tool\":\"file_list_directory\",\"args\":{\"path\":\"" + path + "\"}}";
    }

    private int count(ChatMessage.MessageType type) {
        int n = 0;
        for (ChatMessage m : mSession.getMessages()) {
            if (m.getType() == type) n++;
        }
        return n;
    }

    private static List<String> names(List<McpTool> tools) {
        List<String> names = new ArrayList<>();
        for (McpTool t : tools) names.add(t.getName());
        return names;
    }

    @Test
    public void everyCallInAReplyRunsInOrderWhenParallelCallsAreSupported() throws Exception {
        write("a/one.txt", "1");
        write("b/two.txt", "2");
        mProvider.nativeTools(true)
                .reply(listCall(new File(mWorkspace, "a").getAbsolutePath()) + "\n" + listCall(new File(mWorkspace, "b").getAbsolutePath()))
                .reply("done");

        send("list both");

        assertEquals(2, mProvider.requestCount());
        assertEquals(2, count(ChatMessage.MessageType.TOOL_RESULT));
        List<ChatMessage> history = mProvider.history(1);
        int one = -1, two = -1;
        for (int i = 0; i < history.size(); i++) {
            String text = history.get(i).getText();
            if (text != null && text.contains("one.txt")) one = i;
            if (text != null && text.contains("two.txt")) two = i;
        }
        assertTrue("both results reach the model", one >= 0 && two >= 0);
        assertTrue("results keep the call order", one < two);
    }

    @Test
    public void onlyTheFirstCallRunsWithoutParallelSupportAndTheRestIsReportedSkipped() throws Exception {
        write("a/one.txt", "1");
        write("b/two.txt", "2");
        mProvider.nativeTools(false)
                .reply(listCall(new File(mWorkspace, "a").getAbsolutePath()) + "\n" + listCall(new File(mWorkspace, "b").getAbsolutePath()))
                .reply("done");

        send("list both");

        assertEquals(1, count(ChatMessage.MessageType.TOOL_RESULT));
        boolean skippedNoteSent = false;
        for (ChatMessage m : mProvider.history(1)) {
            if (m.getText() != null && m.getText().contains("Not executed")) skippedNoteSent = true;
        }
        assertTrue(skippedNoteSent);
    }

    @Test
    public void nativeModeDeclaresToolsAndLeavesSchemasOutOfThePrompt() throws Exception {
        mProvider.nativeTools(true).reply("ok");

        send("hello");

        List<McpTool> declared = mProvider.tools(0);
        assertNotNull(declared);
        List<String> names = names(declared);
        assertTrue(names.contains("file_list_directory"));
        assertTrue(names.contains("get_skill_schema"));
        assertTrue(names.contains("task_complete"));
        String system = mProvider.systemInstruction(0);
        assertTrue(system.startsWith("BASE-INSTRUCTION"));
        assertFalse(system.contains("meta_tool"));
        assertFalse(system.contains("is_loaded"));
    }

    @Test
    public void textModeDeclaresNoToolsAndKeepsTheLoadingCatalog() throws Exception {
        mProvider.reply("ok");

        send("hello");

        assertNull(mProvider.tools(0));
        String system = mProvider.systemInstruction(0);
        assertTrue(system.contains("meta_tool"));
        assertTrue(system.contains("is_loaded"));
    }

    @Test
    public void agentAllowListLimitsTheDeclaredTools() throws Exception {
        write(".github/agents/lister.agent.md", "---\ntools: [read]\n---\nList things.");
        mProvider.nativeTools(true).reply("ok");

        send("/lister go");

        List<String> names = names(mProvider.tools(0));
        assertTrue(names.contains("file_list_directory"));
        assertTrue(names.contains("get_skill_schema"));
        assertTrue(names.contains("task_complete"));
        assertFalse(names.contains("file_delete"));
        assertFalse(names.contains("execute_command"));
    }

    @Test
    public void rejectedNativeToolsFallBackToTheTextProtocolAndAreRemembered() throws Exception {
        mProvider.nativeTools(true)
                .replyError(400, "tools are not supported")
                .reply("ok")
                .reply("ok again");

        send("hello");

        assertEquals(2, mProvider.requestCount());
        assertNotNull(mProvider.tools(0));
        assertNull(mProvider.tools(1));
        assertTrue("the retry carries the text-mode catalog", mProvider.systemInstruction(1).contains("meta_tool"));
        assertFalse(mRouter.usesNativeTools(mRouter.getActiveProviderName(), "scripted-model"));

        send("again");

        assertEquals(3, mProvider.requestCount());
        assertNull("remembered for the next turn", mProvider.tools(2));
    }

    @Test
    public void aFailureOfBothModesIsNotRemembered() throws Exception {
        mProvider.nativeTools(true)
                .replyError(400, "bad request")
                .replyError(400, "bad request");

        send("hello");

        assertTrue(mRouter.usesNativeTools(mRouter.getActiveProviderName(), "scripted-model"));
    }

    @Test
    public void nativeCallsAndResultsAreLinkedByTheProvidersIds() throws Exception {
        write("a/one.txt", "1");
        write("b/two.txt", "2");
        org.json.JSONObject first = new org.json.JSONObject(listCall(new File(mWorkspace, "a").getAbsolutePath()));
        org.json.JSONObject second = new org.json.JSONObject(listCall(new File(mWorkspace, "b").getAbsolutePath()));
        mProvider.nativeTools(true)
                .replyWithCalls("", new com.vibestudio.app.chat.model.AiResponse.ToolCall("id-1", first, "STATE-1", "gemini"),
                        new com.vibestudio.app.chat.model.AiResponse.ToolCall("id-2", second, null, "gemini"))
                .reply("done");

        send("list both");

        List<ChatMessage> calls = new ArrayList<>();
        List<ChatMessage> results = new ArrayList<>();
        for (ChatMessage m : mSession.getMessages()) {
            if (m.getType() == ChatMessage.MessageType.TOOL_CALL) calls.add(m);
            if (m.getType() == ChatMessage.MessageType.TOOL_RESULT) results.add(m);
        }
        assertEquals(2, calls.size());
        assertEquals(2, results.size());
        assertEquals("id-1", calls.get(0).getCallId());
        assertEquals("id-2", calls.get(1).getCallId());
        assertEquals("STATE-1", calls.get(0).getCallState());
        assertEquals("gemini", calls.get(0).getCallStateKind());
        assertEquals("id-1", results.get(0).getCallId());
        assertEquals("id-2", results.get(1).getCallId());
        assertNull("replay state belongs to the call only", results.get(0).getCallState());
        assertEquals("the calls of one reply share a turn", calls.get(0).getTurnId(), calls.get(1).getTurnId());
        assertNotNull(calls.get(0).getTurnId());
    }

    @Test
    public void textModeCallsGetGeneratedIdsSoTheyCanBeSentNativelyLater() throws Exception {
        write("a/one.txt", "1");
        mProvider.reply(listCall(new File(mWorkspace, "a").getAbsolutePath())).reply("done");

        send("list");

        ChatMessage call = null;
        ChatMessage result = null;
        for (ChatMessage m : mSession.getMessages()) {
            if (m.getType() == ChatMessage.MessageType.TOOL_CALL) call = m;
            if (m.getType() == ChatMessage.MessageType.TOOL_RESULT) result = m;
        }
        assertNotNull(call);
        assertNotNull(call.getCallId());
        assertEquals(call.getCallId(), result.getCallId());
    }
}
