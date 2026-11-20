package com.vibestudio.app.chat.provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class CopilotApiTest {

    @Test
    public void familyMapPicksTheApiByModelName() {
        assertEquals(CopilotApi.CHAT_COMPLETIONS, CopilotApi.forModelName("claude-sonnet-5.5"));
        assertEquals(CopilotApi.CHAT_COMPLETIONS, CopilotApi.forModelName("gpt-4o"));
        assertEquals(CopilotApi.CHAT_COMPLETIONS, CopilotApi.forModelName("gpt-4.1"));
        assertEquals(CopilotApi.RESPONSES, CopilotApi.forModelName("gpt-6-luna"));
        assertEquals(CopilotApi.RESPONSES, CopilotApi.forModelName("grok-4.7"));
        assertEquals(CopilotApi.RESPONSES, CopilotApi.forModelName("mai-code-1.1-flash"));
        assertEquals(CopilotApi.CHAT_COMPLETIONS, CopilotApi.forModelName("something-new"));
    }

    @Test
    public void listedEndpointsBeatTheNameMapUnlessBothAreListed() throws Exception {
        assertEquals(CopilotApi.RESPONSES, CopilotApi.choose("claude-x", new JSONArray("[\"/responses\"]")));
        assertEquals(CopilotApi.CHAT_COMPLETIONS, CopilotApi.choose("grok-4.7", new JSONArray("[\"/chat/completions\"]")));
        assertEquals(CopilotApi.RESPONSES, CopilotApi.choose("grok-4.7", new JSONArray("[\"/chat/completions\",\"/responses\"]")));
        assertEquals(CopilotApi.CHAT_COMPLETIONS, CopilotApi.choose("claude-x", new JSONArray("[\"/v1/messages\",\"/chat/completions\"]")));
        assertNull(CopilotApi.choose("claude-x", new JSONArray("[\"/v1/messages\"]")));
        assertEquals(CopilotApi.RESPONSES, CopilotApi.choose("gpt-6-luna", null));
    }

    @Test
    public void parsesATextAndToolCallFromAPlainResponse() throws Exception {
        String body = "{\"status\":\"completed\",\"output\":["
                + "{\"type\":\"reasoning\",\"summary\":[{\"type\":\"summary_text\",\"text\":\"thinking\"}]},"
                + "{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"Listing files.\"}]},"
                + "{\"type\":\"function_call\",\"name\":\"file_list_directory\",\"call_id\":\"c1\",\"arguments\":\"{\\\"path\\\":\\\".\\\"}\"}"
                + "],\"usage\":{\"input_tokens\":10,\"output_tokens\":5}}";

        CopilotResponsesApi.Result result = CopilotResponsesApi.parse(body, "grok-4.7");

        assertNull(result.error);
        assertEquals("thinking", result.reasoning);
        assertEquals(15, result.tokens);
        assertTrue(result.text.startsWith("Listing files."));
        JSONObject call = new JSONObject(result.text.substring(result.text.indexOf('{')));
        assertEquals("file_list_directory", call.getString("tool"));
        assertEquals(".", call.getJSONObject("args").getString("path"));
    }

    @Test
    public void parsesTheCompletedEventOfAStream() throws Exception {
        String stream = "event: response.created\n"
                + "data: {\"type\":\"response.created\",\"response\":{\"output\":[]}}\n\n"
                + "event: response.completed\n"
                + "data: {\"type\":\"response.completed\",\"response\":{\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"Hi\"}]}],\"usage\":{\"total_tokens\":7}}}\n\n";

        CopilotResponsesApi.Result result = CopilotResponsesApi.parse(stream, "grok-4.7");

        assertEquals("Hi", result.text);
        assertEquals(7, result.tokens);
    }

    @Test
    public void reportsAFailedResponse() throws Exception {
        CopilotResponsesApi.Result result = CopilotResponsesApi.parse("{\"status\":\"failed\",\"error\":{\"message\":\"boom\"}}", "grok-4.7");
        assertNotNull(result.error);
        assertEquals("boom", result.error);
    }

    @Test
    public void buildsAFlatToolRequestWithInstructions() throws Exception {
        JSONObject request = CopilotResponsesApi.buildRequest("grok-4.7", "SYS", null, null);

        assertEquals("grok-4.7", request.getString("model"));
        assertEquals("SYS", request.getString("instructions"));
        assertEquals(0, request.getJSONArray("input").length());
        assertTrue(request.getBoolean("stream"));
    }

    @Test
    public void requestsReasoningSummariesOnlyForReasoningModels() throws Exception {
        JSONObject request = CopilotResponsesApi.buildRequest("gpt-6-luna", "SYS", null, null);
        assertEquals("auto", request.getJSONObject("reasoning").getString("summary"));
        assertEquals("reasoning.encrypted_content", request.getJSONArray("include").getString(0));

        assertTrue(!CopilotResponsesApi.buildRequest("gpt-4.1", "SYS", null, null).has("reasoning"));
        assertTrue(!CopilotResponsesApi.buildRequest("grok-4.7", "SYS", null, null).has("reasoning"));
    }
}
