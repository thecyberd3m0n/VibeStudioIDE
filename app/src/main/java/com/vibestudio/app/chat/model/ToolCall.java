package com.vibestudio.app.chat.model;

import org.json.JSONObject;

public class ToolCall {

    private final String name;
    private final JSONObject arguments;
    private final String rawJson;

    public ToolCall(String name, JSONObject arguments, String rawJson) {
        this.name = name;
        this.arguments = arguments != null ? arguments : new JSONObject();
        this.rawJson = rawJson;
    }

    public String getName() {
        return name;
    }

    public JSONObject getArguments() {
        return arguments;
    }

    public String getRawJson() {
        return rawJson;
    }

    public boolean isMetaTool() {
        return "get_skill_schema".equalsIgnoreCase(name);
    }
}
