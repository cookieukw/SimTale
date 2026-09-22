package com.cookieukw.SimTale.ai;

import java.util.Collection;
import java.util.Map;

public class JsonBuilder {

    private final StringBuilder sb = new StringBuilder();
    private boolean first = true;
    private int depth = 0;

    public static JsonBuilder create() {
        return new JsonBuilder();
    }

    public JsonBuilder object() {
        comma();
        sb.append("{");
        first = true;
        depth++;
        return this;
    }

    public JsonBuilder endObject() {
        sb.append("}");
        first = false;
        depth--;
        return this;
    }

    public void array() {
        comma();
        sb.append("[");
        first = true;
        depth++;
    }

    public void endArray() {
        sb.append("]");
        first = false;
        depth--;
    }

    public JsonBuilder key(String key) {
        comma();
        sb.append("\"").append(escape(key)).append("\":");
        first = true; // next value doesn't need a leading comma
        return this;
    }

    public JsonBuilder value(String value) {
        if (value == null) {
            return nullValue();
        }
        comma();
        sb.append("\"").append(escape(value)).append("\"");
        first = false;
        return this;
    }

    public JsonBuilder value(Number value) {
        if (value == null) {
            return nullValue();
        }
        comma();
        sb.append(value);
        first = false;
        return this;
    }

    public JsonBuilder value(Boolean value) {
        if (value == null) {
            return nullValue();
        }
        comma();
        sb.append(value);
        first = false;
        return this;
    }

    public JsonBuilder nullValue() {
        comma();
        sb.append("null");
        first = false;
        return this;
    }

    public JsonBuilder value(Object value) {
        if (value == null) {
            return nullValue();
        }
        if (value instanceof String) {
            return value((String) value);
        } else if (value instanceof Number) {
            return value((Number) value);
        } else if (value instanceof Boolean) {
            return value((Boolean) value);
        } else if (value instanceof Map) {
            object();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                key(String.valueOf(entry.getKey()));
                value(entry.getValue());
            }
            endObject();
            return this;
        } else if (value instanceof Collection) {
            array();
            for (Object o : (Collection<?>) value) {
                value(o);
            }
            endArray();
            return this;
        } else if (value instanceof AiMessage(String role, String content)) {
            object();
            key("role").value(role);
            key("content").value(content);
            endObject();
            return this;
        }
        return value(value.toString());
    }

    private void comma() {
        if (!first) {
            sb.append(",");
        }
        first = false;
    }

    private String escape(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (c < ' ') {
                        String t = "000" + Integer.toHexString(c);
                        builder.append("\\u").append(t.substring(t.length() - 4));
                    } else {
                        builder.append(c);
                    }
                }
            }
        }
        return builder.toString();
    }

    @Override
    public String toString() {
        return sb.toString();
    }

    public int getDepth() {
        return depth;
    }

    public void setDepth(int depth) {
        this.depth = depth;
    }
}
