package com.vibestudio.app.util;

import android.content.Intent;
import android.net.Uri;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IntentUtils {

    public static boolean isShareOrViewIntent(Intent intent) {
        if (intent == null) return false;
        String action = intent.getAction();
        return Intent.ACTION_SEND.equals(action) || Intent.ACTION_VIEW.equals(action);
    }

    public static String extractUrlFromIntent(Intent intent) {
        if (intent == null) return null;
        String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action)) {
            Uri uri = intent.getData();
            if (uri != null) {
                return cleanUrl(uri.toString());
            }
        } else if (Intent.ACTION_SEND.equals(action)) {
            String extraText = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (extraText != null) {
                String url = extractUrlFromText(extraText);
                if (url != null) return url;
            }
            String extraSubject = intent.getStringExtra(Intent.EXTRA_SUBJECT);
            if (extraSubject != null) {
                String url = extractUrlFromText(extraSubject);
                if (url != null) return url;
            }
        }
        return null;
    }

    public static String extractUrlFromText(String text) {
        if (text == null || text.trim().isEmpty()) return null;

        String trimmed = text.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            String url = trimmed.split("\\s+")[0];
            return cleanUrl(url);
        }

        Pattern pattern = Pattern.compile("https?://[a-zA-Z0-9\\.\\-_~:/?#\\[\\]@!$&'()*+,;=%]+", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return cleanUrl(matcher.group());
        }

        if (trimmed.matches("^[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(/.*)?$")) {
            return "https://" + trimmed;
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
