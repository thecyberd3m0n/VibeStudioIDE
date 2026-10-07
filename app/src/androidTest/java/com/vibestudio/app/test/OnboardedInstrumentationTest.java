package com.vibestudio.app.test;

import android.content.Context;
import android.content.Intent;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.vibestudio.app.R;
import com.vibestudio.app.activity.MainActivity;
import com.vibestudio.app.db.DatabaseHelper;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class OnboardedInstrumentationTest {

    @Test
    public void testClickLauncherButtonDisplaysLauncherOverlay() {
        Context context = ApplicationProvider.getApplicationContext();
        DatabaseHelper dbHelper = new DatabaseHelper(context);

        // Skip test if onboarding is not completed
        Assume.assumeTrue("Skipping Onboarded test because onboarding is not done yet", dbHelper.isEnvInitialized());

        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                Assert.assertNotNull("MainActivity should launch", activity);

                View btnLauncher = activity.findViewById(R.id.btn_launcher_container);
                Assert.assertNotNull("btn_launcher_container view should exist", btnLauncher);

                // Perform click on launcher button on UI thread
                btnLauncher.performClick();

                // Assert launcher fragment buttons are now in view
                View chatBtn = activity.findViewById(R.id.btn_launch_chat);
                Assert.assertNotNull("btn_launch_chat view should be inflated/visible after clicking launcher button", chatBtn);
                Assert.assertEquals("btn_launch_chat should be visible", View.VISIBLE, chatBtn.getVisibility());
            });
        }
    }
}
