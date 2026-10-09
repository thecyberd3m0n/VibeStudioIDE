package com.vibestudio.app.test;

import android.content.Context;
import android.content.Intent;
import android.view.View;

import androidx.fragment.app.Fragment;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.vibestudio.app.R;
import com.vibestudio.app.activity.MainActivity;
import com.vibestudio.app.db.DatabaseHelper;
import com.vibestudio.app.fragments.BrowserFragment;
import com.vibestudio.app.fragments.ChatFragment;
import com.vibestudio.app.fragments.SettingsFragment;
import com.vibestudio.app.fragments.TerminalFragment;
import com.vibestudio.app.tab.TabItem;
import com.vibestudio.app.tab.TabManager;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class LauncherInstrumentationTest {

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

    private interface TestRunnable {
        void run(ActivityScenario<MainActivity> scenario);
    }

    @Test
    public void testLaunchAssistantTabAndReturnToLauncher() {
        checkOnboardingAndLaunch(scenario -> {
            scenario.onActivity(activity -> {
                View chatBtn = activity.findViewById(R.id.btn_launch_chat);
                Assert.assertNotNull("btn_launch_chat view should exist in LauncherFragment", chatBtn);
                Assert.assertEquals("btn_launch_chat should be visible", View.VISIBLE, chatBtn.getVisibility());

                // Click chat button to open Assistant tab
                chatBtn.performClick();
            });

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            scenario.onActivity(activity -> {
                // Verify active tab is ChatFragment
                TabItem activeTab = TabManager.getInstance().getActiveTab();
                Assert.assertNotNull("Active tab should exist after launching Assistant", activeTab);
                Fragment currentFragment = activeTab.getFragment();
                Assert.assertTrue("Active fragment should be instance of ChatFragment", currentFragment instanceof ChatFragment);
                Assert.assertTrue("ChatFragment should be added and visible", currentFragment.isAdded() && !currentFragment.isHidden());

                // Click launcher button to return to launcher
                View btnLauncher = activity.findViewById(R.id.btn_launcher_container);
                Assert.assertNotNull("btn_launcher_container view should exist", btnLauncher);
                btnLauncher.performClick();
            });

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            scenario.onActivity(activity -> {
                // Verify LauncherFragment is shown and launcher buttons are visible
                View chatBtnAgain = activity.findViewById(R.id.btn_launch_chat);
                Assert.assertNotNull("btn_launch_chat view should be visible again after returning to launcher", chatBtnAgain);
                Assert.assertEquals("btn_launch_chat should be visible", View.VISIBLE, chatBtnAgain.getVisibility());
                Assert.assertNull("Active tab selection should be cleared when on launcher", TabManager.getInstance().getActiveTab());
            });
        });
    }

    @Test
    public void testLaunchBrowserTabAndReturnToLauncher() {
        checkOnboardingAndLaunch(scenario -> {
            scenario.onActivity(activity -> {
                View browserBtn = activity.findViewById(R.id.btn_launch_browser);
                Assert.assertNotNull("btn_launch_browser view should exist in LauncherFragment", browserBtn);
                Assert.assertEquals("btn_launch_browser should be visible", View.VISIBLE, browserBtn.getVisibility());

                // Click browser button to open Browser tab
                browserBtn.performClick();
            });

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            scenario.onActivity(activity -> {
                // Verify active tab is BrowserFragment
                TabItem activeTab = TabManager.getInstance().getActiveTab();
                Assert.assertNotNull("Active tab should exist after launching Browser", activeTab);
                Fragment currentFragment = activeTab.getFragment();
                Assert.assertTrue("Active fragment should be instance of BrowserFragment", currentFragment instanceof BrowserFragment);
                Assert.assertTrue("BrowserFragment should be added and visible", currentFragment.isAdded() && !currentFragment.isHidden());

                // Click launcher button to return to launcher
                View btnLauncher = activity.findViewById(R.id.btn_launcher_container);
                Assert.assertNotNull("btn_launcher_container view should exist", btnLauncher);
                btnLauncher.performClick();
            });

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            scenario.onActivity(activity -> {
                // Verify LauncherFragment is shown and launcher buttons are visible
                View browserBtnAgain = activity.findViewById(R.id.btn_launch_browser);
                Assert.assertNotNull("btn_launch_browser view should be visible again after returning to launcher", browserBtnAgain);
                Assert.assertEquals("btn_launch_browser should be visible", View.VISIBLE, browserBtnAgain.getVisibility());
                Assert.assertNull("Active tab selection should be cleared when on launcher", TabManager.getInstance().getActiveTab());
            });
        });
    }

    @Test
    public void testLaunchTerminalTabAndReturnToLauncher() {
        checkOnboardingAndLaunch(scenario -> {
            scenario.onActivity(activity -> {
                View terminalBtn = activity.findViewById(R.id.btn_launch_terminal);
                Assert.assertNotNull("btn_launch_terminal view should exist in LauncherFragment", terminalBtn);
                Assert.assertEquals("btn_launch_terminal should be visible", View.VISIBLE, terminalBtn.getVisibility());

                // Click terminal button to open Terminal tab
                terminalBtn.performClick();
            });

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            scenario.onActivity(activity -> {
                // Verify active tab is TerminalFragment
                TabItem activeTab = TabManager.getInstance().getActiveTab();
                Assert.assertNotNull("Active tab should exist after launching Terminal", activeTab);
                Fragment currentFragment = activeTab.getFragment();
                Assert.assertTrue("Active fragment should be instance of TerminalFragment", currentFragment instanceof TerminalFragment);
                Assert.assertTrue("TerminalFragment should be added and visible", currentFragment.isAdded() && !currentFragment.isHidden());

                // Click launcher button to return to launcher
                View btnLauncher = activity.findViewById(R.id.btn_launcher_container);
                Assert.assertNotNull("btn_launcher_container view should exist", btnLauncher);
                btnLauncher.performClick();
            });

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            scenario.onActivity(activity -> {
                // Verify LauncherFragment is shown and launcher buttons are visible
                View terminalBtnAgain = activity.findViewById(R.id.btn_launch_terminal);
                Assert.assertNotNull("btn_launch_terminal view should be visible again after returning to launcher", terminalBtnAgain);
                Assert.assertEquals("btn_launch_terminal should be visible", View.VISIBLE, terminalBtnAgain.getVisibility());
                Assert.assertNull("Active tab selection should be cleared when on launcher", TabManager.getInstance().getActiveTab());
            });
        });
    }

    @Test
    public void testLaunchSettingsTabAndReturnToLauncher() {
        checkOnboardingAndLaunch(scenario -> {
            scenario.onActivity(activity -> {
                View settingsBtn = activity.findViewById(R.id.btn_launch_settings);
                Assert.assertNotNull("btn_launch_settings view should exist in LauncherFragment", settingsBtn);
                Assert.assertEquals("btn_launch_settings should be visible", View.VISIBLE, settingsBtn.getVisibility());

                // Click settings button to open Settings tab
                settingsBtn.performClick();
            });

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            scenario.onActivity(activity -> {
                // Verify active tab is SettingsFragment
                TabItem activeTab = TabManager.getInstance().getActiveTab();
                Assert.assertNotNull("Active tab should exist after launching Settings", activeTab);
                Fragment currentFragment = activeTab.getFragment();
                Assert.assertTrue("Active fragment should be instance of SettingsFragment", currentFragment instanceof SettingsFragment);
                Assert.assertTrue("SettingsFragment should be added and visible", currentFragment.isAdded() && !currentFragment.isHidden());

                // Click launcher button to return to launcher
                View btnLauncher = activity.findViewById(R.id.btn_launcher_container);
                Assert.assertNotNull("btn_launcher_container view should exist", btnLauncher);
                btnLauncher.performClick();
            });

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            scenario.onActivity(activity -> {
                // Verify LauncherFragment is shown and launcher buttons are visible
                View settingsBtnAgain = activity.findViewById(R.id.btn_launch_settings);
                Assert.assertNotNull("btn_launch_settings view should be visible again after returning to launcher", settingsBtnAgain);
                Assert.assertEquals("btn_launch_settings should be visible", View.VISIBLE, settingsBtnAgain.getVisibility());
                Assert.assertNull("Active tab selection should be cleared when on launcher", TabManager.getInstance().getActiveTab());
            });
        });
    }
}
