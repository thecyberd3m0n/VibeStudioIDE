package com.vibestudio.app.test;

import com.vibestudio.app.db.DatabaseHelper;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import java.util.HashMap;
import java.util.Map;

@Implements(DatabaseHelper.class)
public class ShadowDatabaseHelper {

    private static final Map<String, String> API_KEYS = new HashMap<>();
    private static final Map<String, String> SETTINGS = new HashMap<>();

    @Implementation
    public boolean isEnvInitialized() {
        return "true".equals(getSetting("env_initialized"));
    }

    @Implementation
    public String getSetting(String key) {
        return SETTINGS.get(key);
    }

    @Implementation
    public void saveSetting(String key, String value) {
        SETTINGS.put(key, value);
    }

    @Implementation
    public void setSetting(String key, String value) {
        SETTINGS.put(key, value);
    }

    @Implementation
    public void setEnvInitialized(boolean value) {
        setSetting("env_initialized", value ? "true" : "false");
    }

    @Implementation
    public void saveApiKey(String provider, String apiKey) {
        API_KEYS.put(provider, apiKey);
    }

    @Implementation
    public String getApiKey(String provider) {
        return API_KEYS.get(provider);
    }

    @Implementation
    public void clearApiKey(String provider) {
        API_KEYS.remove(provider);
    }

    public static void reset() {
        API_KEYS.clear();
        SETTINGS.clear();
    }
}
