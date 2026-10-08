package com.vibestudio.app.service;

import android.content.Context;

import com.vibestudio.app.chat.router.AiRouter;
import com.vibestudio.app.db.DatabaseHelper;
import com.vibestudio.app.test.ShadowDatabaseHelper;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE, shadows = {ShadowDatabaseHelper.class})
public class ChatServiceTest {

    private Context mContext;
    private DatabaseHelper mDbHelper;

    @Before
    public void setUp() {
        ShadowDatabaseHelper.reset();
        mContext = RuntimeEnvironment.getApplication();
        mDbHelper = new DatabaseHelper(mContext);
    }

    @Test
    public void testAiRouterPicksSelectedModel() {
        AiRouter router = AiRouter.getInstance();

        // Save active provider and selected model in DB
        router.saveActiveSelection(mContext, "OpenAI", "gpt-4o-mini");
        mDbHelper.saveApiKey("OpenAI", "test-key");

        router.loadActiveState(mContext);

        Assert.assertEquals("OpenAI", router.getActiveProviderName());
        Assert.assertEquals("gpt-4o-mini", router.getActiveModelName("OpenAI"));

        // Change selected model to gpt-4o
        router.saveActiveSelection(mContext, "OpenAI", "gpt-4o");
        Assert.assertEquals("gpt-4o", router.getActiveModelName("OpenAI"));

        // Verify reloading state preserves selected model
        AiRouter newRouter = new AiRouter();
        newRouter.loadActiveState(mContext);
        Assert.assertEquals("gpt-4o", newRouter.getActiveModelName("OpenAI"));
    }

    @Test
    public void testTokenBudgetAndUsageTracking() {
        AiRouter router = AiRouter.getInstance();

        // Check defaults
        Assert.assertEquals(100000, router.getTokenBudget(mContext, "Gemini"));
        Assert.assertEquals(0, router.getUsedTokens(mContext, "Gemini"));
        Assert.assertEquals(100000, router.getTokensLeft(mContext, "Gemini"));

        // Add tokens spent
        router.addUsedTokens(mContext, "Gemini", 25000);
        Assert.assertEquals(25000, router.getUsedTokens(mContext, "Gemini"));
        Assert.assertEquals(75000, router.getTokensLeft(mContext, "Gemini"));
    }
}
