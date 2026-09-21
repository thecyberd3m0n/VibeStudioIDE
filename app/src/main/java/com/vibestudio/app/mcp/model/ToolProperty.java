package com.vibestudio.app.mcp.model;

import org.json.JSONException;
import org.json.JSONObject;

public class ToolProperty {
    private final String name;
    private final PropertyType type;
    private final String description;
    private final boolean required;

    public ToolProperty(String name, PropertyType type, String description, boolean required) {
        this.name = name;
        this.type = type;
        this.description = description;
        this.required = required;
    }

    public String getName() {
        return name;
    }

    public PropertyType getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public boolean isRequired() {
        return required;
    }

    public JSONObject toSchemaJson() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("type", type.getValue());
        if (description != null) {
            obj.put("description", description);
        }
        return obj;
    }
}
