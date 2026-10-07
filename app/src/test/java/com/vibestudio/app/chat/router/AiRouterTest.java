package com.vibestudio.app.chat.router;

import android.content.Context;

import com.vibestudio.app.chat.model.AiResponse;
import com.vibestudio.app.chat.model.ChatMessage;
import com.vibestudio.app.chat.provider.AiProvider;
import com.vibestudio.app.db.DatabaseHelper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, shadows = {AiRouterTest.ShadowDatabaseHelperForRouter.class})
public class AiRouterTest {

    private Context mContext;
    private AiRouter mRouter;

    @Before
    public void setUp() {
        ShadowDatabaseHelperForRouter.sSettings.clear();
        ShadowDatabaseHelperForRouter.sApiKeys.clear();
        mContext = RuntimeEnvironment.getApplication();
        mRouter = new AiRouter();
    }

    @Test
    public void testRegisterAndGetProviders() {
        List<AiProvider> initialProviders = mRouter.getProviders();
        assertEquals(3, initialProviders.size());

        AiProvider mockProvider1 = new MockAiProvider("Gemini");
        AiProvider mockProvider2 = new MockAiProvider("CustomProvider");

        mRouter.registerProvider(mockProvider1);
        mRouter.registerProvider(mockProvider2);

        List<AiProvider> providers = mRouter.getProviders();
        assertEquals(4, providers.size());
        assertEquals("Gemini", mRouter.getProvider("Gemini").getName());
        assertEquals("OpenAI", mRouter.getProvider("OpenAI").getName());
        assertEquals("Claude", mRouter.getProvider("Claude").getName());
        assertEquals("CustomProvider", mRouter.getProvider("CustomProvider").getName());
        assertNull(mRouter.getProvider("NonExistent"));
    }

    @Test
    public void testSetAndGetActiveProviderAndModel() {
        mRouter.setActiveProviderName("Gemini");
        assertEquals("Gemini", mRouter.getActiveProviderName());

        mRouter.setActiveModelName("Gemini", "gemini-2.5-pro");
        assertEquals("gemini-2.5-pro", mRouter.getActiveModelName("Gemini"));
    }

    @Test
    public void testRouteToActiveProviderWithSelectedModel() {
        MockAiProvider geminiProvider = new MockAiProvider("Gemini");
        MockAiProvider openAiProvider = new MockAiProvider("OpenAI");

        mRouter.registerProvider(geminiProvider);
        mRouter.registerProvider(openAiProvider);

        mRouter.setActiveProviderName("Gemini");
        mRouter.setActiveModelName("Gemini", "gemini-2.5-pro");

        List<ChatMessage> history = new ArrayList<>();
        history.add(new ChatMessage("User", "Hello", true));

        AiResponse response = mRouter.generateContent(mContext, "test-api-key", "system instruction", history);

        assertNotNull(response);
        assertEquals("Response from Gemini using model gemini-2.5-pro", response.getContent());
    }

    @Test(expected = IllegalStateException.class)
    public void testRouteWithoutActiveProviderThrowsException() {
        mRouter.setActiveProviderName("NonExistentProvider");
        List<ChatMessage> history = new ArrayList<>();
        mRouter.generateContent(mContext, "test-api-key", "system instruction", history);
    }

    @Test
    public void testPersistAndLoadActiveSelectionFromDatabase() {
        mRouter.saveActiveSelection(mContext, "OpenAI", "gpt-4o");
        assertEquals("OpenAI", mRouter.getActiveProviderName());
        assertEquals("gpt-4o", mRouter.getActiveModelName("OpenAI"));

        AiRouter newRouterInstance = new AiRouter();
        newRouterInstance.loadActiveState(mContext);
        assertEquals("OpenAI", newRouterInstance.getActiveProviderName());
        assertEquals("gpt-4o", newRouterInstance.getActiveModelName("OpenAI"));
    }

    @Test
    public void testGetApiKeyForActiveProvider() {
        mRouter.saveActiveProvider(mContext, "Gemini");
        ShadowDatabaseHelperForRouter.sApiKeys.put("Gemini", "gemini-secret-key");

        assertEquals("gemini-secret-key", mRouter.getActiveApiKey(mContext));
    }

    @Test
    public void testNonApiKeyProviderDefaultsToDisabledStateInModels() {
        MockAiProvider mockProvider = new MockAiProvider("Anthropic");
        mRouter.registerProvider(mockProvider);
        mRouter.setActiveProviderName("Anthropic");

        assertNull(mRouter.getActiveApiKey(mContext));
    }

    private static class MockAiProvider implements AiProvider {
        private final String mName;

        MockAiProvider(String name) {
            mName = name;
        }

        @Override
        public String getName() {
            return mName;
        }

        @Override
        public List<String> getAvailableModels() {
            return Arrays.asList(mName.toLowerCase() + "-default", mName.toLowerCase() + "-pro");
        }

        @Override
        public String getDefaultModel() {
            return mName.toLowerCase() + "-default";
        }

        @Override
        public AiResponse generateContent(Context context, String apiKey, String selectedModel, String systemInstruction, List<ChatMessage> history) {
            return AiResponse.success("Response from " + mName + " using model " + selectedModel);
        }
    }

    @Implements(DatabaseHelper.class)
    public static class ShadowDatabaseHelperForRouter {
        static final Map<String, String> sSettings = new HashMap<>();
        static final Map<String, String> sApiKeys = new HashMap<>();

        @Implementation
        public String getSetting(String key) {
            return sSettings.get(key);
        }

        @Implementation
        public void setSetting(String key, String value) {
            sSettings.put(key, value);
        }

        @Implementation
        public String getApiKey(String provider) {
            return sApiKeys.get(provider);
        }

        @Implementation
        public void saveApiKey(String provider, String key) {
            sApiKeys.put(provider, key);
        }
    }
}
