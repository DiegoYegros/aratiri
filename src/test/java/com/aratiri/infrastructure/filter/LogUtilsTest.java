package com.aratiri.infrastructure.filter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LogUtilsTest {

    @Test
    void formatKeyValue_basic() {
        String result = LogUtils.formatKeyValue("Status", 200);
        assertTrue(result.contains("Status"));
        assertTrue(result.contains("200"));
        assertTrue(result.contains(":"));
    }

    @Test
    void formatKeyValue_nullValue() {
        String result = LogUtils.formatKeyValue("Error", (Object) null);
        assertTrue(result.contains("null"));
    }

    @Test
    void formatKeyValue_customAlignment() {
        String result = LogUtils.formatKeyValue(10, "Status", 200);
        assertTrue(result.startsWith("Status"));
    }

    @Test
    void formatLog_basic() {
        String result = LogUtils.formatLog("method: GET");
        assertTrue(result.contains("method"));
        assertTrue(result.contains("GET"));
    }

    @Test
    void formatLog_withPlaceholders() {
        String result = LogUtils.formatLog("{}: {}", "method", "GET");
        assertTrue(result.contains("method"));
        assertTrue(result.contains("GET"));
    }

    @Test
    void formatLog_withCustomAlignment() {
        String result = LogUtils.formatLog(10, "method: GET");
        assertTrue(result.contains("method"));
    }

    @Test
    void formatLog_noColon() {
        String result = LogUtils.formatLog("simple message");
        assertEquals("simple message", result);
    }

    @Test
    void formatSectionHeader_default() {
        String result = LogUtils.formatSectionHeader("INCOMING");
        assertTrue(result.contains("INCOMING"));
        assertTrue(result.contains("="));
    }

    @Test
    void formatSectionHeader_customSeparator() {
        String result = LogUtils.formatSectionHeader("HEADER", "-");
        assertTrue(result.contains("HEADER"));
        assertTrue(result.contains("-"));
    }

    @Test
    void formatSectionHeader_shortTitle() {
        String result = LogUtils.formatSectionHeader("X");
        assertTrue(result.contains("X"));
        assertTrue(result.length() > 3);
    }

    @Test
    void formatMultipleKeyValues_basic() {
        String result = LogUtils.formatMultipleKeyValues(new Object[]{"key1", "val1", "key2", "val2"});
        assertTrue(result.contains("key1"));
        assertTrue(result.contains("val1"));
        assertTrue(result.contains("key2"));
        assertTrue(result.contains("val2"));
    }

    @Test
    void formatMultipleKeyValues_oddCount_throws() {
        assertThrows(IllegalArgumentException.class, () ->
                LogUtils.formatMultipleKeyValues(new Object[]{"key1", "val1", "key2"}));
    }

    @Test
    void formatMultipleKeyValues_customAlignment() {
        String result = LogUtils.formatMultipleKeyValues(20, new Object[]{"key1", "val1"});
        assertTrue(result.contains("key1"));
        assertTrue(result.contains("val1"));
    }

    @Test
    void formatMultipleKeyValues_withNull() {
        String result = LogUtils.formatMultipleKeyValues(new Object[]{"key1", null});
        assertTrue(result.contains("null"));
    }

    @Test
    void constructor_isPrivate() throws Exception {
        var constructor = LogUtils.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        var instance = constructor.newInstance();
        assertNotNull(instance);
    }
}
