package com.vibestudio.app.chat.connection;

import android.content.Context;

import com.vibestudio.app.db.DatabaseHelper;

public class ModelConnectionManager {

    private static ModelConnectionManager sInstance;

    private ModelConnectionManager() {}

    public static synchronized ModelConnectionManager getInstance() {
        if (sInstance == null) {
            sInstance = new ModelConnectionManager();
        }
        return sInstance;
    }

    public String getApiKey(Context context) {
        if (context == null) return null;
        DatabaseHelper dbHelper = new DatabaseHelper(context.getApplicationContext());
        String apiKey = dbHelper.getApiKey("Gemini");
        return (apiKey != null) ? apiKey.trim() : null;
    }

    public boolean isConnectedAndConfigured(Context context) {
        String key = getApiKey(context);
        return key != null && !key.isEmpty();
    }

    public String getConnectionStatusMessage(Context context) {
        if (isConnectedAndConfigured(context)) {
            return "Connected to Gemini AI Provider";
        }
        return "Error: Gemini API Key is not configured. Please set your API Key in Models setting.";
    }
}
