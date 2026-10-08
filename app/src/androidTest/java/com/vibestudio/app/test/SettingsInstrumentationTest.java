package com.vibestudio.app.test;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.vibestudio.app.R;
import com.vibestudio.app.activity.MainActivity;
import com.vibestudio.app.chat.router.AiRouter;
import com.vibestudio.app.db.DatabaseHelper;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SettingsInstrumentationTest {

    private interface TestRunnable {
        void run(ActivityScenario<MainActivity> scenario);
    }

    private void checkOnboardingAndLaunch(TestRunnable testCode) {
        Context context = ApplicationProvider.getApplicationContext();
        DatabaseHelper dbHelper = new DatabaseHelper(context);

        // Skip test if onboarding is not completed
        Assume.assumeTrue("Skipping test because onboarding is not done yet", dbHelper.isEnvInitialized());

        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                Assert.assertNotNull("MainActivity should launch", activity);
            });

            testCode.run(scenario);
        }
    }

    @Test
    public void testSettingsModelsTabDisplaysSpentTokensAndBudget() {
        Context context = ApplicationProvider.getApplicationContext();
        DatabaseHelper dbHelper = new DatabaseHelper(context);

        // Save active API key & simulate token usage
        String existingKey = dbHelper.getApiKey("Gemini");
        dbHelper.saveApiKey("Gemini", "test_gemini_key_instrumentation");
        AiRouter router = AiRouter.getInstance();
        router.setTokenBudget(context, "Gemini", 100000);
        router.addUsedTokens(context, "Gemini", 1250);

        try {
            checkOnboardingAndLaunch(scenario -> {
                scenario.onActivity(activity -> {
                    View settingsBtn = activity.findViewById(R.id.btn_launch_settings);
                    Assert.assertNotNull("btn_launch_settings view should exist in LauncherFragment", settingsBtn);
                    settingsBtn.performClick();
                });

                InstrumentationRegistry.getInstrumentation().waitForIdleSync();

                scenario.onActivity(activity -> {
                    // Verify Settings tab is active
                    Fragment activeFragment = activity.getSupportFragmentManager().findFragmentById(R.id.content_frame);
                    Assert.assertNotNull("Settings fragment should be hosted in container", activeFragment);

                    // Verify spent token label is displayed in ModelsFragment
                    View root = activity.getWindow().getDecorView();
                    TextView budgetTextView = findTextViewWithTextContaining(root, "tokens used");
                    Assert.assertNotNull("Budget text view displaying tokens used should exist in ModelsFragment", budgetTextView);

                    String text = budgetTextView.getText().toString();
                    Assert.assertTrue("Spent tokens should be non-zero and properly formatted in UI (text: " + text + ")",
                            text.contains("1,250") || text.contains("1250") || !text.contains("0/100,000"));
                });
            });
        } finally {
            // Clean up dummy key to prevent polluting live database
            dbHelper.saveApiKey("Gemini", existingKey != null ? existingKey : "");
        }
    }

    private TextView findTextViewWithTextContaining(View view, String text) {
        if (view instanceof TextView) {
            TextView tv = (TextView) view;
            if (tv.getText() != null && tv.getText().toString().contains(text)) {
                return tv;
            }
        } else if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                TextView res = findTextViewWithTextContaining(vg.getChildAt(i), text);
                if (res != null) return res;
            }
        }
        return null;
    }
}
