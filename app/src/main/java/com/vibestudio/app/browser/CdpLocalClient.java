package com.vibestudio.app.browser;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.Process;
import android.util.Base64;
import android.util.Log;
import android.webkit.WebView;

import com.vibestudio.app.service.LogViewerService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class CdpLocalClient {

    private static final String TAG = "CdpLocalClient";
    private static final AtomicInteger MESSAGE_ID = new AtomicInteger(1);

    private static class CdpSession {
        final LocalSocket socket;
        final InputStream in;
        final OutputStream out;
        final String wsPath;
        final String targetId;

        CdpSession(LocalSocket socket, InputStream in, OutputStream out, String wsPath, String targetId) {
            this.socket = socket;
            this.in = in;
            this.out = out;
            this.wsPath = wsPath;
            this.targetId = targetId;
        }

        boolean isValid() {
            return socket != null && socket.isConnected() && in != null && out != null;
        }

        void close() {
            try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        }
    }

    private static final Map<WebView, CdpSession> sSessions = new WeakHashMap<>();
    private static final Map<WebView, String> sWebViewTags = new WeakHashMap<>();

    public static String getTargetTag(final WebView webView) {
        if (webView == null) return null;
        String tag;
        synchronized (sWebViewTags) {
            tag = sWebViewTags.get(webView);
            if (tag == null) {
                tag = "vibe_target_" + System.identityHashCode(webView) + "_" + System.currentTimeMillis();
                sWebViewTags.put(webView, tag);
            }
        }
        final String finalTag = tag;
        webView.post(() -> {
            try {
                webView.evaluateJavascript("window.__vibestudio_target_id = '" + finalTag + "';", null);
            } catch (Exception ignored) {}
        });
        return tag;
    }

    private static void log(String level, String msg) {
        Log.i(TAG, "[" + level + "] " + msg);
        try {
            if ("ERROR".equalsIgnoreCase(level)) {
                LogViewerService.getInstance().e(TAG, msg);
            } else if ("WARN".equalsIgnoreCase(level) || "WARNING".equalsIgnoreCase(level)) {
                LogViewerService.getInstance().w(TAG, msg);
            } else {
                LogViewerService.getInstance().i(TAG, msg);
            }
        } catch (Exception ignored) {}
    }

    public static synchronized void closeSession(WebView webView) {
        if (webView == null) return;
        CdpSession session = sSessions.remove(webView);
        if (session != null) {
            session.close();
            log("INFO", "Closed CDP session for target: " + session.targetId);
        }
    }

    private static synchronized CdpSession ensureConnectedSession(WebView webView) throws Exception {
        if (webView != null) {
            CdpSession existing = sSessions.get(webView);
            if (existing != null && existing.isValid()) {
                return existing;
            }
            closeSession(webView);
        }

        int pid = Process.myPid();
        String socketName = "webview_devtools_remote_" + pid;
        log("INFO", "Opening DevTools socket connection: " + socketName);

        LocalSocket initSocket = new LocalSocket();
        initSocket.connect(new LocalSocketAddress(socketName, LocalSocketAddress.Namespace.ABSTRACT));
        initSocket.setSoTimeout(5000);

        InputStream in = initSocket.getInputStream();
        OutputStream out = initSocket.getOutputStream();

        String httpRequest = "GET /json/list HTTP/1.1\r\nHost: localhost\r\n\r\n";
        out.write(httpRequest.getBytes(StandardCharsets.UTF_8));
        out.flush();

        String httpResponse = readHttpResponse(in);
        if (httpResponse == null || !httpResponse.contains("200 OK")) {
            initSocket.close();
            throw new RuntimeException("Failed to fetch DevTools targets list from socket " + socketName);
        }

        int bodyStart = httpResponse.indexOf("\r\n\r\n");
        if (bodyStart < 0) bodyStart = httpResponse.indexOf("\n\n");
        if (bodyStart < 0) {
            initSocket.close();
            throw new RuntimeException("Invalid HTTP response from DevTools.");
        }
        String jsonBody = httpResponse.substring(bodyStart + 4);
        JSONArray targets = new JSONArray(jsonBody);
        if (targets.length() == 0) {
            initSocket.close();
            throw new RuntimeException("No active WebView page targets found in DevTools.");
        }

        JSONObject matchedTarget = resolveDevToolsTarget(webView, targets, socketName);
        if (matchedTarget == null) {
            initSocket.close();
            throw new RuntimeException("Could not resolve matching DevTools target for WebView.");
        }

        String wsUrl = matchedTarget.optString("webSocketDebuggerUrl");
        String targetId = matchedTarget.optString("id");
        if (wsUrl == null || wsUrl.isEmpty()) {
            initSocket.close();
            throw new RuntimeException("Could not extract webSocketDebuggerUrl from WebView target " + targetId);
        }

        String wsPath = wsUrl.contains("/devtools/") ? wsUrl.substring(wsUrl.indexOf("/devtools/")) : wsUrl;

        initSocket.close();

        LocalSocket wsSocket = new LocalSocket();
        wsSocket.connect(new LocalSocketAddress(socketName, LocalSocketAddress.Namespace.ABSTRACT));
        wsSocket.setSoTimeout(5000);

        InputStream wsIn = wsSocket.getInputStream();
        OutputStream wsOut = wsSocket.getOutputStream();

        performWsHandshake(wsIn, wsOut, wsPath);

        CdpSession session = new CdpSession(wsSocket, wsIn, wsOut, wsPath, targetId);
        if (webView != null) {
            sSessions.put(webView, session);
        }
        log("INFO", "Persistent DevTools session established for target '" + targetId + "' on path: " + wsPath);
        return session;
    }

    private static String queryTargetTag(String socketName, String wsPath) {
        try {
            LocalSocket wsSocket = new LocalSocket();
            wsSocket.connect(new LocalSocketAddress(socketName, LocalSocketAddress.Namespace.ABSTRACT));
            wsSocket.setSoTimeout(2000);

            InputStream wsIn = wsSocket.getInputStream();
            OutputStream wsOut = wsSocket.getOutputStream();

            performWsHandshake(wsIn, wsOut, wsPath);

            int evalMsgId = MESSAGE_ID.getAndIncrement();
            JSONObject evalReq = new JSONObject();
            evalReq.put("id", evalMsgId);
            evalReq.put("method", "Runtime.evaluate");
            JSONObject evalParams = new JSONObject();
            evalParams.put("expression", "window.__vibestudio_target_id || ''");
            evalParams.put("returnByValue", true);
            evalReq.put("params", evalParams);
            sendWsFrame(wsOut, evalReq.toString());

            JSONObject resp = readWsResponse(wsIn, evalMsgId);
            wsSocket.close();

            if (resp != null && resp.has("result")) {
                JSONObject resultObj = resp.getJSONObject("result");
                if (resultObj.has("result")) {
                    JSONObject valObj = resultObj.getJSONObject("result");
                    String val = valObj.optString("value", "");
                    return !val.isEmpty() ? val : null;
                }
            }
        } catch (Exception e) {
            log("DEBUG", "Failed to query target tag on " + wsPath + ": " + e.getMessage());
        }
        return null;
    }

    private static JSONObject resolveDevToolsTarget(WebView webView, JSONArray targets, String socketName) {
        List<JSONObject> pageTargets = new ArrayList<>();
        for (int i = 0; i < targets.length(); i++) {
            try {
                JSONObject t = targets.getJSONObject(i);
                if ("page".equalsIgnoreCase(t.optString("type"))) {
                    pageTargets.add(t);
                }
            } catch (Exception ignored) {}
        }

        if (pageTargets.isEmpty()) {
            return targets.length() > 0 ? targets.optJSONObject(0) : null;
        }

        if (pageTargets.size() == 1 || webView == null) {
            return pageTargets.get(0);
        }

        String expectedTag = getTargetTag(webView);

        if (expectedTag != null) {
            for (JSONObject t : pageTargets) {
                String wsUrl = t.optString("webSocketDebuggerUrl");
                if (wsUrl != null && !wsUrl.isEmpty()) {
                    String wsPath = wsUrl.contains("/devtools/") ? wsUrl.substring(wsUrl.indexOf("/devtools/")) : wsUrl;
                    String tagFound = queryTargetTag(socketName, wsPath);
                    if (expectedTag.equals(tagFound)) {
                        log("INFO", "Matched DevTools target '" + t.optString("id") + "' by unique window tag: " + expectedTag);
                        return t;
                    }
                }
            }
        }

        Set<String> boundTargetIds = new HashSet<>();
        for (Map.Entry<WebView, CdpSession> entry : sSessions.entrySet()) {
            if (entry.getKey() != webView && entry.getValue() != null && entry.getValue().isValid()) {
                boundTargetIds.add(entry.getValue().targetId);
            }
        }

        String currentUrl = null;
        String currentTitle = null;
        try {
            currentUrl = webView.getUrl();
            currentTitle = webView.getTitle();
        } catch (Exception ignored) {}

        JSONObject bestCandidate = null;
        for (JSONObject t : pageTargets) {
            String tId = t.optString("id");
            if (boundTargetIds.contains(tId)) continue;

            String tUrl = t.optString("url");
            String tTitle = t.optString("title");

            if (currentUrl != null && currentUrl.equalsIgnoreCase(tUrl)) {
                if (currentTitle != null && currentTitle.equalsIgnoreCase(tTitle)) {
                    return t;
                }
                if (bestCandidate == null) {
                    bestCandidate = t;
                }
            } else if (bestCandidate == null) {
                bestCandidate = t;
            }
        }

        if (bestCandidate != null) {
            return bestCandidate;
        }

        return pageTargets.get(0);
    }

    public static synchronized String dispatchCdpClick(WebView webView, double x, double y) {
        log("INFO", "dispatchCdpClick initiated for target coordinates (" + x + ", " + y + ")");
        try {
            CdpSession session = ensureConnectedSession(webView);
            return sendClickEvents(session.in, session.out, x, y);
        } catch (Exception e) {
            log("WARN", "Error on persistent CDP socket, reconnecting... " + e.getMessage());
            closeSession(webView);
            try {
                CdpSession session = ensureConnectedSession(webView);
                return sendClickEvents(session.in, session.out, x, y);
            } catch (Exception ex) {
                log("ERROR", "Error dispatching CDP click after reconnect: " + ex.getMessage());
                return "Error dispatching CDP click: " + ex.getMessage();
            }
        }
    }

    public static synchronized String dispatchCdpClick(double x, double y) {
        return dispatchCdpClick(null, x, y);
    }

    public static synchronized String dispatchCdpClickBySelector(WebView webView, String selector) {
        log("INFO", "dispatchCdpClickBySelector initiated for selector: " + selector);
        try {
            CdpSession session = ensureConnectedSession(webView);
            return executeClickBySelectorInternal(session, selector);
        } catch (Exception e) {
            log("WARN", "Error on persistent CDP socket, reconnecting... " + e.getMessage());
            closeSession(webView);
            try {
                CdpSession session = ensureConnectedSession(webView);
                return executeClickBySelectorInternal(session, selector);
            } catch (Exception ex) {
                log("ERROR", "Error dispatching CDP click by selector: " + ex.getMessage());
                return "Error dispatching CDP click by selector: " + ex.getMessage();
            }
        }
    }

    public static synchronized String dispatchCdpClickBySelector(String selector) {
        return dispatchCdpClickBySelector(null, selector);
    }

    public static synchronized String executeCdp(WebView webView, String method, JSONObject params) {
        log("INFO", "executeCdp method: " + method + ", params: " + (params != null ? params.toString() : "{}"));
        try {
            CdpSession session = ensureConnectedSession(webView);
            return executeCdpInternal(session, method, params);
        } catch (Exception e) {
            log("WARN", "Error on persistent CDP socket, reconnecting... " + e.getMessage());
            closeSession(webView);
            try {
                CdpSession session = ensureConnectedSession(webView);
                return executeCdpInternal(session, method, params);
            } catch (Exception ex) {
                log("ERROR", "Error executing native CDP command: " + ex.getMessage());
                return "Native CDP Error: " + ex.getMessage();
            }
        }
    }

    public static synchronized String executeCdp(String method, JSONObject params) {
        return executeCdp(null, method, params);
    }

    private static String executeClickBySelectorInternal(CdpSession session, String selector) throws Exception {
        InputStream in = session.in;
        OutputStream out = session.out;

        // 1. DOM.getDocument
        int docMsgId = MESSAGE_ID.getAndIncrement();
        JSONObject docReq = new JSONObject();
        docReq.put("id", docMsgId);
        docReq.put("method", "DOM.getDocument");
        docReq.put("params", new JSONObject());
        sendWsFrame(out, docReq.toString());

        JSONObject docResp = readWsResponse(in, docMsgId);
        if (docResp == null || docResp.has("error")) {
            return "Error in DOM.getDocument: " + (docResp != null ? docResp.optString("error") : "Timeout");
        }
        int rootNodeId = docResp.getJSONObject("result").getJSONObject("root").getInt("nodeId");

        // 2. DOM.querySelector
        int qsMsgId = MESSAGE_ID.getAndIncrement();
        JSONObject qsReq = new JSONObject();
        qsReq.put("id", qsMsgId);
        qsReq.put("method", "DOM.querySelector");
        JSONObject qsParams = new JSONObject();
        qsParams.put("nodeId", rootNodeId);
        qsParams.put("selector", selector);
        qsReq.put("params", qsParams);
        sendWsFrame(out, qsReq.toString());

        JSONObject qsResp = readWsResponse(in, qsMsgId);
        if (qsResp == null || qsResp.has("error")) {
            return "Error in DOM.querySelector: " + (qsResp != null ? qsResp.optString("error") : "Timeout");
        }
        int targetNodeId = qsResp.getJSONObject("result").getInt("nodeId");
        if (targetNodeId <= 0) {
            return "Error: Element not found for selector '" + selector + "'";
        }

        // 3. DOM.scrollIntoViewIfNeeded
        int scrollMsgId = MESSAGE_ID.getAndIncrement();
        JSONObject scrollReq = new JSONObject();
        scrollReq.put("id", scrollMsgId);
        scrollReq.put("method", "DOM.scrollIntoViewIfNeeded");
        JSONObject scrollParams = new JSONObject();
        scrollParams.put("nodeId", targetNodeId);
        scrollReq.put("params", scrollParams);
        sendWsFrame(out, scrollReq.toString());
        readWsResponse(in, scrollMsgId);

        // 4. DOM.getBoxModel
        int boxMsgId = MESSAGE_ID.getAndIncrement();
        JSONObject boxReq = new JSONObject();
        boxReq.put("id", boxMsgId);
        boxReq.put("method", "DOM.getBoxModel");
        JSONObject boxParams = new JSONObject();
        boxParams.put("nodeId", targetNodeId);
        boxReq.put("params", boxParams);
        sendWsFrame(out, boxReq.toString());

        JSONObject boxResp = readWsResponse(in, boxMsgId);

        // If boxModel fails, attempt to unhide element via CDP
        if (boxResp == null || boxResp.has("error") || !boxResp.has("result")) {
            log("WARN", "DOM.getBoxModel failed for selector '" + selector + "'. Unhiding via CDP...");
            int attrMsgId = MESSAGE_ID.getAndIncrement();
            JSONObject attrReq = new JSONObject();
            attrReq.put("id", attrMsgId);
            attrReq.put("method", "DOM.setAttributeValue");
            JSONObject attrParams = new JSONObject();
            attrParams.put("nodeId", targetNodeId);
            attrParams.put("name", "style");
            attrParams.put("value", "position:fixed !important; top:10px !important; left:10px !important; width:100px !important; height:100px !important; display:block !important; visibility:visible !important; opacity:1 !important; z-index:999999 !important;");
            attrReq.put("params", attrParams);
            sendWsFrame(out, attrReq.toString());
            readWsResponse(in, attrMsgId);

            boxMsgId = MESSAGE_ID.getAndIncrement();
            boxReq = new JSONObject();
            boxReq.put("id", boxMsgId);
            boxReq.put("method", "DOM.getBoxModel");
            boxParams = new JSONObject();
            boxParams.put("nodeId", targetNodeId);
            boxReq.put("params", boxParams);
            sendWsFrame(out, boxReq.toString());
            boxResp = readWsResponse(in, boxMsgId);
        }

        if (boxResp == null || boxResp.has("error") || !boxResp.has("result")) {
            return "Error in DOM.getBoxModel: " + (boxResp != null ? boxResp.optString("error") : "Timeout or invisible element");
        }

        JSONObject model = boxResp.getJSONObject("result").getJSONObject("model");
        JSONArray quad = model.has("border") ? model.getJSONArray("border") : model.getJSONArray("content");
        double x = (quad.getDouble(0) + quad.getDouble(2) + quad.getDouble(4) + quad.getDouble(6)) / 4.0;
        double y = (quad.getDouble(1) + quad.getDouble(3) + quad.getDouble(5) + quad.getDouble(7)) / 4.0;

        log("INFO", "Calculated coordinates for selector '" + selector + "' via native CDP DOM.getBoxModel: (" + x + ", " + y + ")");

        return sendClickEvents(in, out, x, y);
    }

    private static String executeCdpInternal(CdpSession session, String method, JSONObject params) throws Exception {
        InputStream in = session.in;
        OutputStream out = session.out;

        if ("DOM.setFileInputFiles".equalsIgnoreCase(method)) {
            String selector = params.optString("selector", "input[type='file']");
            JSONArray filesArray = params.optJSONArray("files");
            if (filesArray == null && params.has("filePath")) {
                filesArray = new JSONArray();
                filesArray.put(params.optString("filePath"));
            }
            if (filesArray == null || filesArray.length() == 0) {
                return "Error: 'files' array or 'filePath' string parameter is required for DOM.setFileInputFiles.";
            }

            int docMsgId = MESSAGE_ID.getAndIncrement();
            JSONObject docReq = new JSONObject();
            docReq.put("id", docMsgId);
            docReq.put("method", "DOM.getDocument");
            docReq.put("params", new JSONObject());
            sendWsFrame(out, docReq.toString());

            JSONObject docResp = readWsResponse(in, docMsgId);
            if (docResp == null || docResp.has("error")) {
                return "Error in DOM.getDocument: " + (docResp != null ? docResp.optString("error") : "Timeout");
            }
            int rootNodeId = docResp.getJSONObject("result").getJSONObject("root").getInt("nodeId");

            int qsMsgId = MESSAGE_ID.getAndIncrement();
            JSONObject qsReq = new JSONObject();
            qsReq.put("id", qsMsgId);
            qsReq.put("method", "DOM.querySelector");
            JSONObject qsParams = new JSONObject();
            qsParams.put("nodeId", rootNodeId);
            qsParams.put("selector", selector);
            qsReq.put("params", qsParams);
            sendWsFrame(out, qsReq.toString());

            JSONObject qsResp = readWsResponse(in, qsMsgId);
            if (qsResp == null || qsResp.has("error")) {
                return "Error in DOM.querySelector: " + (qsResp != null ? qsResp.optString("error") : "Timeout");
            }
            int targetNodeId = qsResp.getJSONObject("result").getInt("nodeId");
            if (targetNodeId <= 0) {
                return "Error: File input element not found for selector '" + selector + "'";
            }

            int setMsgId = MESSAGE_ID.getAndIncrement();
            JSONObject setReq = new JSONObject();
            setReq.put("id", setMsgId);
            setReq.put("method", "DOM.setFileInputFiles");
            JSONObject setParams = new JSONObject();
            setParams.put("nodeId", targetNodeId);
            setParams.put("files", filesArray);
            setReq.put("params", setParams);
            sendWsFrame(out, setReq.toString());

            JSONObject setResp = readWsResponse(in, setMsgId);
            if (setResp != null && !setResp.has("error")) {
                log("INFO", "Successfully attached files via Native CDP: " + filesArray.toString());

                try {
                    int evalMsgId = MESSAGE_ID.getAndIncrement();
                    JSONObject evalReq = new JSONObject();
                    evalReq.put("id", evalMsgId);
                    evalReq.put("method", "Runtime.evaluate");
                    JSONObject evalParams = new JSONObject();
                    String escapedSel = selector.replace("'", "\'");
                    String evalScript = "(function() { " +
                            "  var el = document.querySelector('" + escapedSel + "'); " +
                            "  if (el) { " +
                            "    el.dispatchEvent(new Event('input', { bubbles: true })); " +
                            "    el.dispatchEvent(new Event('change', { bubbles: true })); " +
                            "    return { count: el.files ? el.files.length : 0, name: el.files && el.files[0] ? el.files[0].name : '' }; " +
                            "  } " +
                            "  return null; " +
                            "})();";
                    evalParams.put("expression", evalScript);
                    evalParams.put("returnByValue", true);
                    evalReq.put("params", evalParams);
                    sendWsFrame(out, evalReq.toString());
                    readWsResponse(in, evalMsgId);
                } catch (Exception ignored) {}

                return "Successfully attached files via Native CDP: " + filesArray.toString() + " (Dispatched input/change events to '" + selector + "')";
            } else {
                return "Error in native DOM.setFileInputFiles: " + (setResp != null ? setResp.optString("error") : "Timeout");
            }

        } else {
            int msgId = MESSAGE_ID.getAndIncrement();
            JSONObject req = new JSONObject();
            req.put("id", msgId);
            req.put("method", method);
            req.put("params", params != null ? params : new JSONObject());
            sendWsFrame(out, req.toString());

            JSONObject resp = readWsResponse(in, msgId);
            if (resp != null) {
                log("INFO", "CDP response received for " + method + ": " + resp.toString());
                return resp.toString();
            } else {
                log("ERROR", "Timed out waiting for CDP response to method '" + method + "'");
                return "Error: Timed out waiting for CDP response to method '" + method + "'";
            }
        }
    }

    private static void performWsHandshake(InputStream in, OutputStream out, String wsPath) throws Exception {
        byte[] secKeyBytes = new byte[16];
        new SecureRandom().nextBytes(secKeyBytes);
        String secKey = Base64.encodeToString(secKeyBytes, Base64.NO_WRAP);

        String wsHandshake = "GET " + wsPath + " HTTP/1.1\r\n" +
                "Host: localhost\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Key: " + secKey + "\r\n" +
                "Sec-WebSocket-Version: 13\r\n\r\n";

        out.write(wsHandshake.getBytes(StandardCharsets.UTF_8));
        out.flush();

        String wsHandshakeResp = readHttpResponse(in);
        if (wsHandshakeResp == null || !wsHandshakeResp.contains("101")) {
            throw new RuntimeException("DevTools WebSocket handshake failed.");
        }
        log("INFO", "DevTools WebSocket handshake successful (HTTP 101 Switching Protocols).");
    }

    private static String sendClickEvents(InputStream in, OutputStream out, double x, double y) throws Exception {
        // 1. Input.dispatchTouchEvent (touchStart)
        int id1 = MESSAGE_ID.getAndIncrement();
        JSONObject touchStart = new JSONObject();
        touchStart.put("id", id1);
        touchStart.put("method", "Input.dispatchTouchEvent");
        JSONObject tsParams = new JSONObject();
        tsParams.put("type", "touchStart");
        JSONArray touchPoints = new JSONArray();
        JSONObject tp = new JSONObject();
        tp.put("x", x);
        tp.put("y", y);
        touchPoints.put(tp);
        tsParams.put("touchPoints", touchPoints);
        touchStart.put("params", tsParams);
        sendWsFrame(out, touchStart.toString());
        readWsResponse(in, id1);

        // 2. Input.dispatchTouchEvent (touchEnd)
        int id2 = MESSAGE_ID.getAndIncrement();
        JSONObject touchEnd = new JSONObject();
        touchEnd.put("id", id2);
        touchEnd.put("method", "Input.dispatchTouchEvent");
        JSONObject teParams = new JSONObject();
        teParams.put("type", "touchEnd");
        teParams.put("touchPoints", new JSONArray());
        touchEnd.put("params", teParams);
        sendWsFrame(out, touchEnd.toString());
        readWsResponse(in, id2);

        // 3. Input.dispatchMouseEvent (mousePressed)
        int id3 = MESSAGE_ID.getAndIncrement();
        JSONObject mousePressed = new JSONObject();
        mousePressed.put("id", id3);
        mousePressed.put("method", "Input.dispatchMouseEvent");
        JSONObject mpParams = new JSONObject();
        mpParams.put("type", "mousePressed");
        mpParams.put("x", x);
        mpParams.put("y", y);
        mpParams.put("button", "left");
        mpParams.put("clickCount", 1);
        mousePressed.put("params", mpParams);
        sendWsFrame(out, mousePressed.toString());
        readWsResponse(in, id3);

        // 4. Input.dispatchMouseEvent (mouseReleased)
        int id4 = MESSAGE_ID.getAndIncrement();
        JSONObject mouseReleased = new JSONObject();
        mouseReleased.put("id", id4);
        mouseReleased.put("method", "Input.dispatchMouseEvent");
        JSONObject mrParams = new JSONObject();
        mrParams.put("type", "mouseReleased");
        mrParams.put("x", x);
        mrParams.put("y", y);
        mrParams.put("button", "left");
        mrParams.put("clickCount", 1);
        mouseReleased.put("params", mrParams);
        sendWsFrame(out, mrParams.toString());
        readWsResponse(in, id4);

        String successMsg = "Successfully dispatched CDP trusted touch & mouse click events at (" + x + ", " + y + ")";
        log("INFO", successMsg);
        return successMsg;
    }

    private static String readHttpResponse(InputStream in) throws Exception {
        ByteArrayOutputStream rawBuf = new ByteArrayOutputStream();
        byte[] chunk = new byte[2048];
        int headerEndIndex = -1;
        int contentLength = -1;

        while (true) {
            int read = in.read(chunk);
            if (read <= 0) {
                break;
            }
            rawBuf.write(chunk, 0, read);

            byte[] data = rawBuf.toByteArray();

            if (headerEndIndex < 0) {
                headerEndIndex = findHeaderEnd(data);
                if (headerEndIndex >= 0) {
                    String headersStr = new String(data, 0, headerEndIndex, StandardCharsets.US_ASCII);
                    contentLength = parseContentLength(headersStr);

                    if (headersStr.contains("101")) {
                        return new String(data, 0, data.length, StandardCharsets.UTF_8);
                    }
                }
            }

            if (headerEndIndex >= 0) {
                int bodyBytesRead = data.length - headerEndIndex;
                if (contentLength >= 0 && bodyBytesRead >= contentLength) {
                    break;
                }
            }
        }

        byte[] finalData = rawBuf.toByteArray();
        if (finalData.length == 0) return null;
        return new String(finalData, StandardCharsets.UTF_8);
    }

    private static int findHeaderEnd(byte[] data) {
        for (int i = 0; i < data.length - 3; i++) {
            if (data[i] == '\r' && data[i + 1] == '\n' && data[i + 2] == '\r' && data[i + 3] == '\n') {
                return i + 4;
            }
        }
        for (int i = 0; i < data.length - 1; i++) {
            if (data[i] == '\n' && data[i + 1] == '\n') {
                return i + 2;
            }
        }
        return -1;
    }

    private static int parseContentLength(String headers) {
        if (headers == null) return -1;
        String[] lines = headers.split("\r?\n");
        for (String line : lines) {
            String lower = line.toLowerCase().trim();
            if (lower.startsWith("content-length:")) {
                try {
                    return Integer.parseInt(lower.substring("content-length:".length()).trim());
                } catch (Exception ignored) {}
            }
        }
        return -1;
    }

    private static void sendWsFrame(OutputStream out, String text) throws Exception {
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        byte[] frame;
        byte[] mask = new byte[4];
        new SecureRandom().nextBytes(mask);

        int len = payload.length;
        if (len <= 125) {
            frame = new byte[2 + 4 + len];
            frame[0] = (byte) 0x81;
            frame[1] = (byte) (0x80 | len);
            System.arraycopy(mask, 0, frame, 2, 4);
            for (int i = 0; i < len; i++) {
                frame[6 + i] = (byte) (payload[i] ^ mask[i % 4]);
            }
        } else if (len <= 65535) {
            frame = new byte[4 + 4 + len];
            frame[0] = (byte) 0x81;
            frame[1] = (byte) (0x80 | 126);
            frame[2] = (byte) ((len >> 8) & 0xFF);
            frame[3] = (byte) (len & 0xFF);
            System.arraycopy(mask, 0, frame, 4, 4);
            for (int i = 0; i < len; i++) {
                frame[8 + i] = (byte) (payload[i] ^ mask[i % 4]);
            }
        } else {
            frame = new byte[10 + 4 + len];
            frame[0] = (byte) 0x81;
            frame[1] = (byte) (0x80 | 127);
            long l = len;
            for (int i = 7; i >= 0; i--) {
                frame[2 + i] = (byte) (l & 0xFF);
                l >>= 8;
            }
            System.arraycopy(mask, 0, frame, 10, 4);
            for (int i = 0; i < len; i++) {
                frame[14 + i] = (byte) (payload[i] ^ mask[i % 4]);
            }
        }
        out.write(frame);
        out.flush();
    }

    private static JSONObject readWsResponse(InputStream in, int expectedMsgId) throws Exception {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            int b1 = in.read();
            if (b1 == -1) break;
            int b2 = in.read();
            if (b2 == -1) break;

            boolean masked = (b2 & 0x80) != 0;
            long payloadLen = b2 & 0x7F;
            if (payloadLen == 126) {
                payloadLen = ((in.read() & 0xFF) << 8) | (in.read() & 0xFF);
            } else if (payloadLen == 127) {
                long extLen = 0;
                for (int i = 0; i < 8; i++) {
                    int b = in.read();
                    if (b == -1) break;
                    extLen = (extLen << 8) | (b & 0xFF);
                }
                payloadLen = extLen;
            }

            byte[] maskKey = null;
            if (masked) {
                maskKey = new byte[4];
                int maskRead = 0;
                while (maskRead < 4) {
                    int r = in.read(maskKey, maskRead, 4 - maskRead);
                    if (r <= 0) break;
                    maskRead += r;
                }
            }

            if (payloadLen > 50_000_000) {
                throw new IllegalStateException("WebSocket frame payload too large: " + payloadLen + " bytes");
            }

            byte[] payload = new byte[(int) payloadLen];
            int totalRead = 0;
            while (totalRead < payloadLen) {
                int r = in.read(payload, totalRead, (int) payloadLen - totalRead);
                if (r <= 0) break;
                totalRead += r;
            }

            if (masked && maskKey != null) {
                for (int i = 0; i < totalRead; i++) {
                    payload[i] = (byte) (payload[i] ^ maskKey[i % 4]);
                }
            }

            String respText = new String(payload, 0, totalRead, StandardCharsets.UTF_8);
            try {
                JSONObject json = new JSONObject(respText);
                if (json.optInt("id", -1) == expectedMsgId) {
                    return json;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }
}
