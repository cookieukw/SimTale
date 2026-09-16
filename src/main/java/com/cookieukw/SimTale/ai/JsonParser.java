package com.cookieukw.SimTale.ai;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JsonParser {

    /**
     * Extracts a value from a JSON string using a dotted path (e.g., "choices.0.message.content").
     */
    public static String extractPath(String json, String path) {
        if (json == null || json.isBlank() || path == null || path.isBlank()) {
            return null;
        }

        String[] segments = path.split("\\.");
        String currentSection = json;

        for (String segment : segments) {
            if (currentSection == null || currentSection.isBlank()) {
                return null;
            }

            // Clean leading/trailing spaces
            currentSection = currentSection.trim();

            // Try to see if segment is an array index
            try {
                int index = Integer.parseInt(segment);
                currentSection = getArrayElement(currentSection, index);
            } catch (NumberFormatException e) {
                currentSection = getObjectField(currentSection, segment);
            }
        }

        if (currentSection == null) return null;
        currentSection = currentSection.trim();

        // If it's a quoted string, unescape and strip quotes
        if (currentSection.startsWith("\"") && currentSection.endsWith("\"")) {
            String value = currentSection.substring(1, currentSection.length() - 1);
            return unescape(value);
        }

        return currentSection;
    }

    private static String getObjectField(String json, String field) {
        /* Simple search for key "field" followed by optional space, colon, optional space
        Handles quotes: either "field" or 'field' (standard is double quotes)
        */
        String patternStr = "\"" + Pattern.quote(field) + "\"\\s*:\\s*";
        Pattern pattern = Pattern.compile(patternStr);
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find()) {
            return null;
        }

        int startValueIndex = matcher.end();
        return extractJsonValue(json, startValueIndex);
    }

    private static String getArrayElement(String json, int index) {
        json = json.trim();
        if (!json.startsWith("[")) {
            return null;
        }

        int pos = 1;
        int currentIdx = 0;
        int len = json.length();

        while (pos < len) {
            char c = json.charAt(pos);
            if (Character.isWhitespace(c) || c == ',') {
                pos++;
                continue;
            }
            if (c == ']') {
                break;
            }

            String val = extractJsonValue(json, pos);
            if (val == null) {
                return null;
            }

            if (currentIdx == index) {
                return val;
            }

            pos += val.length();
            currentIdx++;
        }

        return null;
    }

    private static String extractJsonValue(String json, int startIndex) {
        int pos = startIndex;
        int len = json.length();

        // Skip leading whitespace
        while (pos < len && Character.isWhitespace(json.charAt(pos))) {
            pos++;
        }

        if (pos >= len) {
            return null;
        }

        char firstChar = json.charAt(pos);

        if (firstChar == '"') {
            // String: find matching unescaped quote
            int start = pos;
            pos++; // Skip first quote
            while (pos < len) {
                char c = json.charAt(pos);
                if (c == '\\') {
                    pos += 2; // Skip escaped character
                } else if (c == '"') {
                    pos++; // Include closing quote
                    return json.substring(start, pos);
                } else {
                    pos++;
                }
            }
            return null;
        } else if (firstChar == '{') {
            // Object: count braces
            int start = pos;
            int braces = 1;
            pos++;
            while (pos < len && braces > 0) {
                char c = json.charAt(pos);
                if (c == '"') {
                    // Skip string contents
                    pos++;
                    while (pos < len) {
                        char sc = json.charAt(pos);
                        if (sc == '\\') {
                            pos += 2;
                        } else if (sc == '"') {
                            pos++;
                            break;
                        } else {
                            pos++;
                        }
                    }
                } else {
                    if (c == '{') braces++;
                    else if (c == '}') braces--;
                    pos++;
                }
            }
            return braces == 0 ? json.substring(start, pos) : null;
        } else if (firstChar == '[') {
            // Array: count brackets
            int start = pos;
            int brackets = 1;
            pos++;
            while (pos < len && brackets > 0) {
                char c = json.charAt(pos);
                if (c == '"') {
                    // Skip string contents
                    pos++;
                    while (pos < len) {
                        char sc = json.charAt(pos);
                        if (sc == '\\') {
                            pos += 2;
                        } else if (sc == '"') {
                            pos++;
                            break;
                        } else {
                            pos++;
                        }
                    }
                } else {
                    if (c == '[') brackets++;
                    else if (c == ']') brackets--;
                    pos++;
                }
            }
            return brackets == 0 ? json.substring(start, pos) : null;
        } else {
            // Number, boolean, null: read until delimiter (comma, brace, bracket, whitespace)
            int start = pos;
            while (pos < len) {
                char c = json.charAt(pos);
                if (c == ',' || c == '}' || c == ']' || Character.isWhitespace(c)) {
                    break;
                }
                pos++;
            }
            return json.substring(start, pos);
        }
    }

    private static String unescape(String s) {
        StringBuilder sb = new StringBuilder();
        int len = s.length();
        int pos = 0;
        while (pos < len) {
            char c = s.charAt(pos);
            if (c == '\\' && pos + 1 < len) {
                pos++;
                char next = s.charAt(pos);
                switch (next) {
                    case '\"' -> sb.append('\"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (pos + 4 < len) {
                            String hex = s.substring(pos + 1, pos + 5);
                            sb.append((char) Integer.parseInt(hex, 16));
                            pos += 4;
                        }
                    }
                    default -> sb.append(next);
                }
            } else {
                sb.append(c);
            }
            pos++;
        }
        return sb.toString();
    }
}
