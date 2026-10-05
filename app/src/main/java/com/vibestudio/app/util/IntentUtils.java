package com.vibestudio.app.util;

import android.content.Intent;
import android.net.Uri;

public class IntentUtils {

    public static boolean isViewIntent(Intent intent) {
        if (intent == null) return false;
        return Intent.ACTION_VIEW.equals(intent.getAction());
    }

    public static String extractUrlFromIntent(Intent intent) {
        if (intent == null) return null;
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri uri = intent.getData();
            if (uri != null) {
                return cleanUrl(uri.toString());
            }
        }
        return null;
    }

    private static String cleanUrl(String url) {
        if (url == null) return null;
        String result = url;
        while (result.endsWith(".") || result.endsWith(",") || result.endsWith("!") || result.endsWith("?")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
