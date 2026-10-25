package com.vibestudio.app.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.vibestudio.app.R;

public class TerminalService extends Service {

    private static final String TAG = "TerminalService";
    private static final String CHANNEL_ID = "terminal_foreground_service_channel";
    private static final int NOTIFICATION_ID = 2001;

    public static final String ACTION_START = "com.vibestudio.app.service.TerminalService.START";
    public static final String ACTION_STOP = "com.vibestudio.app.service.TerminalService.STOP";

    private PowerManager.WakeLock mWakeLock;

    public static void start(Context context) {
        if (context == null) return;
        Context appCtx = context.getApplicationContext();
        Intent intent = new Intent(appCtx, TerminalService.class);
        intent.setAction(ACTION_START);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appCtx.startForegroundService(intent);
            } else {
                appCtx.startService(intent);
            }
        } catch (Exception e) {
            LogViewerService.getInstance().e(TAG, "Failed to start TerminalService", e);
        }
    }

    public static void stop(Context context) {
        if (context == null) return;
        Context appCtx = context.getApplicationContext();
        Intent intent = new Intent(appCtx, TerminalService.class);
        intent.setAction(ACTION_STOP);
        try {
            appCtx.startService(intent);
        } catch (Exception e) {
            LogViewerService.getInstance().e(TAG, "Failed to stop TerminalService", e);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        acquireWakeLock();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            releaseWakeLock();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        Notification notification = createNotification();
        startForeground(NOTIFICATION_ID, notification);
        acquireWakeLock();

        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Terminal Background Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Keeps terminal sessions and background servers running");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("VibeStudio IDE - Terminal Active")
                .setContentText("Background terminal sessions are active")
                .setSmallIcon(R.drawable.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true);
        return builder.build();
    }

    private synchronized void acquireWakeLock() {
        if (mWakeLock == null || !mWakeLock.isHeld()) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VibeStudio:TerminalWakeLock");
                mWakeLock.acquire();
                LogViewerService.getInstance().i(TAG, "Acquired Terminal Partial WakeLock");
            }
        }
    }

    private synchronized void releaseWakeLock() {
        if (mWakeLock != null && mWakeLock.isHeld()) {
            try {
                mWakeLock.release();
                LogViewerService.getInstance().i(TAG, "Released Terminal Partial WakeLock");
            } catch (Exception ignored) {}
            mWakeLock = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        releaseWakeLock();
    }
}
