package com.eu.habbo.habbohotel.items.interactions.wired.utils;

import com.eu.habbo.Emulator;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WiredMessageFormatter {
    private static final int DEFAULT_VISIBLE_MAX_LENGTH = 300;
    private static final int MAX_VISIBLE_LINES = 8;
    public static final int BUBBLE_WIDTH_MIN = 55;
    public static final int BUBBLE_WIDTH_DEFAULT = 125;
    public static final int BUBBLE_WIDTH_STANDARD = BUBBLE_WIDTH_DEFAULT;
    public static final int BUBBLE_WIDTH_MAX = 900;
    public static final int TEXT_ALIGN_LEFT = 0;
    public static final int TEXT_ALIGN_CENTER = 1;
    public static final int TEXT_ALIGN_RIGHT = 2;
    private static final Pattern FORMATTING_TAG_PATTERN = Pattern.compile("\\G\\[(?:/?(?:b|i|u|red|blue|green|cyan|purple|left|center|right|wave|shake|cuss|pulse|line)|/?#[0-9a-fA-F]{6}|nw-width:\\d{1,4}|nw-align:(?:left|center|right))\\]");
    private static final Pattern ANY_FORMATTING_TAG_PATTERN = Pattern.compile("\\[(?:/?(?:b|i|u|red|blue|green|cyan|purple|left|center|right|wave|shake|cuss|pulse|line)|/?#[0-9a-fA-F]{6}|nw-width:\\d{1,4}|nw-align:(?:left|center|right))\\]", Pattern.CASE_INSENSITIVE);
    private static final Pattern LAYOUT_TAG_PATTERN = Pattern.compile("\\[(?:nw-width:\\d{1,4}|nw-align:(?:left|center|right))\\]", Pattern.CASE_INSENSITIVE);
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\([A-Za-z0-9_]{1,40}\\)");

    private WiredMessageFormatter() {
    }

    public static int maxVisibleLength() {
        return Math.max(DEFAULT_VISIBLE_MAX_LENGTH, Emulator.getConfig().getInt("hotel.wired.message.max_length", DEFAULT_VISIBLE_MAX_LENGTH));
    }

    public static String limitVisibleLength(String message) {
        return limitVisibleLength(message, maxVisibleLength());
    }

    public static String limitVisibleLength(String message, int maxLength) {
        if (message == null || message.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder(message.length());
        Matcher tagMatcher = FORMATTING_TAG_PATTERN.matcher(message);
        int visibleLength = 0;
        int lineCount = 1;

        for (int i = 0; i < message.length();) {
            tagMatcher.region(i, message.length());

            if (tagMatcher.lookingAt()) {
                result.append(tagMatcher.group());
                i = tagMatcher.end();
                continue;
            }

            if (visibleLength >= maxLength) {
                break;
            }

            if (message.charAt(i) == '\n' && ++lineCount > MAX_VISIBLE_LINES) {
                break;
            }

            result.append(message.charAt(i));
            visibleLength++;
            i++;
        }

        return result.toString();
    }

    public static String filterPreservingPlaceholders(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(message);
        StringBuilder filtered = new StringBuilder(message.length());
        int cursor = 0;

        while (matcher.find()) {
            filtered.append(Emulator.getGameEnvironment().getWordFilter().filter(
                    message.substring(cursor, matcher.start()),
                    null));
            filtered.append(matcher.group());
            cursor = matcher.end();
        }

        filtered.append(Emulator.getGameEnvironment().getWordFilter().filter(
                message.substring(cursor),
                null));
        return filtered.toString();
    }

    public static int normalizeBubbleWidth(int width) {
        if (width <= 0) {
            return BUBBLE_WIDTH_DEFAULT;
        }

        if (width < BUBBLE_WIDTH_MIN) {
            return BUBBLE_WIDTH_MIN;
        }

        if (width > BUBBLE_WIDTH_MAX) {
            return BUBBLE_WIDTH_MAX;
        }

        return width;
    }

    public static int normalizeTextAlignment(int alignment) {
        if (alignment == TEXT_ALIGN_CENTER || alignment == TEXT_ALIGN_RIGHT) {
            return alignment;
        }

        return TEXT_ALIGN_LEFT;
    }

    public static String withLayout(String message, int bubbleWidth, int textAlignment) {
        return "[nw-width:" + normalizeBubbleWidth(bubbleWidth) + "][nw-align:" + textAlignmentName(textAlignment) + "]" + withoutLayout(message);
    }

    public static String withoutLayout(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }

        return LAYOUT_TAG_PATTERN.matcher(message).replaceAll("");
    }

    public static String withoutFormatting(String message) {
        if (message == null || message.isEmpty()) {
            return "";
        }

        return ANY_FORMATTING_TAG_PATTERN.matcher(message).replaceAll("");
    }

    private static String textAlignmentName(int alignment) {
        switch (normalizeTextAlignment(alignment)) {
            case TEXT_ALIGN_CENTER:
                return "center";
            case TEXT_ALIGN_RIGHT:
                return "right";
            default:
                return "left";
        }
    }
}
