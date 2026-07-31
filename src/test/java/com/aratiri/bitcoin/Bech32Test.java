package com.aratiri.bitcoin;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class Bech32Test {

  @Test
  void encodeLnurl_matchesKnownVector() {
    String url = "https://aratiri.diegoyegros.com/.well-known/lnurlp/silentkoala91";
    String expectedLnurl = "lnurl1dp68gurn8ghj7ctjv96xjunf9ejxjet8dauk2emjdaejucm0d5hjuam9d3kz66mwdamkutmvde6hymrs9aekjmr9de6xkmmpd3snjvgxlxfwt";
    assertEquals(expectedLnurl, Bech32.encodeLnurl(url));
  }

  @Test
  void decode_roundtripKnownVector() {
    String lnurl = "lnurl1dp68gurn8ghj7ctjv96xjunf9ejxjet8dauk2emjdaejucm0d5hjuam9d3kz66mwdamkutmvde6hymrs9aekjmr9de6xkmmpd3snjvgxlxfwt";
    String expectedUrl = "https://aratiri.diegoyegros.com/.well-known/lnurlp/silentkoala91";
    Bech32.Data decoded = Bech32.decode(lnurl);
    assertEquals("lnurl", decoded.hrp());
    assertEquals(expectedUrl, new String(Bech32.convertBits(decoded.data(), 5, 8, false)));
  }

  @Test
  void encodeLnurl_differentUrlsProduceDifferentEncodings() {
    assertNotEquals(Bech32.encodeLnurl("https://a.com/b"), Bech32.encodeLnurl("https://a.com/c"));
  }

  @Test
  void decode_roundtripArbitraryUrl() {
    String encoded = Bech32.encodeLnurl("https://aratiri.example.com/test");
    Bech32.Data decoded = Bech32.decode(encoded);
    assertEquals("lnurl", decoded.hrp());
    String url = new String(Bech32.convertBits(decoded.data(), 5, 8, false), StandardCharsets.UTF_8);
    assertEquals("https://aratiri.example.com/test", url);
  }

  @Test
  void decode_invalidChecksum_throws() {
    String invalidLnurl = "lnurl1dp68gurn8ghj7mrww4exctt5dahkccn00qhxget8wfjk2um0veca3";
    assertThrows(IllegalArgumentException.class, () -> Bech32.decode(invalidLnurl));
  }

  @Test
  void decode_mixedCase_throws() {
    assertThrows(IllegalArgumentException.class, () -> Bech32.decode("Lnurl1dp68gurn8ghj7mrww4exctt5dahkccn00qhxget8wfjk2um0veca2"));
  }

  @Test
  void decode_invalidCharacter_throws() {
    assertThrows(IllegalArgumentException.class, () -> Bech32.decode("lnurl1dp68gurn8ghj7mrww4exctt5dahkccn00qhxget8wfjk2um0veca!"));
  }

  @Test
  void decode_noSeparator_throws() {
    assertThrows(IllegalArgumentException.class, () -> Bech32.decode("abc"));
  }

  @Test
  void convertBits_invalidPadding_throws() {
    byte[] input = {(byte) 0xff};
    assertThrows(IllegalArgumentException.class, () -> Bech32.convertBits(input, 8, 5, false));
  }

  @Test
  void bytesToHex_returnsHexString() {
    assertEquals("017fff", Bech32.bytesToHex(new byte[]{0x01, 0x7f, (byte) 0xff}));
  }

  @Test
  void data_equalsHashCodeAndToString() {
    Bech32.Data data1 = Bech32.decode(Bech32.encodeLnurl("https://a.com/b"));
    Bech32.Data data2 = Bech32.decode(Bech32.encodeLnurl("https://a.com/b"));
    Bech32.Data data3 = Bech32.decode(Bech32.encodeLnurl("https://a.com/c"));

    assertEquals(data1, data2);
    assertNotEquals(data1, data3);
    assertEquals(data1.hashCode(), data2.hashCode());
    assertTrue(data1.toString().contains("lnurl"));
    assertTrue(data1.toString().contains("Bech32.Data"));
  }
}
