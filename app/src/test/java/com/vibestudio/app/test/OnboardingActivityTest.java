package com.vibestudio.app.test;

import android.view.View;
import android.widget.Button;

import com.vibestudio.app.R;
import com.vibestudio.app.activity.OnboardingActivity;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = "app/src/main/AndroidManifest.xml", shadows = {ShadowDatabaseHelper.class})
public class OnboardingActivityTest {

    @Test
    public void testOnboardingOpensAndDisplaysNextButton() {
        OnboardingActivity activity = Robolectric.buildActivity(OnboardingActivity.class)
                .create()
                .start()
                .resume()
                .get();

        Assert.assertNotNull("Activity should not be null", activity);

        Button btnNext = activity.findViewById(R.id.btn_next);
        Assert.assertNotNull("btn_next button should exist in layout", btnNext);

        // Print text representation of displayed UI tree (No expensive screenshots!)
        String uiDump = ViewTreePrinter.dump(activity.getWindow().getDecorView());
        System.out.println("=== UI Tree Dump ===");
        System.out.println(uiDump);

        // Assertions for UI content and state
        Assert.assertEquals("Button text should be 'Next'", "Next", btnNext.getText().toString());
        Assert.assertEquals("Button should be visible", View.VISIBLE, btnNext.getVisibility());
        Assert.assertTrue("Button should be enabled", btnNext.isEnabled());
    }

    @Test
    public void testStep4VersionControlConfigurationLayoutAndControls() {
        OnboardingActivity activity = Robolectric.buildActivity(OnboardingActivity.class)
                .create()
                .start()
                .resume()
                .get();

        Button btnNext = activity.findViewById(R.id.btn_next);
        Button btnBack = activity.findViewById(R.id.btn_back);
        Button btnConfigGitUser = activity.findViewById(R.id.btn_config_git_user);
        Button btnConfigSshKeys = activity.findViewById(R.id.btn_config_ssh_keys);
        View step4Layout = activity.findViewById(R.id.step4_layout);

        Assert.assertNotNull("btn_config_git_user should exist", btnConfigGitUser);
        Assert.assertNotNull("btn_config_ssh_keys should exist", btnConfigSshKeys);
        Assert.assertNotNull("step4_layout should exist", step4Layout);

        // Navigate step 1 -> step 2
        btnNext.performClick();
        Assert.assertEquals("Back button should show 'Back' on step 2", "Back", btnBack.getText().toString());

        // Navigate step 2 -> step 3
        btnNext.performClick();
        Assert.assertEquals("Next button should say 'Installing...'", "Installing...", btnNext.getText().toString());
    }
}
