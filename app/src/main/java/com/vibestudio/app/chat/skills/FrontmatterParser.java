package com.vibestudio.app.chat.skills;

import android.util.Log;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Splits a markdown file into YAML frontmatter attributes and body. */
public final class FrontmatterParser {
    private static final String TAG = "FrontmatterParser";
    private static final Pattern FRONTMATTER_PATTERN = Pattern.compile("^---\\s*\\n([\\s\\S]*?)\\n---\\s*\\n?([\\s\\S]*)");

    public static final class Result {
        private final Map<String, Object> mAttributes;
        private final String mBody;

        Result(Map<String, Object> attributes, String body) {
            mAttributes = attributes;
            mBody = body;
        }

        public Map<String, Object> getAttributes() {
            return mAttributes;
        }

        public String getBody() {
            return mBody;
        }

        public String getString(String key) {
            return asString(mAttributes.get(key));
        }

        public Boolean getBoolean(String key) {
            return asBoolean(mAttributes.get(key));
        }

        /** Accepts either a YAML sequence or a comma-separated scalar; returns null when the key is absent. */
        public List<String> getStringList(String key) {
            return asStringList(mAttributes.get(key));
        }
    }

    private FrontmatterParser() {}

    public static Result parse(String rawContent) {
        if (rawContent == null) return new Result(Collections.<String, Object>emptyMap(), "");
        Matcher m = FRONTMATTER_PATTERN.matcher(rawContent);
        if (!m.find()) return new Result(Collections.<String, Object>emptyMap(), rawContent);
        return new Result(parseYaml(m.group(1)), m.group(2));
    }

    /** Safe (no object instantiation) load; malformed YAML yields an empty map. */
    static Map<String, Object> parseYaml(String yamlText) {
        try {
            Object loaded = new Yaml(new SafeConstructor(new LoaderOptions())).load(yamlText);
            if (loaded instanceof Map) {
                Map<String, Object> result = new LinkedHashMap<>();
                for (Map.Entry<?, ?> e : ((Map<?, ?>) loaded).entrySet()) {
                    result.put(String.valueOf(e.getKey()), e.getValue());
                }
                return result;
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Invalid YAML frontmatter, ignoring: " + e.getMessage());
        }
        return new LinkedHashMap<>();
    }

    static String asString(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    static Boolean asBoolean(Object value) {
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof String) {
            String s = ((String) value).trim();
            if (s.equalsIgnoreCase("true")) return Boolean.TRUE;
            if (s.equalsIgnoreCase("false")) return Boolean.FALSE;
        }
        return null;
    }

    static List<String> asStringList(Object value) {
        if (value == null) return null;
        List<String> out = new ArrayList<>();
        if (value instanceof Iterable) {
            for (Object o : (Iterable<?>) value) {
                String s = asString(o);
                if (s != null && !s.isEmpty()) out.add(s);
            }
        } else {
            for (String part : String.valueOf(value).split(",")) {
                String s = part.trim();
                if (!s.isEmpty()) out.add(s);
            }
        }
        return out;
    }
}
