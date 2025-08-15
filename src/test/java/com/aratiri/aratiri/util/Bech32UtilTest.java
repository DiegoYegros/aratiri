package com.aratiri.aratiri.util;

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
        assertEquals("lnurl", decoded.hrp);
        assertEquals(expectedUrl, new String(Bech32Util.convertBits(decoded.data, 5, 8, false)));
    }

    @Test
    void testNpubToHex() {
        String npub = "npub1p3rfw7wscmzfn9z3fa74nzgyqe70p57j8mws0e88dh7awjepmzcq7jgxl9";
        String expectedHex = "0c469779d0c6c49994514f7d598904067cf0d3d23edd07e4e76dfdd74b21d8b0";
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