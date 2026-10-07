package com.vibestudio.app.test;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.View;
import android.widget.Button;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;

import com.vibestudio.app.R;
import com.vibestudio.app.db.DatabaseHelper;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class OnboardingInstrumentationTest {

    private Activity getCurrentActivity() {
        AtomicReference<Activity> currentActivity = new AtomicReference<>();
        Runnable runnable = () -> {
            Collection<Activity> resumedActivities =
                    ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED);
            if (!resumedActivities.isEmpty()) {
                currentActivity.set(resumedActivities.iterator().next());
            } else {
                Collection<Activity> createdActivities =
                        ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.CREATED);
                if (!createdActivities.isEmpty()) {
                    currentActivity.set(createdActivities.iterator().next());
                }
            }
        };

        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(runnable);
        }
        return currentActivity.get();
    }

    @Test
    public void testNewUserLandsOnOnboardingActivityAndCompletesOnboarding() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        DatabaseHelper dbHelper = new DatabaseHelper(context);
        
        // Skip test if onboarding is already completed
        Assume.assumeFalse("Skipping Onboarding test because onboarding is already completed", dbHelper.isEnvInitialized());

        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage("com.vibestudio.app");
        Assert.assertNotNull("Launch intent for com.vibestudio.app should not be null", launchIntent);
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // Launch app activity directly using Android system context
        context.startActivity(launchIntent);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        // Step 1: Verify on Step 1, click Next
        long step1Timeout = 10000;
        long startTime = System.currentTimeMillis();
        Activity onboardingActivity = null;

        while (System.currentTimeMillis() - startTime < step1Timeout) {
            onboardingActivity = getCurrentActivity();
            if (onboardingActivity != null && "com.vibestudio.app.activity.OnboardingActivity".equals(onboardingActivity.getClass().getName())) {
                break;
            }
            Thread.sleep(200);
        }

        Assert.assertNotNull("App should start OnboardingActivity for new user", onboardingActivity);
        Assert.assertEquals("New user should land on OnboardingActivity", 
                "com.vibestudio.app.activity.OnboardingActivity", 
                onboardingActivity.getClass().getName());

        final Activity act1 = onboardingActivity;
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            View step1Layout = act1.findViewById(R.id.step1_layout);
            Assert.assertNotNull("step1_layout should exist in OnboardingActivity", step1Layout);
            Assert.assertEquals("Step 1 should be visible initially", View.VISIBLE, step1Layout.getVisibility());

            Button btnNext = act1.findViewById(R.id.btn_next);
            btnNext.performClick();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        // Step 2: Verify on Step 2, click Next to trigger background installation
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Activity current = getCurrentActivity();
            Assert.assertNotNull("Activity should remain active on step 2", current);
            View step2Layout = current.findViewById(R.id.step2_layout);
            Assert.assertEquals("Step 2 should be visible after clicking Next", View.VISIBLE, step2Layout.getVisibility());

            Button btnNext = current.findViewById(R.id.btn_next);
            btnNext.performClick();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        // Step 3: Wait for environment installation background thread to finish safely
        long timeoutMs = 120000; // 2 minutes max for libtermux bootstrap extraction
        startTime = System.currentTimeMillis();
        final boolean[] isCompleted = new boolean[]{false};

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            Activity current = getCurrentActivity();
            if (current != null && !current.isDestroyed() && !current.isFinishing()) {
                InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                    Button btnNext = current.findViewById(R.id.btn_next);
                    if (btnNext != null && btnNext.isEnabled() && "Finish & Launch".equals(btnNext.getText().toString())) {
                        isCompleted[0] = true;
                    }
                });
            }

            if (isCompleted[0]) {
                break;
            }
            Thread.sleep(500);
        }

        Assert.assertTrue("Environment installation should complete within timeout", isCompleted[0]);

        // Step 4: Click 'Finish & Launch' button
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            Activity current = getCurrentActivity();
            if (current != null && !current.isDestroyed() && !current.isFinishing()) {
                Button btnNext = current.findViewById(R.id.btn_next);
                if (btnNext != null && btnNext.isEnabled()) {
                    btnNext.performClick();
                }
            }
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        // Step 5: Poll and verify user lands on MainActivity
        long mainLaunchTimeout = 20000;
        startTime = System.currentTimeMillis();
        Activity mainActivity = null;

        while (System.currentTimeMillis() - startTime < mainLaunchTimeout) {
            Activity current = getCurrentActivity();
            if (current != null && "com.vibestudio.app.activity.MainActivity".equals(current.getClass().getName())) {
                mainActivity = current;
                break;
            }
            Thread.sleep(300);
        }

        Assert.assertNotNull("MainActivity should launch after completing onboarding", mainActivity);
        Assert.assertEquals("After completing onboarding, user should land on MainActivity",
                "com.vibestudio.app.activity.MainActivity",
                mainActivity.getClass().getName());

        final Activity finalMainActivity = mainActivity;
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            View launcherContainer = finalMainActivity.findViewById(R.id.btn_launcher_container);
            View launcherChatBtn = finalMainActivity.findViewById(R.id.btn_launch_chat);
            Assert.assertTrue("User should see MainActivity layout elements (btn_launcher_container or btn_launch_chat in LauncherFragment)",
                    launcherContainer != null || launcherChatBtn != null);
        });
    }
}
