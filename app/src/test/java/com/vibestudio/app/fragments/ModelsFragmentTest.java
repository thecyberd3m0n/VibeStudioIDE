package com.vibestudio.app.fragments;

import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;

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
    public void testModelsFragmentHidesModelsWhenNoApiKey() {
        mDbHelper.saveApiKey("Gemini", "");

        ModelsFragment fragment = new ModelsFragment();
        mActivity.getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, fragment)
                .commitNow();

        View root = fragment.getView();
        Assert.assertNotNull("Fragment view should not be null", root);

        // Find key button with symbol 🔑
        Button keyBtn = findButtonWithText(root, "🔑");
        Assert.assertNotNull("Key icon button 🔑 should exist", keyBtn);

        // Find toggle button ▼ or ▲
        Button toggleBtn = findButtonWithTextContaining(root, "▼");
        if (toggleBtn == null) {
            toggleBtn = findButtonWithTextContaining(root, "▲");
        }
        Assert.assertNotNull("Collapse toggle button should exist", toggleBtn);

        // Models should not be rendered when no API key
        TextView modelText = findTextViewWithTextContaining(root, "gemini-2.5-flash");
        Assert.assertNull("Model should not be displayed when API key is missing", modelText);
    }

    @Test
    public void testModelsFragmentDisplaysCustomModelsWhenApiKeyPresent() {
        mDbHelper.saveApiKey("Gemini", "test_key_123");

        ModelsFragment fragment = new ModelsFragment();
        mActivity.getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, fragment)
                .commitNow();

        View root = fragment.getView();
        Assert.assertNotNull("Fragment view should not be null", root);

        TextView modelText = findTextViewWithTextContaining(root, "gemini-2.5-flash");
        Assert.assertNotNull("Model should be displayed when API key is present", modelText);
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

    private Button findButtonWithTextContaining(View view, String text) {
        if (view instanceof Button) {
            Button b = (Button) view;
            if (b.getText() != null && b.getText().toString().contains(text)) {
                return b;
            }
        } else if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                Button res = findButtonWithTextContaining(vg.getChildAt(i), text);
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
