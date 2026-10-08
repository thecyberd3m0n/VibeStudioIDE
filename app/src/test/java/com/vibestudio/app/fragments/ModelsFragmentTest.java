package com.vibestudio.app.fragments;

import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;

import com.vibestudio.app.chat.router.AiRouter;
import com.vibestudio.app.db.DatabaseHelper;
import com.vibestudio.app.test.ShadowDatabaseHelper;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = "app/src/main/AndroidManifest.xml", shadows = {ShadowDatabaseHelper.class})
public class ModelsFragmentTest {

    private FragmentActivity mActivity;
    private DatabaseHelper mDbHelper;

    @Before
    public void setUp() {
        ShadowDatabaseHelper.reset();
        mActivity = Robolectric.buildActivity(FragmentActivity.class).create().start().resume().get();
        mDbHelper = new DatabaseHelper(RuntimeEnvironment.application);
    }

    @Test
    public void testModelsFragmentHidesModelsAndBudgetWhenNoApiKey() {
        mDbHelper.saveApiKey("Gemini", "");
        AiRouter.getInstance().setTokenBudget(RuntimeEnvironment.application, "Gemini", 100000);

        ModelsFragment fragment = new ModelsFragment();
        mActivity.getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, fragment)
                .commitNow();

        View root = fragment.getView();
        Assert.assertNotNull("Fragment view should not be null", root);

        // Find key button with symbol 🔑
        Button keyBtn = findButtonWithText(root, "🔑");
        Assert.assertNotNull("Key icon button 🔑 should exist", keyBtn);

        // Models should not be rendered when no API key
        TextView modelText = findTextViewWithTextContaining(root, "gemini-1.5-flash");
        Assert.assertNull("Model should not be displayed when API key is missing", modelText);

        // Budget should not be rendered when no API key
        TextView budgetText = findTextViewWithTextContaining(root, "tokens used");
        Assert.assertNull("Budget bar should not be shown when API key is missing", budgetText);
    }

    @Test
    public void testModelsFragmentDisplaysCustomModelsAndBudgetWhenApiKeyPresent() {
        mDbHelper.saveApiKey("Gemini", "test_key_123");
        AiRouter.getInstance().setTokenBudget(RuntimeEnvironment.application, "Gemini", 100000);
        AiRouter.getInstance().addUsedTokens(RuntimeEnvironment.application, "Gemini", 500);

        ModelsFragment fragment = new ModelsFragment();
        mActivity.getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, fragment)
                .commitNow();

        View root = fragment.getView();
        Assert.assertNotNull("Fragment view should not be null", root);

        TextView modelText = findTextViewWithTextContaining(root, "gemini-1.5-flash");
        Assert.assertNotNull("Model should be displayed when API key is present", modelText);

        TextView budgetText = findTextViewWithTextContaining(root, "tokens used");
        Assert.assertNotNull("Budget bar should be shown when API key is present and budget is set", budgetText);
    }

    @Test
    public void testClickingProviderTitleTriggersApiKeyDialogWhenNoKey() {
        mDbHelper.saveApiKey("Gemini", "");

        ModelsFragment fragment = new ModelsFragment();
        mActivity.getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, fragment)
                .commitNow();

        View root = fragment.getView();
        Assert.assertNotNull("Fragment view should not be null", root);

        TextView titleView = findTextViewWithTextContaining(root, "Gemini");
        Assert.assertNotNull("Provider title should exist", titleView);

        titleView.performClick();

        android.app.AlertDialog dialog = org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
        Assert.assertNotNull("AlertDialog for API key entry should be shown on title click when key is missing", dialog);
    }

    private Button findButtonWithText(View view, String text) {
        if (view instanceof Button) {
            Button b = (Button) view;
            if (text.equals(b.getText().toString())) {
                return b;
            }
        } else if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                Button res = findButtonWithText(vg.getChildAt(i), text);
                if (res != null) return res;
            }
        }
        return null;
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
