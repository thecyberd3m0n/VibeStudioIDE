package com.vibestudio.app.chat.model;

import org.json.JSONObject;

public class ToolCall {

    private final String name;
    private final JSONObject arguments;
    private final String rawJson;
    private final long plannedDelayMs;

    public ToolCall(String name, JSONObject arguments, String rawJson) {
        this(name, arguments, rawJson, 0L);
    }

    public ToolCall(String name, JSONObject arguments, String rawJson, long plannedDelayMs) {
        this.name = name;
        this.arguments = arguments != null ? arguments : new JSONObject();
        this.rawJson = rawJson;
        this.plannedDelayMs = Math.max(0L, plannedDelayMs);
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

    public long getPlannedDelayMs() {
        return plannedDelayMs;
    }

    public boolean isMetaTool() {
        return "get_skill_schema".equalsIgnoreCase(name);
    }
}
