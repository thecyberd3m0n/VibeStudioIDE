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
}
