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
    public void parseChatModelsAcceptsMinimalEntries() throws Exception {
        List<String> models = GithubCopilotAiProvider.parseChatModels("{\"data\":[{\"id\":\"gpt-4o\"}]}");
        assertEquals(Arrays.asList("gpt-4o"), models);
    }

    @Test
    public void parseChatModelsReturnsEmptyWhenNoData() throws Exception {
        assertTrue(GithubCopilotAiProvider.parseChatModels("{\"object\":\"list\"}").isEmpty());
    }

    @Test
    public void editorVersionIdentifiesAsVibeStudio() {
        GithubCopilotAiProvider.initEditorVersion(RuntimeEnvironment.getApplication());
        assertTrue(GithubCopilotAiProvider.getEditorVersion().startsWith("VibeStudio/"));
    }
}
