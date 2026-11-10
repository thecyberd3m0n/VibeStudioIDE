package com.vibestudio.app.chat.prompt;

import android.content.Context;

import com.vibestudio.app.service.LogViewerService;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** Loads the prompt text files shipped in assets/ai. */
public final class PromptAssets {

    private static final String TAG = "PromptAssets";

    private PromptAssets() {
    }

    /** The file's text with a trailing newline per line, or "" when it can't be read. */
    public static String read(Context context, String fileName) {
        if (context == null) return "";
        try (InputStream in = context.getAssets().open("ai/" + fileName);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            LogViewerService.getInstance().w(TAG, "Failed to load " + fileName + " from assets/ai", e);
            return "";
        }
    }
}
