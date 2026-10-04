package com.vibestudio.app.util;

import android.content.Context;
import android.graphics.Typeface;
import android.util.Log;

public class FontUtils {
    private static final String TAG = "FontUtils";
    private static final String MONO_FONT_PATH = "fonts/jetbrains_mono.ttf";
    private static Typeface sMonospaceTypeface;

    public static Typeface getMonospaceTypeface(Context context) {
        if (sMonospaceTypeface == null && context != null) {
            try {
                sMonospaceTypeface = Typeface.createFromAsset(context.getAssets(), MONO_FONT_PATH);
            } catch (Exception e) {
                Log.e(TAG, "Error loading bundled monospace font, falling back to Typeface.MONOSPACE", e);
                sMonospaceTypeface = Typeface.MONOSPACE;
            }
        }
        return sMonospaceTypeface != null ? sMonospaceTypeface : Typeface.MONOSPACE;
    }
}
