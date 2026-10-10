package com.vibestudio.app.browser;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.vibestudio.app.service.LogViewerService;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class BrowserManager {

    private static final String TAG = "BrowserManager";
    private static final String DEFAULT_URL = "https://google.com";
    private static final int MAX_BROWSER_LOGS = 200;
    private static BrowserManager sInstance;

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final Deque<String> mBrowserLogBuffer = new ArrayDeque<>();

    public interface BrowserStateListener {
        void onUrlChanged(WebView view, String newUrl);
        void onTitleChanged(WebView view, String title);
        void onFaviconChanged(WebView view, Bitmap favicon);
    }

    private final Map<WebView, BrowserStateListener> mStateListeners = new WeakHashMap<>();

    private BrowserManager() {}

    public static synchronized BrowserManager getInstance() {
        if (sInstance == null) {
            sInstance = new BrowserManager();
        }
        return sInstance;
    }

    public void setStateListener(WebView webView, BrowserStateListener listener) {
        if (webView == null) return;
        if (listener == null) {
            mStateListeners.remove(webView);
        } else {
            mStateListeners.put(webView, listener);
        }
    }

    public WebView createWebView(final Context context) {
        if (context == null) return null;

        final AtomicReference<WebView> ref = new AtomicReference<>();
        if (Looper.myLooper() == Looper.getMainLooper()) {
            ref.set(createWebViewInternal(context));
        } else {
            final CountDownLatch latch = new CountDownLatch(1);
            mMainHandler.post(() -> {
                try {
                    ref.set(createWebViewInternal(context));
                } finally {
                    latch.countDown();
                }
            });
            try {
                latch.await(3, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {}
        }

        return ref.get();
    }

    private WebView createWebViewInternal(Context context) {
        try {
            LogViewerService.getInstance().i("BrowserManager", "createWebViewInternal with context: " + context);
            WebView webView = new WebView(context);
            
            ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            webView.setLayoutParams(lp);

            setupWebViewSettings(webView);
            addBrowserLog("INFO", TAG, "WebView initialized.");
            webView.loadUrl(DEFAULT_URL);

            return webView;
        } catch (Exception e) {
            addBrowserLog("ERROR", TAG, "Failed to initialize WebView: " + e.getMessage());
            return null;
        }
    }

    private void setupWebViewSettings(final WebView webView) {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }

            @Deprecated
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                addBrowserLog("INFO", "WebViewPage", "Page load started: " + url);
                BrowserStateListener listener = mStateListeners.get(view);
                if (listener != null) {
                    listener.onUrlChanged(view, url);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                String title = view.getTitle();
                addBrowserLog("INFO", "WebViewPage", "Page load finished: " + url + (title != null ? " (" + title + ")" : ""));
                BrowserStateListener listener = mStateListeners.get(view);
                if (listener != null) {
                    listener.onUrlChanged(view, url);
                    if (title != null) {
                        listener.onTitleChanged(view, title);
                    }
                }
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                addBrowserLog("ERROR", "WebViewPage", "Error (" + errorCode + "): " + description + " at " + failingUrl);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request != null && request.isForMainFrame() && error != null) {
                    addBrowserLog("ERROR", "WebViewPage", "MainFrame Error (" + error.getErrorCode() + "): " + error.getDescription() + " at " + request.getUrl());
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                if (consoleMessage != null) {
                    String level = consoleMessage.messageLevel() != null ? consoleMessage.messageLevel().name() : "INFO";
                    String msg = consoleMessage.message() + " [" + consoleMessage.sourceId() + ":" + consoleMessage.lineNumber() + "]";
                    addBrowserLog(level, "JSConsole", msg);
                }
                return super.onConsoleMessage(consoleMessage);
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                super.onReceivedTitle(view, title);
                BrowserStateListener listener = mStateListeners.get(view);
                if (listener != null && title != null) {
                    listener.onTitleChanged(view, title);
                }
            }

            @Override
            public void onReceivedIcon(WebView view, Bitmap icon) {
                super.onReceivedIcon(view, icon);
                BrowserStateListener listener = mStateListeners.get(view);
                if (listener != null && icon != null) {
                    listener.onFaviconChanged(view, icon);
                }
            }
        });
    }

    public synchronized void addBrowserLog(String level, String tag, String message) {
        String logEntry = "[" + level + "] [" + tag + "]: " + message;
        if (mBrowserLogBuffer.size() >= MAX_BROWSER_LOGS) {
            mBrowserLogBuffer.removeFirst();
        }
        mBrowserLogBuffer.addLast(logEntry);
        LogViewerService.getInstance().i("BrowserLog", logEntry);
    }

    public synchronized String getBrowserLogs(int maxLines, String grepPattern, String levelFilter) {
        List<String> filtered = new ArrayList<>();
        for (String line : mBrowserLogBuffer) {
            if (line.trim().isEmpty()) continue;

            if (levelFilter != null && !levelFilter.isEmpty()) {
                if (!line.toUpperCase().contains("[" + levelFilter.toUpperCase() + "]")) {
                    continue;
                }
            }

            if (grepPattern != null && !grepPattern.isEmpty()) {
                if (!line.toLowerCase().contains(grepPattern.toLowerCase())) {
                    continue;
                }
            }

            filtered.add(line);
        }

        int start = Math.max(0, filtered.size() - maxLines);
        List<String> subList = filtered.subList(start, filtered.size());

        StringBuilder sb = new StringBuilder();
        for (String l : subList) {
            sb.append(l).append("\n");
        }
        return sb.toString();
    }


    public boolean isWebViewAvailable(WebView webView) {
        return webView != null;
    }

    public String getCurrentUrl(final WebView webView) {
        if (webView == null) return "Error: WebView not initialized.";

        final AtomicReference<String> urlRef = new AtomicReference<>("");
        final CountDownLatch latch = new CountDownLatch(1);

        mMainHandler.post(() -> {
            try {
                urlRef.set(webView.getUrl() != null ? webView.getUrl() : "");
            } catch (Exception e) {
                urlRef.set("");
            } finally {
                latch.countDown();
            }
        });

        try {
            latch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {}

        return urlRef.get();
    }

    public String setUserAgent(final WebView webView, final String userAgent) {
        if (webView == null) return "Error: WebView not initialized.";

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> resultRef = new AtomicReference<>("");

        mMainHandler.post(() -> {
            try {
                webView.getSettings().setUserAgentString(userAgent);
                String msg = "User-Agent updated successfully: " + userAgent;
                addBrowserLog("INFO", TAG, msg);
                resultRef.set(msg);
            } catch (Exception e) {
                String errorMsg = "Error setting User-Agent: " + e.getMessage();
                addBrowserLog("ERROR", TAG, errorMsg);
                resultRef.set(errorMsg);
            } finally {
                latch.countDown();
            }
        });

        try {
            latch.await(2, TimeUnit.SECONDS);
            return resultRef.get();
        } catch (InterruptedException e) {
            return "Error: Interrupted while setting User-Agent.";
        }
    }

    public String resetCookies() {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> resultRef = new AtomicReference<>("");

        mMainHandler.post(() -> {
            try {
                CookieManager cookieManager = CookieManager.getInstance();
                cookieManager.removeAllCookies(new ValueCallback<Boolean>() {
                    @Override
                    public void onReceiveValue(Boolean success) {
                        String msg = Boolean.TRUE.equals(success) ? "Cookies reset successfully." : "Failed to reset cookies.";
                        addBrowserLog("INFO", TAG, msg);
                        resultRef.set(msg);
                        latch.countDown();
                    }
                });
                cookieManager.flush();
            } catch (Exception e) {
                String errorMsg = "Error resetting cookies: " + e.getMessage();
                addBrowserLog("ERROR", TAG, errorMsg);
                resultRef.set(errorMsg);
                latch.countDown();
            }
        });

        try {
            if (latch.await(3, TimeUnit.SECONDS)) {
                return resultRef.get();
            } else {
                return "Error: Timeout while resetting cookies.";
            }
        } catch (InterruptedException e) {
            return "Error: Interrupted while resetting cookies.";
        }
    }

    public String navigate(final WebView webView, final String url) {
        if (webView == null) return "Error: WebView not initialized.";

        String finalUrl = url;
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            if (url.startsWith("localhost") || url.startsWith("127.0.0.1")) {
                finalUrl = "http://" + url;
            } else {
                finalUrl = "https://" + url;
            }
        }

        final String urlToLoad = finalUrl;
        mMainHandler.post(() -> {
            addBrowserLog("INFO", TAG, "Navigating to: " + urlToLoad);
            webView.loadUrl(urlToLoad);
        });
        return "Navigating to " + urlToLoad;
    }

    public String executeJs(final WebView webView, final String script) {
        if (webView == null) return "Error: WebView not initialized.";

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> resultRef = new AtomicReference<>("");

        mMainHandler.post(() -> {
            try {
                addBrowserLog("DEBUG", TAG, "Evaluating JS: " + script);
                webView.evaluateJavascript(script, new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String value) {
                        String res = value != null ? value : "";
                        addBrowserLog("DEBUG", TAG, "JS Result: " + res);
                        resultRef.set(res);
                        latch.countDown();
                    }
                });
            } catch (Exception e) {
                String errorMsg = "Error executing JS: " + e.getMessage();
                addBrowserLog("ERROR", TAG, errorMsg);
                resultRef.set(errorMsg);
                latch.countDown();
            }
        });

        try {
            if (latch.await(5, TimeUnit.SECONDS)) {
                return resultRef.get();
            } else {
                return "Error: JS execution timed out.";
            }
        } catch (InterruptedException e) {
            return "Error: JS execution interrupted.";
        }
    }

    public String click(final WebView webView, final String selector) {
        if (webView == null) return "Error: WebView not initialized.";

        addBrowserLog("INFO", TAG, "Clicking selector: " + selector);
        String script = "(function() { " +
                "  var el = document.querySelector('" + selector.replace("'", "\\'") + "'); " +
                "  if (!el) return 'Element not found: " + selector.replace("'", "\\'") + "'; " +
                "  el.click(); " +
                "  return 'Clicked successfully'; " +
                "})();";
        return executeJs(webView, script);
    }

    public String type(final WebView webView, final String selector, final String text) {
        if (webView == null) return "Error: WebView not initialized.";

        addBrowserLog("INFO", TAG, "Typing text into selector: " + selector);
        String escapedText = text.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n");
        String script = "(function() { " +
                "  var el = document.querySelector('" + selector.replace("'", "\\'") + "'); " +
                "  if (!el) return 'Element not found: " + selector.replace("'", "\\'") + "'; " +
                "  el.focus(); " +
                "  var nativeSetter = (Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value') || {}).set " +
                "    || (Object.getOwnPropertyDescriptor(window.HTMLTextAreaElement.prototype, 'value') || {}).set; " +
                "  if (nativeSetter) { nativeSetter.call(el, '" + escapedText + "'); } else { el.value = '" + escapedText + "'; } " +
                "  el.dispatchEvent(new Event('input', { bubbles: true })); " +
                "  el.dispatchEvent(new Event('change', { bubbles: true })); " +
                "  return 'Text entered successfully'; " +
                "})();";
        return executeJs(webView, script);
    }

    public JSONObject takeScreenshot(final WebView webView, final Context context) {
        final JSONObject result = new JSONObject();
        if (webView == null) {
            try {
                result.put("status", "error");
                result.put("message", "WebView not initialized.");
            } catch (Exception ignored) {}
            return result;
        }

        final CountDownLatch latch = new CountDownLatch(1);

        mMainHandler.post(() -> {
            try {
                addBrowserLog("INFO", TAG, "Taking WebView screenshot.");
                int width = webView.getWidth();
                int height = webView.getHeight();
                if (width <= 0) width = 1080;
                if (height <= 0) height = 1920;

                if (webView.getWidth() <= 0 || webView.getHeight() <= 0) {
                    webView.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
                    webView.layout(0, 0, width, height);
                }

                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                webView.draw(canvas);

                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, outputStream);
                byte[] byteArray = outputStream.toByteArray();
                String base64Image = Base64.encodeToString(byteArray, Base64.NO_WRAP);

                String filePath = "";
                if (context != null) {
                    File file = new File(context.getCacheDir(), "webview_screenshot_" + System.currentTimeMillis() + ".png");
                    FileOutputStream fos = new FileOutputStream(file);
                    fos.write(byteArray);
                    fos.close();
                    filePath = file.getAbsolutePath();
                }

                result.put("status", "success");
                result.put("base64", base64Image);
                result.put("file_path", filePath);
                result.put("width", width);
                result.put("height", height);

            } catch (Exception e) {
                try {
                    result.put("status", "error");
                    result.put("message", e.getMessage());
                } catch (Exception ignored) {}
            } finally {
                latch.countDown();
            }
        });

        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {}

        return result;
    }

    public void closeWebView(final WebView webView) {
        if (webView == null) return;
        mStateListeners.remove(webView);
        mMainHandler.post(() -> {
            try {
                webView.destroy();
            } catch (Exception ignored) {}
        });
    }
}
