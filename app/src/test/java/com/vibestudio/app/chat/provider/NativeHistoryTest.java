package com.vibestudio.app.chat.provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.vibestudio.app.chat.model.ChatMessage;
import com.vibestudio.app.mcp.model.McpTool;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** How the chat history is projected, and what each provider puts on the wire for it. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class NativeHistoryTest {

    private static ChatMessage user(String text) {
        return new ChatMessage("You", text, true);
    }

    private static ChatMessage assistant(String text) {
        return new ChatMessage("Model", text, false);
    }

    private static ChatMessage call(String id, String turn, String tool, String args, String state, String kind) {
        String json = "{\"planned_delay_ms\":0,\"tool\":\"" + tool + "\",\"args\":" + args + "}";
        return new ChatMessage("Model", json, false, ChatMessage.MessageType.TOOL_CALL, tool, null)
                .withToolLink(id, turn, state, kind);
    }

    private static ChatMessage delayedCall(String id, String turn) {
        String json = "{\"planned_delay_ms\":5000,\"tool\":\"execute_command_async\",\"args\":{\"command\":\"npm i\"}}";
        return new ChatMessage("Model", json, false, ChatMessage.MessageType.TOOL_CALL, "execute_command_async", null)
                .withToolLink(id, turn, null, null);
    }

    private static ChatMessage result(String id, String turn, String tool, String text, boolean error) {
        return new ChatMessage("System Tool", text, true,
                error ? ChatMessage.MessageType.ERROR : ChatMessage.MessageType.TOOL_RESULT, tool, null)
                .withToolLink(id, turn, null, null);
    }

    /** user, one reply with two parallel calls (stored call/result pairs), then the final answer. */
    private static List<ChatMessage> parallelHistory(String stateA, String stateB, String kind) {
        return new ArrayList<>(Arrays.asList(
                user("list both"),
                call("a1", "t1", "file_list_directory", "{\"path\":\"a\"}", stateA, kind),
                result("a1", "t1", "file_list_directory", "one.txt", false),
                call("a2", "t1", "file_list_directory", "{\"path\":\"b\"}", stateB, kind),
                result("a2", "t1", "file_list_directory", "two.txt", false),
                assistant("done")));
    }

    private static List<McpTool> someTools() {
        return Arrays.asList(McpTool.builder("file_list_directory", "lists").build());
    }

    // ---- projection ----

    @Test
    public void withoutNativeToolsEverythingStaysText() {
        List<NativeHistory.Item> items = NativeHistory.project(parallelHistory(null, null, null), false, null);

        assertEquals(6, items.size());
        for (NativeHistory.Item item : items) {
            assertTrue(item.kind == NativeHistory.Item.Kind.USER || item.kind == NativeHistory.Item.Kind.ASSISTANT);
        }
        assertEquals(NativeHistory.Item.Kind.ASSISTANT, items.get(1).kind);
        assertTrue(items.get(1).text.contains("file_list_directory"));
        assertEquals(NativeHistory.Item.Kind.USER, items.get(2).kind);
    }

    @Test
    public void parallelCallsOfOneReplyBecomeOneAssistantItemFollowedByTheirResults() {
        List<NativeHistory.Item> items = NativeHistory.project(parallelHistory(null, null, null), true, null);

        assertEquals(5, items.size());
        assertEquals(NativeHistory.Item.Kind.USER, items.get(0).kind);
        assertEquals(NativeHistory.Item.Kind.ASSISTANT_CALLS, items.get(1).kind);
        assertEquals(2, items.get(1).calls.size());
        assertEquals("a1", items.get(1).calls.get(0).id);
        assertEquals("a2", items.get(1).calls.get(1).id);
        assertEquals(NativeHistory.Item.Kind.TOOL_RESULT, items.get(2).kind);
        assertEquals("a1", items.get(2).call.id);
        assertEquals("one.txt", items.get(2).text);
        assertEquals("a2", items.get(3).call.id);
        assertEquals(NativeHistory.Item.Kind.ASSISTANT, items.get(4).kind);
    }

    @Test
    public void aCallWithoutARecordedResultGetsASyntheticErrorResult() {
        List<ChatMessage> history = new ArrayList<>(Arrays.asList(
                user("go"), call("a1", "t1", "file_list_directory", "{}", null, null)));

        List<NativeHistory.Item> items = NativeHistory.project(history, true, null);

        assertEquals(3, items.size());
        assertEquals(NativeHistory.NO_RESULT_TEXT, items.get(2).text);
        assertTrue(items.get(2).error);
    }

    @Test
    public void aResultWithoutItsCallStaysText() {
        List<ChatMessage> history = new ArrayList<>(Arrays.asList(
                user("go"), result("gone", "t0", "file_list_directory", "orphan", false)));

        List<NativeHistory.Item> items = NativeHistory.project(history, true, null);

        assertEquals(2, items.size());
        assertEquals(NativeHistory.Item.Kind.USER, items.get(1).kind);
    }

    @Test
    public void callsWithoutStateOfTheRequiredKindStayText() {
        List<NativeHistory.Item> foreign = NativeHistory.project(parallelHistory("S", "S", "responses"), true, "gemini");
        List<NativeHistory.Item> none = NativeHistory.project(parallelHistory(null, null, null), true, "gemini");
        List<NativeHistory.Item> own = NativeHistory.project(parallelHistory("S", "S", "gemini"), true, "gemini");

        assertEquals(6, foreign.size());
        assertEquals(6, none.size());
        assertEquals(5, own.size());
        assertEquals(NativeHistory.Item.Kind.ASSISTANT_CALLS, own.get(1).kind);
    }

    @Test
    public void aRequestedDelayGoesBackIntoTheNativeArguments() throws Exception {
        List<ChatMessage> history = new ArrayList<>(Arrays.asList(user("go"), delayedCall("c1", "t1")));

        NativeHistory.Call call = NativeHistory.project(history, true, null).get(1).calls.get(0);

        assertEquals(5000, call.nativeArgs().getLong("planned_delay_ms"));
        assertEquals("npm i", call.nativeArgs().getString("command"));
        assertFalse(call.args.has("planned_delay_ms"));
    }

    // ---- Copilot chat completions ----

    @Test
    public void chatCompletionsGetsToolCallsAndToolMessages() throws Exception {
        JSONObject payload = GithubCopilotAiProvider.buildChatCompletionsPayload(
                "claude-sonnet-5.5", "SYS", parallelHistory(null, null, null), someTools());

        JSONArray messages = payload.getJSONArray("messages");
        assertEquals("system", messages.getJSONObject(0).getString("role"));
        assertEquals("user", messages.getJSONObject(1).getString("role"));
        JSONObject assistant = messages.getJSONObject(2);
        assertEquals("assistant", assistant.getString("role"));
        JSONArray toolCalls = assistant.getJSONArray("tool_calls");
        assertEquals(2, toolCalls.length());
        assertEquals("a1", toolCalls.getJSONObject(0).getString("id"));
        assertEquals("function", toolCalls.getJSONObject(0).getString("type"));
        assertEquals("file_list_directory", toolCalls.getJSONObject(0).getJSONObject("function").getString("name"));
        assertEquals("a", new JSONObject(toolCalls.getJSONObject(0).getJSONObject("function").getString("arguments")).getString("path"));
        assertEquals("tool", messages.getJSONObject(3).getString("role"));
        assertEquals("a1", messages.getJSONObject(3).getString("tool_call_id"));
        assertEquals("one.txt", messages.getJSONObject(3).getString("content"));
        assertEquals("a2", messages.getJSONObject(4).getString("tool_call_id"));
        assertEquals("assistant", messages.getJSONObject(5).getString("role"));
    }

    @Test
    public void chatCompletionsWithoutToolsKeepsTheTextHistory() throws Exception {
        JSONObject payload = GithubCopilotAiProvider.buildChatCompletionsPayload(
                "claude-sonnet-5.5", "SYS", parallelHistory(null, null, null), null);

        JSONArray messages = payload.getJSONArray("messages");
        assertEquals(7, messages.length());
        for (int i = 0; i < messages.length(); i++) {
            assertFalse(messages.getJSONObject(i).has("tool_calls"));
            assertFalse("tool".equals(messages.getJSONObject(i).getString("role")));
        }
    }

    @Test
    public void kimiModelsGetTheirOwnCallIdFormat() throws Exception {
        JSONObject older = GithubCopilotAiProvider.buildChatCompletionsPayload(
                "kimi-k2", "SYS", parallelHistory(null, null, null), someTools());
        JSONObject k3 = GithubCopilotAiProvider.buildChatCompletionsPayload(
                "kimi-k3", "SYS", parallelHistory(null, null, null), someTools());

        JSONArray olderMessages = older.getJSONArray("messages");
        assertEquals("functions.file_list_directory:0",
                olderMessages.getJSONObject(2).getJSONArray("tool_calls").getJSONObject(0).getString("id"));
        assertEquals("functions.file_list_directory:0", olderMessages.getJSONObject(3).getString("tool_call_id"));
        JSONArray k3Messages = k3.getJSONArray("messages");
        assertEquals("file_list_directory:1",
                k3Messages.getJSONObject(2).getJSONArray("tool_calls").getJSONObject(1).getString("id"));
        assertEquals("file_list_directory:1", k3Messages.getJSONObject(4).getString("tool_call_id"));
    }

    // ---- Copilot responses ----

    @Test
    public void responsesGetsFunctionCallItemsAndReplaysReasoningOnlyForTheSameModel() throws Exception {
        String state = new JSONObject().put("model", "gpt-6-luna")
                .put("reasoning", new JSONArray().put(new JSONObject().put("type", "reasoning").put("id", "rs_1")
                        .put("summary", new JSONArray()).put("encrypted_content", "enc"))).toString();
        List<ChatMessage> history = parallelHistory(state, null, "responses");

        JSONArray same = CopilotResponsesApi.buildRequest("gpt-6-luna", "SYS", history, someTools()).getJSONArray("input");
        JSONArray other = CopilotResponsesApi.buildRequest("grok-4.7", "SYS", history, someTools()).getJSONArray("input");

        // user message, reasoning, 2 function_calls, 2 outputs, assistant message
        assertEquals(7, same.length());
        assertEquals("message", same.getJSONObject(0).getString("type"));
        assertEquals("input_text", same.getJSONObject(0).getJSONArray("content").getJSONObject(0).getString("type"));
        assertEquals("reasoning", same.getJSONObject(1).getString("type"));
        assertEquals("enc", same.getJSONObject(1).getString("encrypted_content"));
        assertEquals("function_call", same.getJSONObject(2).getString("type"));
        assertEquals("a1", same.getJSONObject(2).getString("call_id"));
        assertEquals("function_call", same.getJSONObject(3).getString("type"));
        assertEquals("function_call_output", same.getJSONObject(4).getString("type"));
        assertEquals("a1", same.getJSONObject(4).getString("call_id"));
        assertEquals("one.txt", same.getJSONObject(4).getString("output"));
        assertEquals("output_text", same.getJSONObject(6).getJSONArray("content").getJSONObject(0).getString("type"));
        assertEquals(6, other.length());
    }

    @Test
    public void responsesParseKeepsCallIdsAndTheReasoningOfTheReply() throws Exception {
        String body = "{\"status\":\"completed\",\"output\":["
                + "{\"type\":\"reasoning\",\"id\":\"rs_9\",\"status\":\"completed\",\"encrypted_content\":\"zzz\",\"summary\":[]},"
                + "{\"type\":\"function_call\",\"name\":\"file_list_directory\",\"call_id\":\"call_A\",\"arguments\":\"{\\\"path\\\":\\\"a\\\"}\"},"
                + "{\"type\":\"function_call\",\"name\":\"file_list_directory\",\"call_id\":\"call_B\",\"arguments\":\"{}\"}]}";

        CopilotResponsesApi.Result result = CopilotResponsesApi.parse(body, "gpt-6-luna");

        assertEquals(2, result.toolCalls.size());
        assertEquals("call_A", result.toolCalls.get(0).getId());
        assertEquals("responses", result.toolCalls.get(0).getStateKind());
        JSONObject state = new JSONObject(result.toolCalls.get(0).getState());
        assertEquals("gpt-6-luna", state.getString("model"));
        JSONObject item = state.getJSONArray("reasoning").getJSONObject(0);
        assertEquals("rs_9", item.getString("id"));
        assertFalse("response-only fields are not sent back", item.has("status"));
        assertEquals("call_B", result.toolCalls.get(1).getId());
        assertEquals(null, result.toolCalls.get(1).getState());
    }

    // ---- Gemini ----

    @Test
    public void geminiReplaysItsOwnFunctionCallPartsAndAnswersThemWithFunctionResponses() throws Exception {
        String partA = "{\"functionCall\":{\"name\":\"file_list_directory\",\"args\":{\"path\":\"a\"},\"id\":\"g-1\"},\"thoughtSignature\":\"SIG\"}";
        String partB = "{\"functionCall\":{\"name\":\"file_list_directory\",\"args\":{\"path\":\"b\"}}}";

        JSONArray contents = GeminiAiProvider.buildContents(parallelHistory(partA, partB, "gemini"), true);

        assertEquals(4, contents.length());
        assertEquals("user", contents.getJSONObject(0).getString("role"));
        JSONObject model = contents.getJSONObject(1);
        assertEquals("model", model.getString("role"));
        assertEquals(2, model.getJSONArray("parts").length());
        assertEquals("SIG", model.getJSONArray("parts").getJSONObject(0).getString("thoughtSignature"));
        JSONObject responses = contents.getJSONObject(2);
        assertEquals("user", responses.getString("role"));
        JSONArray responseParts = responses.getJSONArray("parts");
        assertEquals(2, responseParts.length());
        JSONObject first = responseParts.getJSONObject(0).getJSONObject("functionResponse");
        assertEquals("file_list_directory", first.getString("name"));
        assertEquals("g-1", first.getString("id"));
        assertEquals("one.txt", first.getJSONObject("response").getString("result"));
        assertFalse("no id when the call had none", responseParts.getJSONObject(1).getJSONObject("functionResponse").has("id"));
        assertEquals("model", contents.getJSONObject(3).getString("role"));
    }

    @Test
    public void geminiTurnsOfOneRoleAreMergedAndErrorsUseTheErrorKey() throws Exception {
        String part = "{\"functionCall\":{\"name\":\"file_delete\",\"args\":{}}}";
        List<ChatMessage> history = new ArrayList<>(Arrays.asList(
                user("go"),
                call("c1", "t1", "file_delete", "{}", part, "gemini"),
                result("c1", "t1", "file_delete", "denied", true),
                user("continue")));

        JSONArray contents = GeminiAiProvider.buildContents(history, true);

        assertEquals(3, contents.length());
        JSONArray parts = contents.getJSONObject(2).getJSONArray("parts");
        assertEquals("denied", parts.getJSONObject(0).getJSONObject("functionResponse").getJSONObject("response").getString("error"));
        assertEquals("continue", parts.getJSONObject(1).getString("text"));
    }

    @Test
    public void geminiKeepsCallsItDidNotProduceAsText() throws Exception {
        JSONArray contents = GeminiAiProvider.buildContents(parallelHistory(null, null, null), true);

        for (int i = 0; i < contents.length(); i++) {
            JSONArray parts = contents.getJSONObject(i).getJSONArray("parts");
            for (int j = 0; j < parts.length(); j++) {
                assertFalse(parts.getJSONObject(j).has("functionCall"));
                assertFalse(parts.getJSONObject(j).has("functionResponse"));
            }
        }
    }
}
