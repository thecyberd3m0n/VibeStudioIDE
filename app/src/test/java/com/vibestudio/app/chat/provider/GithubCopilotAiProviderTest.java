package com.vibestudio.app.chat.provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class GithubCopilotAiProviderTest {

    @Test
    public void parseChatModelsFiltersNonSelectableEntriesAndPutsDefaultFirst() throws Exception {
        String body = "{\"data\":["
                + "{\"id\":\"gpt-4\",\"model_picker_enabled\":false,\"capabilities\":{\"type\":\"chat\"}},"
                + "{\"id\":\"text-embedding-3-small\",\"model_picker_enabled\":true,\"capabilities\":{\"type\":\"embeddings\"}},"
                + "{\"id\":\"claude-3.7-sonnet\",\"model_picker_enabled\":true,\"capabilities\":{\"type\":\"chat\"},\"policy\":{\"state\":\"unconfigured\"}},"
                + "{\"id\":\"gpt-4.1\",\"model_picker_enabled\":true,\"capabilities\":{\"type\":\"chat\"},\"is_chat_default\":true},"
                + "{\"id\":\"claude-sonnet-4\",\"model_picker_enabled\":true,\"capabilities\":{\"type\":\"chat\"},\"policy\":{\"state\":\"enabled\"}},"
                + "{\"id\":\"gpt-4o\",\"model_picker_enabled\":true,\"capabilities\":{\"type\":\"chat\"}},"
                + "{\"some-unknown-field\":123}"
                + "],\"object\":\"list\"}";

        List<String> models = GithubCopilotAiProvider.parseChatModels(body);

        assertEquals(Arrays.asList("gpt-4.1", "claude-sonnet-4", "gpt-4o"), models);
    }

    @Test
    public void parseChatModelsKeepsModelsWithAnySupportedEndpointAndDropsOthers() throws Exception {
        String body = "{\"data\":["
                + "{\"id\":\"gpt-4.1\",\"capabilities\":{\"type\":\"chat\"},\"supported_endpoints\":[\"/chat/completions\",\"/responses\"]},"
                + "{\"id\":\"gpt-5-responses-only\",\"capabilities\":{\"type\":\"chat\"},\"supported_endpoints\":[\"/responses\"]},"
                + "{\"id\":\"messages-only\",\"capabilities\":{\"type\":\"chat\"},\"supported_endpoints\":[\"/v1/messages\"]},"
                + "{\"id\":\"gpt-4o\",\"capabilities\":{\"type\":\"chat\"}}"
                + "]}";

        assertEquals(Arrays.asList("gpt-4.1", "gpt-5-responses-only", "gpt-4o"), GithubCopilotAiProvider.parseChatModels(body));
    }

    @Test
    public void parseChatModelsReportsToolCallsAndApiPerModel() throws Exception {
        String body = "{\"data\":["
                + "{\"id\":\"with-tools\",\"capabilities\":{\"type\":\"chat\",\"supports\":{\"tool_calls\":true,\"parallel_tool_calls\":true}}},"
                + "{\"id\":\"no-tools\",\"capabilities\":{\"type\":\"chat\",\"supports\":{\"tool_calls\":false}}},"
                + "{\"id\":\"grok-4.7\",\"capabilities\":{\"type\":\"chat\"},\"supported_endpoints\":[\"/responses\"]}"
                + "]}";
        java.util.Map<String, GithubCopilotAiProvider.ModelInfo> infos = new java.util.LinkedHashMap<>();

        GithubCopilotAiProvider.parseChatModels(body, infos);

        assertTrue(infos.get("with-tools").toolCalls);
        assertTrue(!infos.get("no-tools").toolCalls);
        assertTrue(!infos.get("grok-4.7").toolCalls);
        assertTrue(infos.get("with-tools").parallelToolCalls);
        assertTrue(!infos.get("no-tools").parallelToolCalls);
        assertEquals(CopilotApi.RESPONSES, infos.get("grok-4.7").api);
        assertEquals(CopilotApi.CHAT_COMPLETIONS, infos.get("with-tools").api);
    }

    @Test
    public void unknownModelsAreTriedWithNativeTools() {
        GithubCopilotAiProvider provider = new GithubCopilotAiProvider();
        provider.restoreModelCapabilitiesState("{\"no-tools\":{\"tools\":false,\"api\":\"CHAT_COMPLETIONS\"},\"legacy\":true,"
                + "\"parallel\":{\"tools\":true,\"parallel\":true,\"api\":\"CHAT_COMPLETIONS\"}}");

        assertTrue(!provider.supportsNativeTools("no-tools"));
        assertTrue(provider.supportsNativeTools("legacy"));
        assertTrue(provider.supportsNativeTools("never-seen"));
        assertTrue(provider.supportsParallelToolCalls("parallel"));
        assertTrue(!provider.supportsParallelToolCalls("legacy"));
        assertTrue(!provider.supportsParallelToolCalls("never-seen"));
    }

    @Test
    public void parseChatModelsAcceptsMinimalEntries() throws Exception {
        List<String> models = GithubCopilotAiProvider.parseChatModels("{\"data\":[{\"id\":\"gpt-4o\"}]}");
        assertEquals(Arrays.asList("gpt-4o"), models);
    }

    @Test
    public void parseChatModelsReturnsEmptyWhenNoData() throws Exception {
        assertTrue(GithubCopilotAiProvider.parseChatModels("{\"object\":\"list\"}").isEmpty());
    }

    @Test
    public void toolCallContentLiftsPlannedDelayOutOfArgs() throws Exception {
        org.json.JSONObject args = new org.json.JSONObject().put("command", "npm run dev").put("planned_delay_ms", 5000);

        org.json.JSONObject call = new org.json.JSONObject(AbstractAiProvider.toolCallContent("execute_command_async", args));

        assertEquals(5000, call.getLong("planned_delay_ms"));
        assertEquals("execute_command_async", call.getString("tool"));
        assertEquals("npm run dev", call.getJSONObject("args").getString("command"));
        assertTrue(!call.getJSONObject("args").has("planned_delay_ms"));
    }

    @Test
    public void toolCallContentDefaultsToZeroDelay() throws Exception {
        org.json.JSONObject call = new org.json.JSONObject(AbstractAiProvider.toolCallContent("file", null));
        assertEquals(0, call.getLong("planned_delay_ms"));
    }

    @Test
    public void editorVersionIdentifiesAsVibeStudio() {
        GithubCopilotAiProvider.initEditorVersion(RuntimeEnvironment.getApplication());
        assertTrue(GithubCopilotAiProvider.getEditorVersion().startsWith("VibeStudio/"));
    }
}
