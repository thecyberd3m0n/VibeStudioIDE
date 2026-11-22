package com.vibestudio.app.chat.safety;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.vibestudio.app.service.LogViewerService;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class PermissionPromptManager {

    private static final String TAG = "PermissionPromptManager";
    private static final PermissionPromptManager sInstance = new PermissionPromptManager();

    private final Set<String> mGrantedPaths = Collections.newSetFromMap(new ConcurrentHashMap<>());


    public static PermissionPromptManager getInstance() {
        return sInstance;
    }

    public boolean isPathGranted(String path) {
        if (path == null) return true;
        for (String granted : mGrantedPaths) {
            if (path.equals(granted) || path.startsWith(granted + "/")) {
                return true;
            }
        }
        return false;
    }

    public void grantPath(String path) {
        if (path != null) {
            mGrantedPaths.add(path);
        }
    }

    public boolean requestPermissionSync(Context context, String toolName, String targetPath) {
        if (isPathGranted(targetPath)) {
            return true;
        }
        return promptSync(context, "⚠️ Workspace Boundary Warning",
                "The AI Agent is requesting access to a path outside the assigned workspace scope:\n\n"
                        + targetPath + "\n\nDo you want to allow this action?",
                "Allow Access", () -> grantPath(targetPath));
    }

    /** Shows a blocking Allow/Block dialog; {@code onAllow} runs on Allow. Callers own how long a grant lasts. */
    public boolean promptSync(Context context, String title, String message, String allowLabel, Runnable onAllow) {

        if (context == null) {
            LogViewerService.getInstance().w(TAG, "Null context provided for permission prompt. Denying: " + title);
            return false;
        }

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean result = new AtomicBoolean(false);

        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                new AlertDialog.Builder(context)
                        .setTitle(title)
                        .setMessage(message)
                        .setPositiveButton(allowLabel, (dialog, which) -> {
                            onAllow.run();
                            result.set(true);
                            latch.countDown();
                        })
                        .setNegativeButton("Block", (dialog, which) -> latch.countDown())
                        .setOnCancelListener(dialog -> latch.countDown())
                        .show();
            } catch (Exception e) {
                LogViewerService.getInstance().e(TAG, "Failed to show permission dialog", e);
                latch.countDown();
            }
        });

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }

        return result.get();
    }
}