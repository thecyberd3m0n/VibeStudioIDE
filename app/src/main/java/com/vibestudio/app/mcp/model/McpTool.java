package com.vibestudio.app.mcp.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class McpTool {
    private final String name;
    private final String description;
    private final List<ToolProperty> properties;

    public McpTool(String name, String description, List<ToolProperty> properties) {
        this.name = name;
        this.description = description;
        this.properties = properties != null ? properties : Collections.emptyList();
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public List<ToolProperty> getProperties() {
        return properties;
    }

    public JSONObject toJsonSchema() {
        JSONObject toolObj = new JSONObject();
        try {
            toolObj.put("name", name);
            toolObj.put("description", description);

            JSONObject inputSchema = new JSONObject();
            inputSchema.put("type", PropertyType.OBJECT.getValue());

            if (!properties.isEmpty()) {
                JSONObject propsObj = new JSONObject();
                JSONArray reqArray = new JSONArray();

                for (ToolProperty prop : properties) {
                    propsObj.put(prop.getName(), prop.toSchemaJson());
                    if (prop.isRequired()) {
                        reqArray.put(prop.getName());
                    }
                }

                inputSchema.put("properties", propsObj);
                if (reqArray.length() > 0) {
                    inputSchema.put("required", reqArray);
                }
            }

            toolObj.put("inputSchema", inputSchema);
        } catch (JSONException e) {
            // Should not happen
        }
        return toolObj;
    }

    public static Builder builder(String name, String description) {
        return new Builder(name, description);
    }

    public static class Builder {
        private final String name;
        private final String description;
        private final List<ToolProperty> properties = new ArrayList<>();

        public Builder(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public Builder addProperty(String name, PropertyType type, String description, boolean required) {
            properties.add(new ToolProperty(name, type, description, required));
            return this;
        }

        public Builder addProperty(String name, PropertyType type, String description) {
            return addProperty(name, type, description, false);
        }

        public McpTool build() {
            return new McpTool(name, description, properties);
        }
    }
}
