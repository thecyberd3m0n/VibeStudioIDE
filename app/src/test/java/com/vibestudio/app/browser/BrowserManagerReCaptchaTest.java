package com.vibestudio.app.browser;

import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;

import androidx.fragment.app.FragmentActivity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, manifest = "app/src/main/AndroidManifest.xml")
public class BrowserManagerReCaptchaTest {

    private FragmentActivity mActivity;
    private WebView mWebView;

    @Before
    public void setUp() {
        mActivity = Robolectric.buildActivity(FragmentActivity.class).create().start().resume().get();
        mWebView = BrowserManager.getInstance().createWebView(mActivity);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
    }

    @Test
    public void testReCaptchaWebSettingsCompliance() {
        assertNotNull("WebView should not be null", mWebView);
        WebSettings settings = mWebView.getSettings();

        // 1. Storage & JS APIs required by reCAPTCHA
        assertTrue("javaScriptEnabled must be true for reCAPTCHA runtime scripts", settings.getJavaScriptEnabled());
        assertTrue("javaScriptCanOpenWindowsAutomatically must be true", settings.getJavaScriptCanOpenWindowsAutomatically());
        assertTrue("domStorageEnabled must be true for reCAPTCHA token handshakes", settings.getDomStorageEnabled());
        assertTrue("databaseEnabled must be true for client storage", settings.getDatabaseEnabled());
        assertEquals("cacheMode must be WebSettings.LOAD_DEFAULT", WebSettings.LOAD_DEFAULT, settings.getCacheMode());

        // 2. Multi-window / popup support for reCAPTCHA visual challenges
        assertTrue("supportMultipleWindows must be true for reCAPTCHA challenge popups", settings.supportMultipleWindows());
    }

    @Test
    public void testCookieManagerAcceptsCookies() {
        CookieManager cookieManager = CookieManager.getInstance();
        assertNotNull(cookieManager);
        assertTrue("CookieManager acceptCookie must be true", cookieManager.acceptCookie());
    }
}
