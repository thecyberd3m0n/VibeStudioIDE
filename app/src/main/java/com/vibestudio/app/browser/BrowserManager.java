package com.vibestudio.app.browser;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class BrowserManager {

    private static final String TAG = "BrowserManager";
    private static final String DEFAULT_URL = "https://google.com";
    private static final int MAX_BROWSER_LOGS = 200;
    private static BrowserManager sInstance;

    private WebView mWebView;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final Deque<String> mBrowserLogBuffer = new ArrayDeque<>();

    public interface UrlChangeListener {
        void onUrlChanged(String newUrl);
    }

    private UrlChangeListener mUrlChangeListener;

    private BrowserManager() {}

    public static synchronized BrowserManager getInstance() {
        if (sInstance == null) {
            sInstance = new BrowserManager();
        }
        return sInstance;
    }

    public void setUrlChangeListener(UrlChangeListener listener) {
        this.mUrlChangeListener = listener;
    }

    public synchronized void ensureInitialized(final Context context) {
        if (mWebView != null) return;

        final CountDownLatch latch = new CountDownLatch(1);
        mMainHandler.post(() -> {
            try {
                if (mWebView == null) {
                    Context appContext = context.getApplicationContext();
                    mWebView = new WebView(appContext);
                    
                    // Invisible off-screen layout params so rendering engine functions fully
                    ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT);
                    mWebView.setLayoutParams(lp);

                    setupWebViewSettings(mWebView);
                    addBrowserLog("INFO", TAG, "Global WebView initialized on MainActivity start.");
                    mWebView.loadUrl(DEFAULT_URL);
                }
            } catch (Exception e) {
                addBrowserLog("ERROR", TAG, "Failed to initialize global WebView: " + e.getMessage());
            } finally {
                latch.countDown();
            }
        });

        try {
            latch.await(3, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {}
    }

    private void setupWebViewSettings(WebView webView) {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                addBrowserLog("INFO", "WebViewPage", "Page load started: " + url);
                if (mUrlChangeListener != null) {
                    mUrlChangeListener.onUrlChanged(url);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                addBrowserLog("INFO", "WebViewPage", "Page load finished: " + url);
                if (mUrlChangeListener != null) {
                    mUrlChangeListener.onUrlChanged(url);
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

    public synchronized void clearBrowserLogs() {
        mBrowserLogBuffer.clear();
    }

    public WebView getWebView() {
        return mWebView;
    }

    public boolean isWebViewAvailable() {
        return mWebView != null;
    }

    public String getCurrentUrl() {
        if (mWebView == null) return "Error: WebView not initialized or active.";

        final AtomicReference<String> urlRef = new AtomicReference<>("");
        final CountDownLatch latch = new CountDownLatch(1);

        mMainHandler.post(() -> {
            try {
                urlRef.set(mWebView.getUrl() != null ? mWebView.getUrl() : "");
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

    public String setUserAgent(final String userAgent) {
        if (mWebView == null) return "Error: WebView not initialized or active.";

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> resultRef = new AtomicReference<>("");

        mMainHandler.post(() -> {
            try {
                mWebView.getSettings().setUserAgentString(userAgent);
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

    public String navigate(final String url) {
        if (mWebView == null) return "Error: WebView not initialized or active.";

        final String finalUrl = (!url.startsWith("http://") && !url.startsWith("https://"))
                ? "https://" + url : url;

        mMainHandler.post(() -> {
            addBrowserLog("INFO", TAG, "Navigating to: " + finalUrl);
            mWebView.loadUrl(finalUrl);
        });
        return "Navigating to " + finalUrl;
    }

    public String executeJs(final String script) {
        if (mWebView == null) return "Error: WebView not initialized or active.";

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> resultRef = new AtomicReference<>("");

        mMainHandler.post(() -> {
            try {
                addBrowserLog("DEBUG", TAG, "Evaluating JS: " + script);
                mWebView.evaluateJavascript(script, new ValueCallback<String>() {
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

    public String click(final String selector) {
        if (mWebView == null) return "Error: WebView not initialized or active.";

        addBrowserLog("INFO", TAG, "Clicking selector: " + selector);
        String script = "(function() { " +
                "  var el = document.querySelector('" + selector.replace("'", "\\'") + "'); " +
                "  if (!el) return 'Element not found: " + selector.replace("'", "\\'") + "'; " +
                "  el.click(); " +
                "  return 'Clicked successfully'; " +
                "})();";
        return executeJs(script);
    }

    public String keyboardClick(final String selector, final String text) {
        if (mWebView == null) return "Error: WebView not initialized or active.";

        addBrowserLog("INFO", TAG, "Typing text into selector: " + selector);
        String escapedText = text.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n");
        String script = "(function() { " +
                "  var el = document.querySelector('" + selector.replace("'", "\\'") + "'); " +
                "  if (!el) return 'Element not found: " + selector.replace("'", "\\'") + "'; " +
                "  el.focus(); " +
                "  el.value = '" + escapedText + "'; " +
                "  el.dispatchEvent(new Event('input', { bubbles: true })); " +
                "  el.dispatchEvent(new Event('change', { bubbles: true })); " +
                "  return 'Text entered successfully'; " +
                "})();";
        return executeJs(script);
    }

    public JSONObject takeScreenshot(final Context context) {
        final JSONObject result = new JSONObject();
        if (mWebView == null) {
            try {
                result.put("status", "error");
                result.put("message", "WebView not initialized or active.");
            } catch (Exception ignored) {}
            return result;
        }

        final CountDownLatch latch = new CountDownLatch(1);

        mMainHandler.post(() -> {
            try {
                addBrowserLog("INFO", TAG, "Taking WebView screenshot.");
                int width = mWebView.getWidth();
                int height = mWebView.getHeight();
                if (width <= 0) width = 1080;
                if (height <= 0) height = 1920;

                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                mWebView.draw(canvas);

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
}
