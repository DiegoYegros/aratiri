package com.aratiri.aratiri.utils;

public class LogUtils {

    private static final int DEFAULT_ALIGNMENT = 30;

    /**
     * Formats a log message with standardized alignment using default position (30 chars)
     *
     * @param message The log message with {} placeholders
     * @param args    Arguments to replace placeholders
     * @return Formatted string with aligned colons
     */
    public static String formatLog(String message, Object... args) {
        return formatLog(DEFAULT_ALIGNMENT, message, args);
    }

    /**
     * Formats a log message with standardized alignment at specified position
     *
     * @param alignmentPosition Position where colons should be aligned
     * @param message           The log message with {} placeholders
     * @param args              Arguments to replace placeholders
     * @return Formatted string with aligned colons
     */
    public static String formatLog(int alignmentPosition, String message, Object... args) {
        String formatted = formatMessage(message, args);
        int colonIndex = formatted.indexOf(':');
        if (colonIndex == -1) {
            return formatted;
        }
        String beforeColon = formatted.substring(0, colonIndex).trim();
        String afterColon = formatted.substring(colonIndex + 1).trim();
        String paddedBefore = String.format("%-" + alignmentPosition + "s", beforeColon);
        return paddedBefore + ": " + afterColon;
    }

    /**
     * Formats a key-value pair with standardized alignment
     *
     * @param key   The key/label
     * @param value The value
     * @return Formatted string with aligned colon
     */
    public static String formatKeyValue(String key, Object value) {
        return formatKeyValue(DEFAULT_ALIGNMENT, key, value);
    }

    /**
     * Formats a key-value pair with standardized alignment at specified position
     *
     * @param alignmentPosition Position where colons should be aligned
     * @param key               The key/label
     * @param value             The value
     * @return Formatted string with aligned colon
     */
    public static String formatKeyValue(int alignmentPosition, String key, Object value) {
        String paddedKey = String.format("%-" + alignmentPosition + "s", key);
        return paddedKey + ": " + (value != null ? value.toString() : "null");
    }

    /**
     * Creates a formatted section header
     *
     * @param title The section title
     * @return Formatted header with separators
     */
    public static String formatSectionHeader(String title) {
        return formatSectionHeader(title, "=");
    }

    /**
     * Creates a formatted section header with custom separator
     *
     * @param title     The section title
     * @param separator The separator character
     * @return Formatted header with separators
     */
    public static String formatSectionHeader(String title, String separator) {
        int separatorLength = Math.max(3, (50 - title.length()) / 2);
        String separatorString = separator.repeat(separatorLength);
        return separatorString + " " + title + " " + separatorString;
    }

    /**
     * Simple message formatting (replaces {} with arguments)
     *
     * @param message Message template with {} placeholders
     * @param args    Arguments to replace placeholders
     * @return Formatted message
     */
    private static String formatMessage(String message, Object... args) {
        if (args == null || args.length == 0) {
            return message;
        }

        String result = message;
        for (Object arg : args) {
            int index = result.indexOf("{}");
            if (index != -1) {
                String argString = arg != null ? arg.toString() : "null";
                result = result.substring(0, index) + argString + result.substring(index + 2);
            }
        }
        return result;
    }

    /**
     * Formats multiple key-value pairs with consistent alignment
     *
     * @param alignmentPosition Position where colons should be aligned
     * @param keyValuePairs     Pairs of key-value objects (key1, value1, key2, value2, ...)
     * @return Formatted string with each key-value pair on a new line
     */
    public static String formatMultipleKeyValues(int alignmentPosition, Object... keyValuePairs) {
        if (keyValuePairs.length % 2 != 0) {
            throw new IllegalArgumentException("Key-value pairs must be provided in pairs");
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            String key = keyValuePairs[i].toString();
            Object value = keyValuePairs[i + 1];

            if (i > 0) {
                sb.append("\n");
            }
            sb.append(formatKeyValue(alignmentPosition, key, value));
        }

        return sb.toString();
    }

    /**
     * Formats multiple key-value pairs with default alignment
     *
     * @param keyValuePairs Pairs of key-value objects (key1, value1, key2, value2, ...)
     * @return Formatted string with each key-value pair on a new line
     */
    public static String formatMultipleKeyValues(Object... keyValuePairs) {
        return formatMultipleKeyValues(DEFAULT_ALIGNMENT, keyValuePairs);
    }
}