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
        mTools.add(McpTool.builder("android_dump_ui_tree", "Dump the foreground app UI tree with sequential node ids usable by android_tap_node.")
                .addProperty("compact", PropertyType.BOOLEAN, "Strip non-interactive wrappers (default true).", false).build());
        mTools.add(McpTool.builder("android_tap_node", "Tap a node by id from the latest android_dump_ui_tree.")
                .addProperty("id", PropertyType.INTEGER, "Node id.", true).build());
        mTools.add(McpTool.builder("android_tap", "Tap screen coordinates (screenshot coords divided by scaleFactor).")
                .addProperty("x", PropertyType.INTEGER, "X.", true)
                .addProperty("y", PropertyType.INTEGER, "Y.", true)
                .addProperty("scaleFactor", PropertyType.STRING, "Screenshot scaleFactor (default 1).", false).build());
        mTools.add(McpTool.builder("android_swipe", "Swipe between two points in native pixels.")
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
        mTools.add(McpTool.builder("android_launch_app", "Launch an app by package name (must be in the Automator allowlist).")
                .addProperty("package", PropertyType.STRING, "Package name.", true).build());
        mTools.add(McpTool.builder("android_screenshot", "Capture a <=64KB WebP/JPEG screenshot as JSON with base64, scaleFactor.").build());
        mTools.add(McpTool.builder("android_logcat", "Read recent logcat lines (logcat -d -t N). Without Shizuku only the Automator's own log is readable; with Shizuku the whole device log.")
                .addProperty("lines", PropertyType.INTEGER, "Number of trailing lines (default 200).", false).build());
        mTools.add(McpTool.builder("android_force_stop", "Close a tested app (allowlisted package). Uses am force-stop with Shizuku, otherwise killBackgroundProcesses.")
                .addProperty("package", PropertyType.STRING, "Package name.", true).build());

        mTools.add(McpTool.builder("android_install_apk", "Install an APK via the Automator (PackageInstaller; Shizuku not required, the user may have to confirm the system dialog).")
                .addProperty("path", PropertyType.STRING, "APK path readable by the Automator (e.g. /sdcard/...).", true).build());

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
            if ("install_apk".equals(remote)) remote = "shell_install";
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
            return McpToolResult.success(ok[0], toolName);
        } catch (SecurityException e) {
            return McpToolResult.error("Automator rejected caller: " + e.getMessage());
        } catch (Exception e) {
            return McpToolResult.error("Automator: " + e.getMessage());
        }
    }
}