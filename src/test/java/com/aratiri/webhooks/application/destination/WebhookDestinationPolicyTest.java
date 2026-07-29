package com.aratiri.webhooks.application.destination;

import com.aratiri.infrastructure.configuration.WebhookDestinationProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static com.aratiri.webhooks.application.destination.DestinationPolicyTestSupport.FakeResolver;
import static com.aratiri.webhooks.application.destination.DestinationPolicyTestSupport.defaultProperties;
import static com.aratiri.webhooks.application.destination.DestinationPolicyTestSupport.ipv4;
import static com.aratiri.webhooks.application.destination.DestinationPolicyTestSupport.ipv6;
import static com.aratiri.webhooks.application.destination.DestinationPolicyTestSupport.policy;
import static com.aratiri.webhooks.application.destination.DestinationPolicyTestSupport.policyWithResolver;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookDestinationPolicyTest {

  @Test
  void allowsPublicIpv4Https() {
    FakeResolver resolver = new FakeResolver().put("hooks.example.com", ipv4("93.184.216.34"));
    WebhookDestinationPolicy policy = policyWithResolver(resolver);

    assertDoesNotThrow(() -> policy.validate("https://hooks.example.com/webhook"));
  }

  @Test
  void allowsPublicIpv6Https() {
    FakeResolver resolver = new FakeResolver()
        .put("hooks.example.com", ipv6("2606:2800:220:1:248:1893:25c8:1946"));
    WebhookDestinationPolicy policy = policyWithResolver(resolver);

    assertDoesNotThrow(() -> policy.validate("https://hooks.example.com/path"));
  }

  @Test
  void allowsPublicIpv6LiteralWithAndWithoutPort() {
    WebhookDestinationPolicy policy = policyWithResolver(new FakeResolver());
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
    WebhookDestinationPolicy policy = policyWithResolver(new FakeResolver());
    WebhookDestinationRejectedException ex = assertThrows(
        WebhookDestinationRejectedException.class,
        () -> policy.validate(url));
    assertEquals(WebhookDestinationRejectedException.PUBLIC_MESSAGE, ex.getMessage());
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
    assertTrue(WebhookDestinationPolicy.isProhibitedAddress(ipv6(literal)), literal);
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
    WebhookDestinationPolicy policy = policyWithResolver(new FakeResolver());
    assertThrows(WebhookDestinationRejectedException.class, () -> policy.validate(url));
  }

  @Test
  void privateIpv6LiteralRejectionIsClassifierNotAuthorityFormatting() {
    WebhookDestinationProperties lab = defaultProperties();
    lab.setAllowPrivateNetworks(true);
    WebhookDestinationPolicy labPolicy = policy(lab, new FakeResolver());

    // Authority/canonicalization accepts bracketed IPv6; only the network classifier blocks it.
    assertDoesNotThrow(() -> labPolicy.validate("https://[::1]/hook"));
    assertDoesNotThrow(() -> labPolicy.validate("https://[::10.0.0.1]/hook"));
    assertDoesNotThrow(() -> labPolicy.validate("https://[64:ff9b::10.0.0.1]/hook"));

    WebhookDestinationPolicy strict = policyWithResolver(new FakeResolver());
    assertTrue(WebhookDestinationPolicy.isProhibitedAddress(ipv6("::1")));
    assertTrue(WebhookDestinationPolicy.isProhibitedAddress(ipv6("::10.0.0.1")));
    assertTrue(WebhookDestinationPolicy.isProhibitedAddress(ipv6("64:ff9b::10.0.0.1")));
    assertThrows(WebhookDestinationRejectedException.class,
        () -> strict.validate("https://[::1]/hook"));
    assertThrows(WebhookDestinationRejectedException.class,
        () -> strict.validate("https://[::10.0.0.1]/hook"));
    assertThrows(WebhookDestinationRejectedException.class,
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
    WebhookDestinationPolicy policy = policyWithResolver(resolver);

    assertThrows(WebhookDestinationRejectedException.class,
        () -> policy.validate("https://compat.example.com/hook"));
    assertThrows(WebhookDestinationRejectedException.class,
        () -> policy.validate("https://nat64.example.com/hook"));
    assertThrows(WebhookDestinationRejectedException.class,
        () -> policy.validate("https://nat64-meta.example.com/hook"));
    assertThrows(WebhookDestinationRejectedException.class,
        () -> policy.validate("https://nat64-local.example.com/hook"));
    assertThrows(WebhookDestinationRejectedException.class,
        () -> policy.validate("https://sixto4.example.com/hook"));
    assertThrows(WebhookDestinationRejectedException.class,
        () -> policy.validate("https://teredo.example.com/hook"));
    assertDoesNotThrow(() -> policy.validate("https://ok.example.com/hook"));
  }

  @Test
  void rejectsMixedPublicAndPrivateDnsAnswers() {
    FakeResolver resolver = new FakeResolver()
        .put("evil.example.com", ipv4("93.184.216.34"), ipv4("10.0.0.1"));
    WebhookDestinationPolicy policy = policyWithResolver(resolver);

    assertThrows(WebhookDestinationRejectedException.class,
        () -> policy.validate("https://evil.example.com/hook"));
  }

  @Test
  void rejectsDnsFailure() {
    FakeResolver resolver = new FakeResolver().fail("missing.example.com");
    WebhookDestinationPolicy policy = policyWithResolver(resolver);

    assertThrows(WebhookDestinationRejectedException.class,
        () -> policy.validate("https://missing.example.com/hook"));
  }

  @Test
  void rejectsEmptyDnsAnswers() {
    WebhookHostResolver empty = host -> List.of();
    WebhookDestinationPolicy policy = policyWithResolver(empty);

    assertThrows(WebhookDestinationRejectedException.class,
        () -> policy.validate("https://empty.example.com/hook"));
  }

  @Test
  void normalizesHostCaseAndTrailingDot() {
    FakeResolver resolver = new FakeResolver().put("hooks.example.com", ipv4("93.184.216.34"));
    WebhookDestinationPolicy policy = policyWithResolver(resolver);

    assertDoesNotThrow(() -> policy.validate("https://Hooks.EXAMPLE.com./hooks"));
    assertEquals(1, resolver.resolveCount());
  }

  @Test
  void normalizeHost_convertsIdnToAscii() {
    assertEquals("xn--bcher-kva.example",
        WebhookDestinationPolicy.normalizeHost("Bücher.example"));
  }

  @Test
  void normalizeHost_stripsSingleIpv6BracketLayer() {
    assertEquals("2001:4860:4860::8888",
        WebhookDestinationPolicy.normalizeHost("[2001:4860:4860::8888]"));
  }

  @Test
  void allowlistExactHostRequiredWhenConfigured() {
    WebhookDestinationProperties properties = defaultProperties();
    properties.setAllowedHosts(List.of("hooks.example.com"));
    FakeResolver resolver = new FakeResolver()
        .put("hooks.example.com", ipv4("93.184.216.34"))
        .put("other.example.com", ipv4("93.184.216.34"));
    WebhookDestinationPolicy policy = policy(properties, resolver);

    assertDoesNotThrow(() -> policy.validate("https://hooks.example.com/a"));
    assertThrows(WebhookDestinationRejectedException.class,
        () -> policy.validate("https://other.example.com/a"));
  }

  @Test
  void allowlistMatchesNormalizedIdnCaseAndTrailingDot() {
    WebhookDestinationProperties properties = defaultProperties();
    properties.setAllowedHosts(List.of("XN--BCHER-KVA.EXAMPLE."));
    FakeResolver resolver = new FakeResolver()
        .put("xn--bcher-kva.example", ipv4("93.184.216.34"));
    WebhookDestinationPolicy policy = policy(properties, resolver);

    assertDoesNotThrow(() -> policy.validate("https://xn--bcher-kva.example/hook"));
    assertEquals(
        "xn--bcher-kva.example",
        WebhookDestinationPolicy.normalizeHost("Bücher.EXAMPLE."));
  }

  @Test
  void allowlistMatchesIpv6LiteralEntriesWithOptionalBrackets() {
    WebhookDestinationProperties properties = defaultProperties();
    properties.setAllowedHosts(List.of("[2001:4860:4860::8888]"));
    WebhookDestinationPolicy policy = policy(properties, new FakeResolver());

    assertDoesNotThrow(() -> policy.validate("https://[2001:4860:4860::8888]/hook"));
    assertDoesNotThrow(() -> policy.validate("https://[2001:4860:4860::8888]:443/hook"));

    properties.setAllowedHosts(List.of("2001:4860:4860::8888"));
    WebhookDestinationPolicy unbracketedAllow = policy(properties, new FakeResolver());
    assertDoesNotThrow(() -> unbracketedAllow.validate("https://[2001:4860:4860::8888]/hook"));

    assertThrows(WebhookDestinationRejectedException.class,
        () -> unbracketedAllow.validate("https://[2001:4860:4860::8844]/hook"));
  }

  @Test
  void allowlistWildcardSuffixMatchesUnambiguously() {
    WebhookDestinationProperties properties = defaultProperties();
    properties.setAllowedHosts(List.of("*.example.com"));
    FakeResolver resolver = new FakeResolver()
        .put("a.example.com", ipv4("93.184.216.34"))
        .put("example.com", ipv4("93.184.216.34"))
        .put("a.example.com.evil.com", ipv4("93.184.216.34"));
    WebhookDestinationPolicy policy = policy(properties, resolver);

    assertDoesNotThrow(() -> policy.validate("https://a.example.com/hook"));
    assertThrows(WebhookDestinationRejectedException.class,
        () -> policy.validate("https://example.com/hook"));
    assertThrows(WebhookDestinationRejectedException.class,
        () -> policy.validate("https://a.example.com.evil.com/hook"));
  }

  @Test
  void allowlistIgnoresUserRegexStylePatterns() {
    WebhookDestinationProperties properties = defaultProperties();
    properties.setAllowedHosts(List.of(".*\\.example\\.com"));
    FakeResolver resolver = new FakeResolver().put("hooks.example.com", ipv4("93.184.216.34"));
    WebhookDestinationPolicy policy = policy(properties, resolver);

    assertThrows(WebhookDestinationRejectedException.class,
        () -> policy.validate("https://hooks.example.com/hook"));
  }

  @Test
  void allowlistMalformedEntriesFailClosedPerRequest() {
    WebhookDestinationProperties properties = defaultProperties();
    properties.setAllowedHosts(List.of("0x7f000001", "[bad", "hooks.example.com"));
    FakeResolver resolver = new FakeResolver().put("hooks.example.com", ipv4("93.184.216.34"));
    WebhookDestinationPolicy policy = policy(properties, resolver);

    assertDoesNotThrow(() -> policy.validate("https://hooks.example.com/hook"));
    properties.setAllowedHosts(List.of("0x7f000001", "[bad"));
    WebhookDestinationPolicy onlyBad = policy(properties, resolver);
    assertThrows(WebhookDestinationRejectedException.class,
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
    WebhookDestinationPolicy policy = policyWithResolver(resolver);
    assertThrows(WebhookDestinationRejectedException.class, () -> policy.validate(url));
  }

  @Test
  void allowHttpEscapeHatchIsExplicit() {
    WebhookDestinationProperties properties = defaultProperties();
    properties.setAllowHttp(true);
    FakeResolver resolver = new FakeResolver().put("hooks.example.com", ipv4("93.184.216.34"));
    WebhookDestinationPolicy policy = policy(properties, resolver);

    assertDoesNotThrow(() -> policy.validate("http://hooks.example.com/hook"));
  }

  @Test
  void allowPrivateNetworksEscapeHatchIsExplicit() {
    WebhookDestinationProperties properties = defaultProperties();
    properties.setAllowPrivateNetworks(true);
    WebhookDestinationPolicy policy = policy(properties, new FakeResolver());

    assertDoesNotThrow(() -> policy.validate("https://127.0.0.1/hook"));
    assertDoesNotThrow(() -> policy.validate("https://10.0.0.1/hook"));
  }

  @Test
  void defaultsRemainFailClosed() {
    WebhookDestinationProperties properties = defaultProperties();
    assertFalse(properties.isAllowHttp());
    assertFalse(properties.isAllowPrivateNetworks());
    assertTrue(properties.getAllowedHosts().isEmpty());
  }

  @Test
  void publicIpv4LiteralAllowed() {
    WebhookDestinationPolicy policy = policyWithResolver(new FakeResolver());
    assertDoesNotThrow(() -> policy.validate("https://93.184.216.34/hook"));
  }

  @Test
  void hostnameResolvingOnlyToPublicIpv6Allowed() {
    FakeResolver resolver = new FakeResolver()
        .put("ipv6.example.com", ipv6("2001:4860:4860::8888"));
    WebhookDestinationPolicy policy = policyWithResolver(resolver);
    assertDoesNotThrow(() -> policy.validate("https://ipv6.example.com/hook"));
  }

  @Test
  void rejectsHostnameResolvingToLinkLocal() {
    FakeResolver resolver = new FakeResolver()
        .put("meta.example.com", ipv4("169.254.169.254"));
    WebhookDestinationPolicy policy = policyWithResolver(resolver);
    assertThrows(WebhookDestinationRejectedException.class,
        () -> policy.validate("https://meta.example.com/"));
  }

  @Test
  void prohibitedAddressClassifierCoversSiteLocal() {
    assertTrue(WebhookDestinationPolicy.isProhibitedAddress(ipv4("192.168.0.1")));
    assertTrue(WebhookDestinationPolicy.isProhibitedAddress(ipv6("::1")));
  }

  @Test
  void ordinaryGlobalUnicastIpv6RemainsAllowed() {
    assertFalse(WebhookDestinationPolicy.isProhibitedAddress(ipv6("2001:4860:4860::8888")));
    assertFalse(WebhookDestinationPolicy.isProhibitedAddress(
        ipv6("2606:2800:220:1:248:1893:25c8:1946")));
  }
}
