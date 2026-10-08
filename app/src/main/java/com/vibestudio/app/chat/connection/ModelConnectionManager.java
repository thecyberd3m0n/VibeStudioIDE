package com.vibestudio.app.chat.connection;

import android.content.Context;

import com.vibestudio.app.chat.router.AiRouter;

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
        AiRouter router = AiRouter.getInstance();
        router.loadActiveProvider(context);
        return router.getActiveApiKey(context);
    }

    public boolean isConnectedAndConfigured(Context context) {
        String key = getApiKey(context);
        return key != null && !key.isEmpty();
    }

    public String getConnectionStatusMessage(Context context) {
        AiRouter router = AiRouter.getInstance();
        router.loadActiveProvider(context);
        String providerName = router.getActiveProviderName();
        String modelName = router.getActiveModelName(providerName);
        if (isConnectedAndConfigured(context)) {
            if (modelName != null && !modelName.isEmpty()) {
                return "Connected to " + providerName + " (" + modelName + ")";
            }
            return "Connected to " + providerName + " AI Provider";
        }
        return "Error: " + providerName + " API Key is not configured. Please set your API Key in Models setting.";
    }
}
