package com.vibestudio.app.mcp;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import com.vibestudio.app.VibeApplication;
import com.vibestudio.app.chat.safety.PermissionPromptManager;
import com.vibestudio.app.mcp.model.McpTool;
import com.vibestudio.app.mcp.model.McpToolResult;
import com.vibestudio.app.mcp.model.PropertyType;
import com.vibestudio.app.view.BaseToolMessageView;
import com.vibestudio.app.view.CustomStatusToolMessage;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import dev.vibestudio.automator.ipc.IAutomatorCallback;
import dev.vibestudio.automator.ipc.IAutomatorService;

/**
 * MCP server bridging the agent to VibeStudioAutomator (dev.vibestudio.automator) over bound AIDL.
 * Tools are prefixed "android_" and map 1:1 to Automator tool names.
 */
public class AutomatorMcpServer implements McpServer {
    private static final String PKG = "dev.vibestudio.automator";
    private static final String ACTION = "dev.vibestudio.automator.ACTION_BIND";
    private static final String PREFIX = "android_";
    private static final long CALL_TIMEOUT_S = 30;

    private final List<McpTool> mTools = new ArrayList<>();
    private volatile IAutomatorService mService;
    private boolean mActive = true;
    private boolean mBinding;
    /** Per-instance, and each ChatSession owns its own McpClientManager, so the grant lasts one chat session. */
    private volatile boolean mSessionGranted;

    private final ServiceConnection mConn = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName n, IBinder b) {
            synchronized (AutomatorMcpServer.this) {
                mService = IAutomatorService.Stub.asInterface(b);
                mBinding = false;
                AutomatorMcpServer.this.notifyAll();
            }
        }
        @Override public void onServiceDisconnected(ComponentName n) { mService = null; }
    };

    public AutomatorMcpServer() {
        mTools.add(McpTool.builder("android_status", "Get VibeStudioAutomator driver status (mode ACCESSIBILITY/SHIZUKU/UNAVAILABLE, capabilities, allowlist) and run state.").build());
        mTools.add(McpTool.builder("android_begin_session", "REQUIRED FIRST. Declare the app under test (descriptor = package). The Automator binds that app's window: all coordinates, UI tree and screenshots are relative to it (0,0 = top-left corner of the app window, not the device screen) and nothing outside it is reachable. Returns the window width/height.")
                .addProperty("package", PropertyType.STRING, "Package name of the tested app (must be in the Automator allowlist).", true).build());
        mTools.add(McpTool.builder("android_begin_full_device_session", "Separate mode, no descriptor: operate on the whole device screen (only if Full Device Mode is enabled by the user in the Automator).").build());
        mTools.add(McpTool.builder("android_dump_ui_tree", "Dump the foreground app UI tree with sequential node ids usable by android_tap_node.")
                .addProperty("compact", PropertyType.BOOLEAN, "Strip non-interactive wrappers (default true).", false).build());
        mTools.add(McpTool.builder("android_tap_node", "Tap a node by id from the latest android_dump_ui_tree.")
                .addProperty("id", PropertyType.INTEGER, "Node id.", true).build());
        mTools.add(McpTool.builder("android_tap", "Tap in the session window space: (0,0) is the top-left corner of the tested app window, not the display (screenshot coords divided by scaleFactor). The Automator clamps every point into that window, so a tap can never land outside the app.")
                .addProperty("x", PropertyType.INTEGER, "X.", true)
                .addProperty("y", PropertyType.INTEGER, "Y.", true)
                .addProperty("scaleFactor", PropertyType.STRING, "Screenshot scaleFactor (default 1).", false).build());
        mTools.add(McpTool.builder("android_swipe", "Swipe between two points in the session window space ((0,0) = top-left of the tested app window). Points are clamped into that window.")
                .addProperty("x1", PropertyType.INTEGER, "Start X.", true)
                .addProperty("y1", PropertyType.INTEGER, "Start Y.", true)
                .addProperty("x2", PropertyType.INTEGER, "End X.", true)
                .addProperty("y2", PropertyType.INTEGER, "End Y.", true)
                .addProperty("durationMs", PropertyType.INTEGER, "Duration ms (default 300).", false).build());
        mTools.add(McpTool.builder("android_input_text", "Set text on a node (or the focused node if id omitted).")
                .addProperty("text", PropertyType.STRING, "Text.", true)
                .addProperty("id", PropertyType.INTEGER, "Node id (optional).", false).build());
        mTools.add(McpTool.builder("android_global_action", "Perform BACK, HOME, RECENTS or NOTIFICATIONS.")
                .addProperty("action", PropertyType.STRING, "BACK|HOME|RECENTS|NOTIFICATIONS", true).build());
        mTools.add(McpTool.builder("android_launch_app", "Launch an app by package name (must be in the Automator allowlist); binds the session to it, or adopts its already open window if the session package matches. By DEFAULT the app opens in a floating window (top_right quarter) when freeform is supported; set standard_window=true only to deliberately use normal full-screen mode (e.g. the user asked for it).")
                .addProperty("package", PropertyType.STRING, "Package name.", true)
                .addProperty("quarter", PropertyType.STRING, "Optional: top_left|top_right|bottom_left|bottom_right. Floating window position (default top_right).", false)
                .addProperty("standard_window", PropertyType.BOOLEAN, "Set true to launch in normal full-screen mode instead of the default floating window. Use only if the user asked for it or floating mode is unsuitable. Default: false.", false).build());

        mTools.add(McpTool.builder("android_screenshot", "Capture a <=64KB WebP/JPEG screenshot as JSON with base64, scaleFactor.").build());
        mTools.add(McpTool.builder("android_logcat", "Read recent logcat lines (logcat -d -t N). Whole device log needs Shizuku or READ_LOGS granted via pm grant; otherwise only the Automator's own log is readable.")
                .addProperty("lines", PropertyType.INTEGER, "Number of trailing lines (default 200).", false)
                .addProperty("package", PropertyType.STRING, "Optional: only this running allowlisted package (--pid).", false)
                .addProperty("level", PropertyType.STRING, "Optional minimum level: V|D|I|W|E|F.", false).build());

        mTools.add(McpTool.builder("android_force_stop", "Close a tested app (allowlisted package). Uses am force-stop with Shizuku, otherwise killBackgroundProcesses.")
                .addProperty("package", PropertyType.STRING, "Package name.", true).build());

        mTools.add(McpTool.builder("android_install_apk", "Install an APK via the Automator (PackageInstaller; Shizuku not required, the user must tap Install in the system dialog). BLOCKS the agent loop with no timeout until the user acts (Install or cancel); the screen is not readable and the frame is hidden meanwhile. Do not call other tools in parallel.")
                .addProperty("path", PropertyType.STRING, "Absolute path of an APK readable by the IDE (e.g. the IDE home build output); it is streamed to the Automator. Never copy the APK to /sdcard or shared storage.", true).build());

        mTools.add(McpTool.builder("android_emergency_stop", "Immediately stop the Automator agent session.").build());
    }

    @Override public String getName() { return "android"; }
    @Override public String getDescription() { return "Inspect, navigate and test native Android apps offline via VibeStudioAutomator (AccessibilityService, optional Shizuku). Tools: android_*."; }
    @Override public void startServer() { mActive = true; }
    @Override public void stopServer() {
        mActive = false;
        mService = null;
    }
    @Override public boolean isActive() { return mActive; }
    /** Tools are offered to the agent only while the Automator app is installed and bindable. */
    @Override public List<McpTool> getTools() { return isAutomatorInstalled() ? mTools : new ArrayList<>(); }

    private static boolean isAutomatorInstalled() {
        VibeApplication app = VibeApplication.getInstance();
        return app != null && app.getPackageManager().resolveService(new Intent(ACTION).setPackage(PKG), 0) != null;
    }
    @Override public int getPriority() { return 3; }
    @Override public boolean isLazy() { return true; }

    @Override public boolean handlesTool(String toolName) {
        if (toolName == null) return false;
        for (McpTool t : mTools) if (t.getName().equals(toolName)) return true;
        return false;
    }

    @Override public CharSequence prepareToolCall(String toolName, JSONObject args, Context context,
            com.vibestudio.app.chat.safety.WorkspaceChangesetTracker tracker) {
        return "Android Automator [" + toolName + "]" + (args != null && args.length() > 0 ? ": " + args : "");
    }

    @Override public BaseToolMessageView createToolMessageView(Context context, String toolName) {
        return new CustomStatusToolMessage(context);
    }

    private IAutomatorService ensureBound(Context ctx) throws Exception {
        IAutomatorService s = mService;
        if (s != null && s.asBinder().isBinderAlive()) return s;
        synchronized (this) {
            if (!mBinding) {
                mBinding = true;
                Intent i = new Intent(ACTION).setPackage(PKG);
                boolean ok;
                try {
                    ok = ctx.getApplicationContext().bindService(i, mConn, Context.BIND_AUTO_CREATE);
                } catch (SecurityException e) {
                    mBinding = false;
                    throw new Exception("Bind denied (signature permission dev.vibestudio.permission.BIND_AUTOMATOR): " + e.getMessage());
                }
                if (!ok) {
                    mBinding = false;
                    throw new Exception("VibeStudioAutomator is not installed or cannot be bound");
                }
            }
            long deadline = System.currentTimeMillis() + 8000;
            while (mService == null && System.currentTimeMillis() < deadline) wait(500);
        }
        if (mService == null) throw new Exception("Timed out binding to VibeStudioAutomator");
        return mService;
    }

    @Override public McpToolResult callTool(String toolName, Map<String, Object> arguments, Context context) {
        if (!isAutomatorInstalled()) {
            return McpToolResult.error("VibeStudioAutomator is not installed; Android device tools are unavailable.");
        }
        boolean emergency = "android_emergency_stop".equals(toolName);
        sLast = this;
        if (!emergency && !mSessionGranted && !PermissionPromptManager.getInstance().promptSync(context,
                "🤖 Permission Required",
                "The AI Agent wants to inspect and control apps on this device through VibeStudioAutomator "
                        + "(read the screen, tap, type, take screenshots).\n\nAllow for this chat session?",
                "Allow", () -> mSessionGranted = true)) {
            return McpToolResult.error("User denied access to Android device control.");
        }

        try {
            IAutomatorService svc = ensureBound(context);
            if (emergency) {

                svc.emergencyStop();
                return McpToolResult.success("Emergency stop sent", "Stopped");
            }
            if ("android_status".equals(toolName)) {
                JSONObject o = new JSONObject(svc.getDriverStatus());
                o.put("state", svc.getState());
                return McpToolResult.success(o.toString(), "Automator status");
            }
            String remote = toolName.substring(PREFIX.length());
            if ("install_apk".equals(remote)) {
                // Hand the APK to the Automator as a file descriptor: no shared-storage path needed.
                Object p = arguments == null ? null : arguments.get("path");
                if (p == null) return McpToolResult.error("Missing 'path'");
                java.io.File apk = new java.io.File(String.valueOf(p));
                if (!apk.isAbsolute()) apk = new java.io.File(new java.io.File(context.getFilesDir(), "home"), apk.getPath());
                if (!apk.canRead()) return McpToolResult.error("APK not readable: " + apk);
                final String[] iok = new String[1], ierr = new String[1];
                final CountDownLatch ilatch = new CountDownLatch(1);
                try (android.os.ParcelFileDescriptor fd = android.os.ParcelFileDescriptor.open(apk, android.os.ParcelFileDescriptor.MODE_READ_ONLY)) {
                    svc.installApk(fd, apk.length(), new IAutomatorCallback.Stub() {
                        @Override public void onSuccess(String r) { iok[0] = r; ilatch.countDown(); }
                        @Override public void onError(int code, String m) { ierr[0] = "Error " + code + ": " + m; ilatch.countDown(); }
                    });
                    // No timeout: the loop stays suspended until the Automator reports the user's decision
                    // (Install / cancel / focus back to VibeStudio) or STOP releases the call.
                    ilatch.await();
                }
                if (ierr[0] != null) return McpToolResult.error(ierr[0]);
                return McpToolResult.success(iok[0], toolName);
            }

            JSONObject args = new JSONObject();
            if (arguments != null) for (Map.Entry<String, Object> e : arguments.entrySet()) args.put(e.getKey(), e.getValue());

            final String[] ok = new String[1];
            final String[] err = new String[1];
            final CountDownLatch latch = new CountDownLatch(1);
            svc.executeTool(remote, args.toString(), new IAutomatorCallback.Stub() {
                @Override public void onSuccess(String r) { ok[0] = r; latch.countDown(); }
                @Override public void onError(int code, String m) { err[0] = "Error " + code + ": " + m; latch.countDown(); }
            });
            if (!latch.await(CALL_TIMEOUT_S, TimeUnit.SECONDS)) return McpToolResult.error("Automator call timed out");
            if (err[0] != null) return McpToolResult.error(err[0]);
            if (remote.endsWith("screenshot")) {
                // Hand the already compressed (<=64KB) image to the chat as an attachment instead of text.
                Map<String, Object> img = new java.util.HashMap<>();
                img.put("image_base64", new JSONObject(ok[0]).optString("jpegBase64"));
                img.put("image_mime", "image/jpeg");
                return McpToolResult.success(img, toolName);
            }
            return McpToolResult.success(ok[0], toolName);

        } catch (SecurityException e) {
            return McpToolResult.error("Automator rejected caller: " + e.getMessage());
        } catch (Exception e) {
            return McpToolResult.error("Automator: " + e.getMessage());
        }
    }

    /** Called by the IDE when the agent loop ends (task_complete / stop / error): hides the Automator frame + bubble. Fire-and-forget. */
    private static volatile AutomatorMcpServer sLast;
    public static void endSessionIfActive() { AutomatorMcpServer s = sLast; if (s != null) s.endSession(); }
    /** Called when the user starts a new agent run: clears a previous STOP so the Automator accepts commands again. */
    public static void resumeIfActive() {
        final AutomatorMcpServer s = sLast;
        if (s == null) return;
        new Thread(() -> {
            try {
                IAutomatorService svc = s.mService;
                if (svc != null && svc.asBinder().isBinderAlive()) svc.resume();
            } catch (Throwable ignored) {}
        }, "automator-resume").start();
    }

    public void endSession() {
        final IAutomatorService s = mService;
        if (s == null) return;
        try {
            if (!s.asBinder().isBinderAlive()) return;
            s.executeTool("end_session", "{}", new IAutomatorCallback.Stub() {
                @Override public void onSuccess(String r) {}
                @Override public void onError(int code, String m) {}
            });
        } catch (Throwable ignored) {}
    }
}