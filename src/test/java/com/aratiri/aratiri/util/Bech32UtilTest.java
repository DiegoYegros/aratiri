package com.aratiri.aratiri.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class Bech32UtilTest {

    @Test
    void testEncodeLnurl() {
        String url = "https://service.com/api?q=123";
        String expectedLnurl = "lnurl1dp68gurn8ghj7mrww4exctt5dahkccn00qhxget8wfjk2um0veca2";
        assertEquals(expectedLnurl, Bech32Util.encodeLnurl(url));
    }

    @Test
    void testBech32Decode() {
        String lnurl = "lnurl1dp68gurn8ghj7mrww4exctt5dahkccn00qhxget8wfjk2um0veca2";
        String expectedUrl = "https://service.com/api?q=123";
        Bech32Util.Bech32Data decoded = Bech32Util.bech32Decode(lnurl);
        assertEquals("lnurl", decoded.hrp);
        assertEquals(expectedUrl, new String(Bech32Util.convertBits(decoded.data, 5, 8, false)));
    }

    @Test
    void testNpubToHex() {
        String npub = "npub180cvv07tjdrrr6pln0v7wgyut40005sflkn8000l2h7kv7wdv4rqs0ftfk";
        String expectedHex = "0000000000000000000000000000000000000000000000000000000000000001";
        assertEquals(expectedHex, NostrUtil.npubToHex(npub));
    }

    @Test
    void testInvalidChecksum() {
        String invalidLnurl = "lnurl1dp68gurn8ghj7mrww4exctt5dahkccn00qhxget8wfjk2um0veca3";
        assertThrows(IllegalArgumentException.class, () -> Bech32Util.bech32Decode(invalidLnurl));
    }

    @Test
    void testMixedCase() {
        String mixedCaseLnurl = "Lnurl1dp68gurn8ghj7mrww4exctt5dahkccn00qhxget8wfjk2um0veca2";
        assertThrows(IllegalArgumentException.class, () -> Bech32Util.bech32Decode(mixedCaseLnurl));
    }

    @Test
    void testInvalidCharacter() {
        String invalidCharLnurl = "lnurl1dp68gurn8ghj7mrww4exctt5dahkccn00qhxget8wfjk2um0veca!";
        assertThrows(IllegalArgumentException.class, () -> Bech32Util.bech32Decode(invalidCharLnurl));
    }
}