package com.aratiri.util;

import com.aratiri.shared.util.Bech32Util;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class Bech32UtilTest {

    @Test
    void testEncodeLnurl() {
        String url = "https://aratiri.diegoyegros.com/.well-known/lnurlp/silentkoala91";
        String expectedLnurl = "lnurl1dp68gurn8ghj7ctjv96xjunf9ejxjet8dauk2emjdaejucm0d5hjuam9d3kz66mwdamkutmvde6hymrs9aekjmr9de6xkmmpd3snjvgxlxfwt";
        assertEquals(expectedLnurl, Bech32Util.encodeLnurl(url));
    }

    @Test
    void testBech32Decode() {
        String lnurl = "lnurl1dp68gurn8ghj7ctjv96xjunf9ejxjet8dauk2emjdaejucm0d5hjuam9d3kz66mwdamkutmvde6hymrs9aekjmr9de6xkmmpd3snjvgxlxfwt";
        String expectedUrl = "https://aratiri.diegoyegros.com/.well-known/lnurlp/silentkoala91";
        Bech32Util.Bech32Data decoded = Bech32Util.bech32Decode(lnurl);
        assertEquals("lnurl", decoded.hrp());
        assertEquals(expectedUrl, new String(Bech32Util.convertBits(decoded.data(), 5, 8, false)));
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