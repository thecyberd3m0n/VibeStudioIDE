package com.vibestudio.app.chat.provider;

import org.json.JSONArray;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** The Copilot HTTP chat APIs this client speaks, and which model families use which. */
enum CopilotApi {
    CHAT_COMPLETIONS("/chat/completions"),
    RESPONSES("/responses");

    final String path;

    CopilotApi(String path) {
        this.path = path;
    }

    /**
     * Model-name prefix to API, first match wins. Used when /models gives no endpoint list, and to break the tie
     * for models that list both endpoints. Claude also lists /v1/messages (Anthropic Messages API), which we don't use.
     */
    private static final Map<String, CopilotApi> FAMILY_APIS = new LinkedHashMap<>();

    static {
        FAMILY_APIS.put("gpt-3", CHAT_COMPLETIONS);
        FAMILY_APIS.put("gpt-4", CHAT_COMPLETIONS);
        FAMILY_APIS.put("gpt-", RESPONSES);
        FAMILY_APIS.put("grok", RESPONSES);
        FAMILY_APIS.put("mai", RESPONSES);
        FAMILY_APIS.put("claude", CHAT_COMPLETIONS);
        FAMILY_APIS.put("gemini", CHAT_COMPLETIONS);
        FAMILY_APIS.put("kimi", CHAT_COMPLETIONS);
    }

    static CopilotApi forModelName(String modelId) {
        String id = modelId == null ? "" : modelId.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, CopilotApi> entry : FAMILY_APIS.entrySet()) {
            if (id.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return CHAT_COMPLETIONS;
    }

    /**
     * The API to use for a model given the supported_endpoints from /models: the only supported one, the family
     * default when both are listed or the list is missing, or null when it lists none we can speak.
     */
    static CopilotApi choose(String modelId, JSONArray supportedEndpoints) {
        if (supportedEndpoints == null || supportedEndpoints.length() == 0) {
            return forModelName(modelId);
        }
        boolean chat = contains(supportedEndpoints, CHAT_COMPLETIONS.path);
        boolean responses = contains(supportedEndpoints, RESPONSES.path);
        if (chat && responses) return forModelName(modelId);
        if (chat) return CHAT_COMPLETIONS;
        if (responses) return RESPONSES;
        return null;
    }

    static CopilotApi fromName(String name, String modelId) {
        for (CopilotApi api : values()) {
            if (api.name().equalsIgnoreCase(name)) return api;
        }
        return forModelName(modelId);
    }

    private static boolean contains(JSONArray array, String value) {
        for (int i = 0; i < array.length(); i++) {
            if (value.equals(array.optString(i))) return true;
        }
        return false;
    }
}
