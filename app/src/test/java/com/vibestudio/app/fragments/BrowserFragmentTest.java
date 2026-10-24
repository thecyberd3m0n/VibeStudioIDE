package com.vibestudio.app.fragments;

import android.os.Looper;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;

import androidx.fragment.app.FragmentActivity;

import com.vibestudio.app.R;
import com.vibestudio.app.browser.BrowserManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = "app/src/main/AndroidManifest.xml")
public class BrowserFragmentTest {

    private FragmentActivity mActivity;
    private BrowserFragment mFragment;
    private WebView mWebView;

    @Before
    public void setUp() {
        mActivity = Robolectric.buildActivity(FragmentActivity.class).create().start().resume().get();
        mWebView = BrowserManager.getInstance().createWebView(mActivity);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        mFragment = BrowserFragment.newInstance(mWebView);

        mActivity.getSupportFragmentManager()
                .beginTransaction()
                .add(android.R.id.content, mFragment)
                .commitNow();
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
    }

    @Test
    public void testGoButtonClickNavigatesWebView() {
        EditText editUrl = mFragment.getView().findViewById(R.id.edit_url);
        Button btnGo = mFragment.getView().findViewById(R.id.btn_go);

        assertNotNull(editUrl);
        assertNotNull(btnGo);

        editUrl.setText("example.com");
        btnGo.performClick();
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        assertEquals("https://example.com", mWebView.getUrl());
    }

    @Test
    public void testEnterKeyOrActionGoNavigatesWebView() {
        EditText editUrl = mFragment.getView().findViewById(R.id.edit_url);
        assertNotNull(editUrl);

        editUrl.setText("http://localhost:8080");

        // Simulate actionGo
        editUrl.onEditorAction(EditorInfo.IME_ACTION_GO);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        assertEquals("http://localhost:8080", mWebView.getUrl());

        // Simulate ENTER key down
        editUrl.setText("google.com");
        KeyEvent enterDown = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER);
        editUrl.dispatchKeyEvent(enterDown);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        assertEquals("https://google.com", mWebView.getUrl());
    }
}
