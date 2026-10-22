package com.vibestudio.app.browser;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Build;
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
import android.net.Uri;
import android.webkit.WebResourceResponse;
import android.webkit.WebViewClient;

import androidx.core.content.FileProvider;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;

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
    private ValueCallback<Uri[]> mPendingFilePathCallback;
    private Uri mPendingAutoAttachUri;
    private long mAutoAttachTimestamp = 0;
    private Uri mLastAttachedUri = null;

    public interface BrowserStateListener {
        void onUrlChanged(WebView view, String newUrl);
        void onTitleChanged(WebView view, String title);
        void onFaviconChanged(WebView view, Bitmap favicon);
        default void onShowFileChooser(WebView view, ValueCallback<Uri[]> filePathCallback, WebChromeClient.FileChooserParams fileChooserParams) {}
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
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
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if (request != null && request.getUrl() != null) {
                    String method = request.getMethod();
                    if ("GET".equalsIgnoreCase(method) && request.isForMainFrame()) {
                        try {
                            URL url = new URL(request.getUrl().toString());
                            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                            conn.setRequestMethod("GET");
                            conn.setConnectTimeout(8000);
                            conn.setReadTimeout(8000);
                            conn.setInstanceFollowRedirects(true);
                            if (request.getRequestHeaders() != null) {
                                for (Map.Entry<String, String> header : request.getRequestHeaders().entrySet()) {
                                    conn.setRequestProperty(header.getKey(), header.getValue());
                                }
                            }
                            conn.connect();

                            int statusCode = conn.getResponseCode();
                            if (statusCode >= 200 && statusCode < 400) {
                                String encoding = conn.getContentEncoding() != null ? conn.getContentEncoding() : "UTF-8";
                                String mimeType = conn.getContentType();
                                if (mimeType != null && mimeType.contains(";")) {
                                    mimeType = mimeType.split(";")[0].trim();
                                }
                                if (mimeType == null || mimeType.isEmpty()) {
                                    mimeType = "text/html";
                                }

                                Map<String, String> responseHeaders = new HashMap<>();
                                for (Map.Entry<String, List<String>> entry : conn.getHeaderFields().entrySet()) {
                                    if (entry.getKey() == null) continue;
                                    String keyLower = entry.getKey().toLowerCase();
                                    if (keyLower.contains("content-security-policy")) {
                                        continue;
                                    }
                                    if (!entry.getValue().isEmpty()) {
                                        responseHeaders.put(entry.getKey(), entry.getValue().get(0));
                                    }
                                }

                                InputStream stream = statusCode >= 400 ? conn.getErrorStream() : conn.getInputStream();
                                return new WebResourceResponse(mimeType, encoding, statusCode, conn.getResponseMessage(), responseHeaders, stream);
                            }
                        } catch (Exception e) {
                            addBrowserLog("DEBUG", "WebViewCSP", "CSP interception fallback: " + e.getMessage());
                        }
                    }
                }
                return super.shouldInterceptRequest(view, request);
            }
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
            public boolean onShowFileChooser(final WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                boolean isRecentAutoAttach = (System.currentTimeMillis() - mAutoAttachTimestamp < 2000);
                addBrowserLog("INFO", "WebViewFileChooser", "onShowFileChooser triggered by Chromium. PendingUri: " + mPendingAutoAttachUri + ", RecentAutoAttach: " + isRecentAutoAttach);

                if (mPendingAutoAttachUri != null || isRecentAutoAttach) {
                    final Uri autoUri = mPendingAutoAttachUri != null ? mPendingAutoAttachUri : mLastAttachedUri;
                    if (mPendingAutoAttachUri != null) {
                        mLastAttachedUri = mPendingAutoAttachUri;
                        mPendingAutoAttachUri = null;
                    }

                    if (filePathCallback != null) {
                        if (autoUri != null) {
                            filePathCallback.onReceiveValue(new Uri[]{ autoUri });
                            addBrowserLog("INFO", "WebViewFileChooser", "Successfully passed Uri[] to Chromium filePathCallback: " + autoUri);
                        } else {
                            filePathCallback.onReceiveValue(null);
                        }
                    } else {
                        addBrowserLog("WARNING", "WebViewFileChooser", "filePathCallback is null!");
                    }
                    return true;
                }
                if (mPendingFilePathCallback != null) {
                    try {
                        mPendingFilePathCallback.onReceiveValue(null);
                    } catch (Exception ignored) {}
                }
                mPendingFilePathCallback = filePathCallback;
                addBrowserLog("INFO", "WebViewFileChooser", "File chooser requested by page (manual user interaction).");

                BrowserStateListener listener = mStateListeners.get(webView);
                if (listener != null) {
                    mMainHandler.post(() -> listener.onShowFileChooser(webView, filePathCallback, fileChooserParams));
                }
                return true;
            }
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
                public String attachFile(final Context context, final WebView webView, final String selector, final String filePath) {
        if (webView == null) return "Error: WebView not initialized.";
        if (filePath == null || filePath.trim().isEmpty()) {
            return "Error: File path is empty.";
        }

        String resolvedPath = filePath.trim();
        if (resolvedPath.startsWith("~")) {
            File termuxRoot = new File("/data/data/com.termux/files");
            File homeDir = termuxRoot.exists() ? new File(termuxRoot, "home") : (context != null ? new File(context.getFilesDir(), "home") : null);
            if (homeDir != null) {
                resolvedPath = new File(homeDir, resolvedPath.substring(1)).getAbsolutePath();
            }
        }

        File file = new File(resolvedPath);
        if (!file.exists()) {
            return "Error: File not found: " + filePath + (resolvedPath.equals(filePath) ? "" : " (resolved: " + resolvedPath + ")");
        }

        Uri contentUri = null;
        if (context != null) {
            try {
                contentUri = FileProvider.getUriForFile(
                        context,
                        context.getPackageName() + ".fileprovider",
                        file);
            } catch (Exception e) {
                addBrowserLog("WARNING", TAG, "FileProvider error: " + e.getMessage() + ". Falling back to Uri.fromFile");
                contentUri = Uri.fromFile(file);
            }
        } else {
            contentUri = Uri.fromFile(file);
        }

        mPendingAutoAttachUri = contentUri;
        mLastAttachedUri = contentUri;
        mAutoAttachTimestamp = System.currentTimeMillis();

        addBrowserLog("INFO", TAG, "attachFile initiated. File: " + resolvedPath + ", Uri: " + contentUri + ", Selector: " + selector);

        String targetSelector = (selector != null && !selector.trim().isEmpty()) ? selector : "input[type='file']";
        addBrowserLog("INFO", TAG, "Executing native CDP click by selector on target selector: " + targetSelector);
        String cdpResult = CdpLocalClient.dispatchCdpClickBySelector(targetSelector);
        addBrowserLog("INFO", TAG, "Initial CDP click result on '" + targetSelector + "': " + cdpResult);

        if (cdpResult != null && cdpResult.startsWith("Error")) {
            addBrowserLog("INFO", TAG, "CDP click returned error, attempting fallback JS click on: " + targetSelector);
            final String selEscaped = targetSelector.replace("'", "\\'");
            mMainHandler.post(() -> {
                try {
                    webView.evaluateJavascript(
                        "(function() { " +
                        "  var el = document.querySelector('" + selEscaped + "'); " +
                        "  if (el) { el.click(); return 'clicked'; } " +
                        "  return 'not_found'; " +
                        "})();", null);
                } catch (Exception ignored) {}
            });
        }

        return "File attachment queued for '" + resolvedPath + "'. Native CDP click result: " + cdpResult;
    }

    public String uploadFile(final WebView webView, final String selector, final String filePath) {
        return attachFile(webView != null ? webView.getContext() : null, webView, selector, filePath);
    }

    public String executeCdpCommand(final WebView webView, final String method, final String paramsStr) {
        if (webView == null) return "Error: WebView not initialized.";
        addBrowserLog("INFO", TAG, "Executing CDP method: " + method + " with params: " + paramsStr);

        try {
            JSONObject params = new JSONObject(paramsStr != null && !paramsStr.trim().isEmpty() ? paramsStr : "{}");
            if ("Page.navigate".equalsIgnoreCase(method)) {
                String url = params.optString("url", "");
                return navigate(webView, url);
            } else if ("Page.reload".equalsIgnoreCase(method)) {
                mMainHandler.post(webView::reload);
                return "Page reload initiated via CDP.";
            } else {
                return CdpLocalClient.executeCdp(method, params);
            }
        } catch (Exception e) {
            return "Error executing CDP command '" + method + "': " + e.getMessage();
        }
    }

}
