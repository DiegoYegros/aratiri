package com.aratiri.shared.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class Bech32UtilTest {

    @Test
    void encodeLnurl_generatesBech32() {
        String result = Bech32Util.encodeLnurl("https://aratiri.example.com/.well-known/lnurlp/test");
        assertNotNull(result);
        assertTrue(result.startsWith("lnurl1"));
        assertTrue(result.length() > 6);
    }

    @Test
    void encodeLnurl_differentUrls() {
        String result1 = Bech32Util.encodeLnurl("https://a.com/b");
        String result2 = Bech32Util.encodeLnurl("https://a.com/c");
        assertNotNull(result1);
        assertNotNull(result2);
        assertNotEquals(result1, result2);
    }

    @Test
    void bech32Decode_roundtrip() {
        String encoded = Bech32Util.encodeLnurl("https://aratiri.example.com/test");
        Bech32Util.Bech32Data decoded = Bech32Util.bech32Decode(encoded);
        assertEquals("lnurl", decoded.hrp());
        assertNotNull(decoded.data());

        byte[] converted = Bech32Util.convertBits(decoded.data(), 5, 8, false);
        String url = new String(converted, java.nio.charset.StandardCharsets.UTF_8);
        assertEquals("https://aratiri.example.com/test", url);
    }

    @Test
    void bytesToHex_returnsHexString() {
        byte[] bytes = {0x01, 0x7f, (byte) 0xff};
        String hex = Bech32Util.bytesToHex(bytes);
        assertEquals("017fff", hex);
    }

    @Test
    void bech32Decode_invalid_MixedCase() {
        assertThrows(IllegalArgumentException.class, () -> Bech32Util.bech32Decode("LnURL1..."));
    }

    @Test
    void bech32Decode_invalid_NoSeparator() {
        assertThrows(IllegalArgumentException.class, () -> Bech32Util.bech32Decode("abc"));
    }

    @Test
    void convertBits_noPad_invalidPadding_throws() {
        byte[] input = {(byte) 0xff};
        assertThrows(IllegalArgumentException.class, () -> Bech32Util.convertBits(input, 8, 5, false));
    }

    @Test
    void bech32Data_equalsAndHashCode() {
        Bech32Util.Bech32Data data1 = Bech32Util.bech32Decode(
                Bech32Util.encodeLnurl("https://a.com/b"));
        Bech32Util.Bech32Data data2 = Bech32Util.bech32Decode(
                Bech32Util.encodeLnurl("https://a.com/b"));
        Bech32Util.Bech32Data data3 = Bech32Util.bech32Decode(
                Bech32Util.encodeLnurl("https://a.com/c"));

        assertEquals(data1, data2);
        assertNotEquals(data1, data3);
        assertEquals(data1.hashCode(), data2.hashCode());
    }

    @Test
    void bech32Data_toString() {
        Bech32Util.Bech32Data data = Bech32Util.bech32Decode(
                Bech32Util.encodeLnurl("https://test.com"));
        String str = data.toString();
        assertTrue(str.contains("lnurl"));
        assertTrue(str.contains("Bech32Data"));
    }
}
