package com.eu.habbo.habbohotel.wired.variables;

import java.util.Locale;

public final class WiredVariableName {
    public static final int MAX_LENGTH = 40;

    private WiredVariableName() {
    }

    public static String normalize(String input) {
        if (input == null) {
            return "";
        }

        String lower = input.toLowerCase(Locale.ROOT).trim();
        StringBuilder builder = new StringBuilder(lower.length());

        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);

            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_') {
                builder.append(c);
            } else if (Character.isWhitespace(c)) {
                builder.append('_');
            }
        }

        int start = 0;
        int end = builder.length();

        while (start < end && builder.charAt(start) == '_') {
            start++;
        }

        while (end > start && builder.charAt(end - 1) == '_') {
            end--;
        }

        return builder.substring(start, end);
    }

    public static boolean isValid(String name) {
        if (name == null || name.isEmpty() || name.length() > MAX_LENGTH) {
            return false;
        }

        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_')) {
                return false;
            }
        }

        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                return true;
            }
        }

        return false;
    }
}
