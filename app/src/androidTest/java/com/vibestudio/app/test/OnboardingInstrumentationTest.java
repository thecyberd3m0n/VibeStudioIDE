package com.vibestudio.app.test;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.Button;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.vibestudio.app.R;
import com.vibestudio.app.activity.MainActivity;
import com.vibestudio.app.db.DatabaseHelper;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class OnboardingInstrumentationTest {

    @Test
    public void testNewUserLandsOnOnboardingActivityAndCompletesOnboarding() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        DatabaseHelper dbHelper = new DatabaseHelper(context);
        
        // Skip test if onboarding is already completed
        Assume.assumeFalse("Skipping Onboarding test because onboarding is already completed", dbHelper.isEnvInitialized());

        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage("com.vibestudio.app");
        Assert.assertNotNull("Launch intent for com.vibestudio.app should not be null", launchIntent);
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try (ActivityScenario<?> scenario = ActivityScenario.launch(launchIntent)) {

            // Step 1: Verify on Step 1, click Next
            scenario.onActivity(activity -> {
                Assert.assertNotNull("App main launch activity should start for new user", activity);
                Assert.assertEquals("New user should land on OnboardingActivity", 
                        "com.vibestudio.app.activity.OnboardingActivity", 
                        activity.getClass().getName());

                View step1Layout = activity.findViewById(R.id.step1_layout);
                Assert.assertNotNull("step1_layout should exist in OnboardingActivity", step1Layout);
                Assert.assertEquals("Step 1 should be visible initially", View.VISIBLE, step1Layout.getVisibility());

                Button btnNext = activity.findViewById(R.id.btn_next);
                btnNext.performClick();
            });

            // Step 2: Verify on Step 2, click Next to trigger background installation
            scenario.onActivity(activity -> {
                View step2Layout = activity.findViewById(R.id.step2_layout);
                Assert.assertEquals("Step 2 should be visible after clicking Next", View.VISIBLE, step2Layout.getVisibility());

                Button btnNext = activity.findViewById(R.id.btn_next);
                btnNext.performClick();
            });

            // Step 3: Wait for environment installation background thread to finish safely using scenario.onActivity
            long timeoutMs = 120000; // 2 minutes max for libtermux bootstrap extraction
            long startTime = System.currentTimeMillis();
            final boolean[] isCompleted = new boolean[]{false};

            while (System.currentTimeMillis() - startTime < timeoutMs) {
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();

                if (scenario.getState() == Lifecycle.State.DESTROYED) {
                    break;
                }

                try {
                    scenario.onActivity(activity -> {
                        if (!activity.isDestroyed() && !activity.isFinishing()) {
                            Button btnNext = activity.findViewById(R.id.btn_next);
                            if (btnNext != null && btnNext.isEnabled() && "Finish & Launch".equals(btnNext.getText().toString())) {
                                isCompleted[0] = true;
                            }
                        }
                    });
                } catch (Throwable ignored) {
                }

                if (isCompleted[0]) {
                    break;
                }
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {}
            }

            Assert.assertTrue("Environment installation should complete within timeout", isCompleted[0]);

            // Step 4: Click 'Finish & Launch' button on UI thread
            if (scenario.getState() != Lifecycle.State.DESTROYED) {
                try {
                    scenario.onActivity(activity -> {
                        Button btnNext = activity.findViewById(R.id.btn_next);
                        if (btnNext != null && btnNext.isEnabled()) {
                            btnNext.performClick();
                        }
                    });
                } catch (Throwable ignored) {
                }
            }
        }

        // Step 5: Verify subsequent launch now opens MainActivity directly
        Intent secondLaunchIntent = context.getPackageManager().getLaunchIntentForPackage("com.vibestudio.app");
        secondLaunchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try (ActivityScenario<MainActivity> mainScenario = ActivityScenario.launch(secondLaunchIntent)) {
            mainScenario.onActivity(mainActivity -> {
                Assert.assertNotNull("MainActivity should launch after onboarding finishes", mainActivity);
                Assert.assertEquals("After completing onboarding, user should land on MainActivity",
                        "com.vibestudio.app.activity.MainActivity",
                        mainActivity.getClass().getName());
            });
        }
    }
}
