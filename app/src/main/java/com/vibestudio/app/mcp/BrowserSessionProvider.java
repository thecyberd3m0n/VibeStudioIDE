package com.vibestudio.app.mcp;

import android.content.Context;
import android.webkit.WebView;

import com.vibestudio.app.browser.BrowserManager;
import com.vibestudio.app.fragments.BrowserFragment;
import com.vibestudio.app.tab.TabManager;
import com.vibestudio.app.tab.TabType;

public class BrowserSessionProvider implements ToolSessionProvider {

    @Override
    public ToolType getToolType() {
        return ToolType.BROWSER;
    }

    @Override
    public Object createSessionHandle(Context context) {
        return BrowserManager.getInstance().createWebView(context);
    }

    @Override
    public boolean isSessionAlive(Object handle) {
        return handle instanceof WebView && BrowserManager.getInstance().isWebViewAvailable((WebView) handle);
    }

    @Override
    public void openUiTab(Context context, int sessionId, Object handle) {
        if (handle instanceof WebView) {
            TabManager.getInstance().openTab(
                    TabType.BROWSER,
                    "Browser #" + sessionId,
                    "🌐",
                    BrowserFragment.newInstance((WebView) handle),
                    false
            );
        }
    }
}
