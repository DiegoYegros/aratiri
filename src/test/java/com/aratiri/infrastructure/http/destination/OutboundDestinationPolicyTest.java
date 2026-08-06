package com.aratiri.infrastructure.http.destination;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static com.aratiri.infrastructure.http.destination.DestinationPolicyTestSupport.FakeResolver;
import static com.aratiri.infrastructure.http.destination.DestinationPolicyTestSupport.defaultProperties;
import static com.aratiri.infrastructure.http.destination.DestinationPolicyTestSupport.ipv4;
import static com.aratiri.infrastructure.http.destination.DestinationPolicyTestSupport.ipv6;
import static com.aratiri.infrastructure.http.destination.DestinationPolicyTestSupport.labProperties;
import static com.aratiri.infrastructure.http.destination.DestinationPolicyTestSupport.policy;
import static com.aratiri.infrastructure.http.destination.DestinationPolicyTestSupport.policyWithResolver;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutboundDestinationPolicyTest {

  @Test
  void allowsPublicIpv4Https() {
    FakeResolver resolver = new FakeResolver().put("hooks.example.com", ipv4("93.184.216.34"));
    OutboundDestinationPolicy policy = policyWithResolver(resolver);

    assertDoesNotThrow(() -> policy.validate("https://hooks.example.com/webhook"));
    assertEquals(1, resolver.resolveCount());
  }

  @Test
  void rejectsHostnameResolvingToLoopbackOrRfc1918() {
    FakeResolver resolver = new FakeResolver()
        .put("loopback.example.com", ipv4("127.0.0.1"))
        .put("rfc1918.example.com", ipv4("10.0.0.5"));
    OutboundDestinationPolicy policy = policyWithResolver(resolver);

    assertThrows(OutboundDestinationRejectedException.class,
        () -> policy.validate("https://loopback.example.com/hook"));
    assertThrows(OutboundDestinationRejectedException.class,
        () -> policy.validate("https://rfc1918.example.com/hook"));
    assertEquals(2, resolver.resolveCount());
  }

  @Test
  void allowsPublicIpv6Https() {
    FakeResolver resolver = new FakeResolver()
        .put("hooks.example.com", ipv6("2606:2800:220:1:248:1893:25c8:1946"));
    OutboundDestinationPolicy policy = policyWithResolver(resolver);

    assertDoesNotThrow(() -> policy.validate("https://hooks.example.com/path"));
  }

  @Test
  void allowsPublicIpv6LiteralWithAndWithoutPort() {
    OutboundDestinationPolicy policy = policyWithResolver(new FakeResolver());
    assertDoesNotThrow(() -> policy.validate("https://[2001:4860:4860::8888]/hook"));
    assertDoesNotThrow(() -> policy.validate("https://[2001:4860:4860::8888]:8443/hook"));
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "https://127.0.0.1/hook",
      "https://10.0.0.5/hook",
      "https://172.16.1.1/hook",
      "https://192.168.1.1/hook",
      "https://169.254.169.254/latest/meta-data",
      "https://100.64.0.1/hook",
      "https://0.0.0.0/hook",
      "https://192.0.2.1/hook",
      "https://198.51.100.1/hook",
      "https://203.0.113.1/hook",
      "https://198.18.0.1/hook",
      "https://224.0.0.1/hook",
      "https://240.0.0.1/hook",
      "https://255.255.255.255/hook"
  })
  void rejectsProhibitedIpv4Literals(String url) {
    OutboundDestinationPolicy policy = policyWithResolver(new FakeResolver());
    OutboundDestinationRejectedException ex = assertThrows(
        OutboundDestinationRejectedException.class,
        () -> policy.validate(url));
    assertEquals(OutboundDestinationRejectedException.PUBLIC_MESSAGE, ex.getMessage());
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "::1",
      "fe80::1",
      "fc00::1",
      "fd12:3456:789a::1",
      "ff02::1",
      "2001:db8::1",
      "::ffff:127.0.0.1",
      "::ffff:10.0.0.1",
      "::",
      "::10.0.0.1",
      "64:ff9b::10.0.0.1",
      "64:ff9b::169.254.169.254",
      "64:ff9b:1::10.0.0.1",
      "2002:0a00:0001::1",
      "2001:0:4136:e378:8000:63bf:3fff:fdd2"
  })
  void prohibitedIpv6AddressesReachNetworkClassifier(String literal) {
    assertTrue(OutboundDestinationPolicy.isProhibitedAddress(ipv6(literal)), literal);
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "https://[::1]/hook",
      "https://[fe80::1]/hook",
      "https://[fc00::1]/hook",
      "https://[fd12:3456:789a::1]/hook",
      "https://[ff02::1]/hook",
      "https://[2001:db8::1]/hook",
      "https://[::ffff:127.0.0.1]/hook",
      "https://[::ffff:10.0.0.1]/hook",
      "https://[::]/hook",
      "https://[::10.0.0.1]/hook",
      "https://[64:ff9b::10.0.0.1]/hook",
      "https://[64:ff9b::169.254.169.254]/hook",
      "https://[64:ff9b:1::10.0.0.1]/hook",
      "https://[2002:0a00:0001::1]/hook",
      "https://[2001:0:4136:e378:8000:63bf:3fff:fdd2]/hook"
  })
  void rejectsProhibitedIpv6LiteralsAfterAuthorityCanonicalization(String url) {
    OutboundDestinationPolicy policy = policyWithResolver(new FakeResolver());
    assertThrows(OutboundDestinationRejectedException.class, () -> policy.validate(url));
  }

  @Test
  void privateIpv6LiteralRejectionIsClassifierNotAuthorityFormatting() {
    OutboundDestinationProperties lab = defaultProperties();
    lab.setAllowPrivateNetworks(true);
    OutboundDestinationPolicy labPolicy = policy(lab, new FakeResolver());

    // Authority/canonicalization accepts bracketed IPv6; only the network classifier blocks it.
    assertDoesNotThrow(() -> labPolicy.validate("https://[::1]/hook"));
    assertDoesNotThrow(() -> labPolicy.validate("https://[::10.0.0.1]/hook"));
    assertDoesNotThrow(() -> labPolicy.validate("https://[64:ff9b::10.0.0.1]/hook"));

    OutboundDestinationPolicy strict = policyWithResolver(new FakeResolver());
    assertTrue(OutboundDestinationPolicy.isProhibitedAddress(ipv6("::1")));
    assertTrue(OutboundDestinationPolicy.isProhibitedAddress(ipv6("::10.0.0.1")));
    assertTrue(OutboundDestinationPolicy.isProhibitedAddress(ipv6("64:ff9b::10.0.0.1")));
    assertThrows(OutboundDestinationRejectedException.class,
        () -> strict.validate("https://[::1]/hook"));
    assertThrows(OutboundDestinationRejectedException.class,
        () -> strict.validate("https://[::10.0.0.1]/hook"));
    assertThrows(OutboundDestinationRejectedException.class,
        () -> strict.validate("https://[64:ff9b::10.0.0.1]/hook"));
  }

  @Test
  void rejectsTransitionPrefixDnsAnswers() {
    FakeResolver resolver = new FakeResolver()
        .put("compat.example.com", ipv6("::10.0.0.1"))
        .put("nat64.example.com", ipv6("64:ff9b::10.0.0.1"))
        .put("nat64-meta.example.com", ipv6("64:ff9b::169.254.169.254"))
        .put("nat64-local.example.com", ipv6("64:ff9b:1::10.0.0.1"))
        .put("sixto4.example.com", ipv6("2002:0a00:0001::1"))
        .put("teredo.example.com", ipv6("2001:0:4136:e378:8000:63bf:3fff:fdd2"))
        .put("ok.example.com", ipv6("2001:4860:4860::8888"));
    OutboundDestinationPolicy policy = policyWithResolver(resolver);

    assertThrows(OutboundDestinationRejectedException.class,
        () -> policy.validate("https://compat.example.com/hook"));
    assertThrows(OutboundDestinationRejectedException.class,
        () -> policy.validate("https://nat64.example.com/hook"));
    assertThrows(OutboundDestinationRejectedException.class,
        () -> policy.validate("https://nat64-meta.example.com/hook"));
    assertThrows(OutboundDestinationRejectedException.class,
        () -> policy.validate("https://nat64-local.example.com/hook"));
    assertThrows(OutboundDestinationRejectedException.class,
        () -> policy.validate("https://sixto4.example.com/hook"));
    assertThrows(OutboundDestinationRejectedException.class,
        () -> policy.validate("https://teredo.example.com/hook"));
    assertDoesNotThrow(() -> policy.validate("https://ok.example.com/hook"));
  }

  @Test
  void rejectsMixedPublicAndPrivateDnsAnswers() {
    FakeResolver resolver = new FakeResolver()
        .put("evil.example.com", ipv4("93.184.216.34"), ipv4("10.0.0.1"));
    OutboundDestinationPolicy policy = policyWithResolver(resolver);

    assertThrows(OutboundDestinationRejectedException.class,
        () -> policy.validate("https://evil.example.com/hook"));
  }

  @Test
  void rejectsDnsFailure() {
    FakeResolver resolver = new FakeResolver().fail("missing.example.com");
    OutboundDestinationPolicy policy = policyWithResolver(resolver);

    assertThrows(OutboundDestinationRejectedException.class,
        () -> policy.validate("https://missing.example.com/hook"));
  }

  @Test
  void rejectsEmptyDnsAnswers() {
    OutboundHostResolver empty = host -> List.of();
    OutboundDestinationPolicy policy = policyWithResolver(empty);

    assertThrows(OutboundDestinationRejectedException.class,
        () -> policy.validate("https://empty.example.com/hook"));
  }

  @Test
  void normalizesHostCaseAndTrailingDot() {
    FakeResolver resolver = new FakeResolver().put("hooks.example.com", ipv4("93.184.216.34"));
    OutboundDestinationPolicy policy = policyWithResolver(resolver);

    assertDoesNotThrow(() -> policy.validate("https://Hooks.EXAMPLE.com./hooks"));
    assertEquals(1, resolver.resolveCount());
  }

  @Test
  void normalizeHost_convertsIdnToAscii() {
    assertEquals("xn--bcher-kva.example",
        OutboundDestinationPolicy.normalizeHost("Bücher.example"));
  }

  @Test
  void normalizeHost_stripsSingleIpv6BracketLayer() {
    assertEquals("2001:4860:4860::8888",
        OutboundDestinationPolicy.normalizeHost("[2001:4860:4860::8888]"));
  }

  @Test
  void allowlistExactHostRequiredWhenConfigured() {
    OutboundDestinationProperties properties = defaultProperties();
    properties.setAllowedHosts(List.of("hooks.example.com"));
    FakeResolver resolver = new FakeResolver()
        .put("hooks.example.com", ipv4("93.184.216.34"))
        .put("other.example.com", ipv4("93.184.216.34"));
    OutboundDestinationPolicy policy = policy(properties, resolver);

    assertDoesNotThrow(() -> policy.validate("https://hooks.example.com/a"));
    assertThrows(OutboundDestinationRejectedException.class,
        () -> policy.validate("https://other.example.com/a"));
  }

  @Test
  void allowlistMatchesNormalizedIdnCaseAndTrailingDot() {
    OutboundDestinationProperties properties = defaultProperties();
    properties.setAllowedHosts(List.of("XN--BCHER-KVA.EXAMPLE."));
    FakeResolver resolver = new FakeResolver()
        .put("xn--bcher-kva.example", ipv4("93.184.216.34"));
    OutboundDestinationPolicy policy = policy(properties, resolver);

    assertDoesNotThrow(() -> policy.validate("https://xn--bcher-kva.example/hook"));
    assertEquals(
        "xn--bcher-kva.example",
        OutboundDestinationPolicy.normalizeHost("Bücher.EXAMPLE."));
  }

  @Test
  void allowlistMatchesIpv6LiteralEntriesWithOptionalBrackets() {
    OutboundDestinationProperties properties = defaultProperties();
    properties.setAllowedHosts(List.of("[2001:4860:4860::8888]"));
    OutboundDestinationPolicy policy = policy(properties, new FakeResolver());

    assertDoesNotThrow(() -> policy.validate("https://[2001:4860:4860::8888]/hook"));
    assertDoesNotThrow(() -> policy.validate("https://[2001:4860:4860::8888]:443/hook"));

    properties.setAllowedHosts(List.of("2001:4860:4860::8888"));
    OutboundDestinationPolicy unbracketedAllow = policy(properties, new FakeResolver());
    assertDoesNotThrow(() -> unbracketedAllow.validate("https://[2001:4860:4860::8888]/hook"));

    assertThrows(OutboundDestinationRejectedException.class,
        () -> unbracketedAllow.validate("https://[2001:4860:4860::8844]/hook"));
  }

  @Test
  void allowlistWildcardSuffixMatchesUnambiguously() {
    OutboundDestinationProperties properties = defaultProperties();
    properties.setAllowedHosts(List.of("*.example.com"));
    FakeResolver resolver = new FakeResolver()
        .put("a.example.com", ipv4("93.184.216.34"))
        .put("example.com", ipv4("93.184.216.34"))
        .put("a.example.com.evil.com", ipv4("93.184.216.34"));
    OutboundDestinationPolicy policy = policy(properties, resolver);

    assertDoesNotThrow(() -> policy.validate("https://a.example.com/hook"));
    assertThrows(OutboundDestinationRejectedException.class,
        () -> policy.validate("https://example.com/hook"));
    assertThrows(OutboundDestinationRejectedException.class,
        () -> policy.validate("https://a.example.com.evil.com/hook"));
  }

  @Test
  void allowlistIgnoresUserRegexStylePatterns() {
    OutboundDestinationProperties properties = defaultProperties();
    properties.setAllowedHosts(List.of(".*\\.example\\.com"));
    FakeResolver resolver = new FakeResolver().put("hooks.example.com", ipv4("93.184.216.34"));
    OutboundDestinationPolicy policy = policy(properties, resolver);

    assertThrows(OutboundDestinationRejectedException.class,
        () -> policy.validate("https://hooks.example.com/hook"));
  }

  @Test
  void allowlistMalformedEntriesFailClosedPerRequest() {
    OutboundDestinationProperties properties = defaultProperties();
    properties.setAllowedHosts(List.of("0x7f000001", "[bad", "hooks.example.com"));
    FakeResolver resolver = new FakeResolver().put("hooks.example.com", ipv4("93.184.216.34"));
    OutboundDestinationPolicy policy = policy(properties, resolver);

    assertDoesNotThrow(() -> policy.validate("https://hooks.example.com/hook"));
    properties.setAllowedHosts(List.of("0x7f000001", "[bad"));
    OutboundDestinationPolicy onlyBad = policy(properties, resolver);
    assertThrows(OutboundDestinationRejectedException.class,
        () -> onlyBad.validate("https://hooks.example.com/hook"));
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "http://hooks.example.com/hook",
      "ftp://hooks.example.com/hook",
      "hooks.example.com/hook",
      "/relative",
      "https://user:pass@hooks.example.com/hook",
      "https://hooks.example.com/hook#frag",
      "https:///path",
      "https://hooks.example.com:99999/hook",
      "https://0x7f000001/hook",
      "https://2130706433/hook",
      "https://0177.0.0.1/hook",
      "https://127.0.0.1/hook",
      "https://hooks.example.com\\@evil.com/",
      "https://[fe80::1%25eth0]/hook"
  })
  void rejectsSchemeUserinfoFragmentPortAmbiguousAndAlternativeForms(String url) {
    FakeResolver resolver = new FakeResolver().put("hooks.example.com", ipv4("93.184.216.34"));
    OutboundDestinationPolicy policy = policyWithResolver(resolver);
    assertThrows(OutboundDestinationRejectedException.class, () -> policy.validate(url));
  }

  @Test
  void allowHttpEscapeHatchIsExplicit() {
    OutboundDestinationProperties properties = defaultProperties();
    properties.setAllowHttp(true);
    FakeResolver resolver = new FakeResolver().put("hooks.example.com", ipv4("93.184.216.34"));
    OutboundDestinationPolicy policy = policy(properties, resolver);

    assertDoesNotThrow(() -> policy.validate("http://hooks.example.com/hook"));
  }

  @Test
  void allowPrivateNetworksEscapeHatchIsExplicit() {
    OutboundDestinationProperties properties = defaultProperties();
    properties.setAllowPrivateNetworks(true);
    OutboundDestinationPolicy policy = policy(properties, new FakeResolver());

    assertDoesNotThrow(() -> policy.validate("https://127.0.0.1/hook"));
    assertDoesNotThrow(() -> policy.validate("https://10.0.0.1/hook"));
  }

  @Test
  void labEscapeHatchesAllowHttpAndPrivateTogether() {
    FakeResolver resolver = new FakeResolver()
        .put("lab.example.com", ipv4("10.0.0.5"));
    OutboundDestinationPolicy policy = policy(labProperties(), resolver);

    assertDoesNotThrow(() -> policy.validate("http://127.0.0.1/hook"));
    assertDoesNotThrow(() -> policy.validate("http://10.0.0.1/hook"));
    assertDoesNotThrow(() -> policy.validate("http://lab.example.com/hook"));
    assertDoesNotThrow(() -> policy.validate("https://169.254.169.254/meta"));
  }

  @Test
  void defaultsRemainFailClosed() {
    OutboundDestinationProperties properties = defaultProperties();
    assertFalse(properties.isAllowHttp());
    assertFalse(properties.isAllowPrivateNetworks());
    assertTrue(properties.getAllowedHosts().isEmpty());
  }

  @Test
  void publicIpv4LiteralAllowed() {
    OutboundDestinationPolicy policy = policyWithResolver(new FakeResolver());
    assertDoesNotThrow(() -> policy.validate("https://93.184.216.34/hook"));
  }

  @Test
  void hostnameResolvingOnlyToPublicIpv6Allowed() {
    FakeResolver resolver = new FakeResolver()
        .put("ipv6.example.com", ipv6("2001:4860:4860::8888"));
    OutboundDestinationPolicy policy = policyWithResolver(resolver);
    assertDoesNotThrow(() -> policy.validate("https://ipv6.example.com/hook"));
  }

  @Test
  void rejectsHostnameResolvingToLinkLocal() {
    FakeResolver resolver = new FakeResolver()
        .put("meta.example.com", ipv4("169.254.169.254"));
    OutboundDestinationPolicy policy = policyWithResolver(resolver);
    assertThrows(OutboundDestinationRejectedException.class,
        () -> policy.validate("https://meta.example.com/"));
  }

  @Test
  void prohibitedAddressClassifierCoversSiteLocal() {
    assertTrue(OutboundDestinationPolicy.isProhibitedAddress(ipv4("192.168.0.1")));
    assertTrue(OutboundDestinationPolicy.isProhibitedAddress(ipv6("::1")));
  }

  @Test
  void ordinaryGlobalUnicastIpv6RemainsAllowed() {
    assertFalse(OutboundDestinationPolicy.isProhibitedAddress(ipv6("2001:4860:4860::8888")));
    assertFalse(OutboundDestinationPolicy.isProhibitedAddress(
        ipv6("2606:2800:220:1:248:1893:25c8:1946")));
  }
}
