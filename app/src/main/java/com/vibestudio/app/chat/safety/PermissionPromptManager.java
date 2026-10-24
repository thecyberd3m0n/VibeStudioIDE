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
        if (context == null) {
            LogViewerService.getInstance().w(TAG, "Null context provided for permission prompt. Denying access to: " + targetPath);
            return false;
        }

        if (isPathGranted(targetPath)) {
            return true;
        }

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicBoolean result = new AtomicBoolean(false);

        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                new AlertDialog.Builder(context)
                        .setTitle("⚠️ Workspace Boundary Warning")
                        .setMessage("The AI Agent is requesting access to a path outside the assigned workspace scope:\n\n"
                                + targetPath + "\n\nDo you want to allow this action?")
                        .setPositiveButton("Allow Access", (dialog, which) -> {
                            grantPath(targetPath);
                            result.set(true);
                            latch.countDown();
                        })
                        .setNegativeButton("Block", (dialog, which) -> {
                            result.set(false);
                            latch.countDown();
                        })
                        .setOnCancelListener(dialog -> {
                            result.set(false);
                            latch.countDown();
                        })
                        .show();
            } catch (Exception e) {
                LogViewerService.getInstance().e(TAG, "Failed to show permission dialog", e);
                result.set(false);
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
