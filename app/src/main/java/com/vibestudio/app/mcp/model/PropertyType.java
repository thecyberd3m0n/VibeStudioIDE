package com.vibestudio.app.mcp.model;

public enum PropertyType {
    STRING("string"),
    INTEGER("integer"),
    BOOLEAN("boolean"),
    OBJECT("object"),
    ARRAY("array");

    private final String value;

    PropertyType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
