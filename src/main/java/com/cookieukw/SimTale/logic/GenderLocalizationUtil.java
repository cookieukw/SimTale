package com.cookieukw.SimTale.logic;

import com.cookieukw.SimTale.core.Gender;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class to dynamically localize and format gender-specific text.
 * Replaces patterns like "{ele|ela}" or "{cansado|cansada}" depending on the target gender.
 */
public class GenderLocalizationUtil {

    private static final Pattern GENDER_PATTERN = Pattern.compile("\\{([^|{}]+)\\|([^|{}]+)\\}");

    /**
     * Formats a text by replacing gender-specific markers with the appropriate term.
     * Example: format("{ele|ela} está {cansado|cansada}", Gender.FEMALE) -> "ela está cansada"
     *
     * @param text   The raw translation string containing patterns like {masculino|feminino}
     * @param gender The target Gender (MALE or FEMALE)
     * @return The formatted text
     */
    public static String format(String text, Gender gender) {
        if (text == null) return null;
        if (gender == null) return text;

        Matcher matcher = GENDER_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String replacement = (gender == Gender.FEMALE) ? matcher.group(2) : matcher.group(1);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
