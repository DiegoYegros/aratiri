package com.aratiri.webhooks.application.destination;

import com.aratiri.infrastructure.configuration.WebhookDestinationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;

/**
 * Fail-closed webhook destination policy enforced at admin CRUD and immediately before send.
 *
 * <p><b>DNS rebinding residual:</b> {@link java.net.http.HttpClient} re-resolves the hostname when
 * connecting and does not offer a portable way to pin the validated addresses while preserving TLS
 * hostname verification / SNI. Send-time validation shrinks the TOCTOU window but does not eliminate
 * rebinding. A non-empty {@code aratiri.webhooks.destination.allowed-hosts} list narrows which names
 * may be chosen; it does <em>not</em> pin DNS and does <em>not</em> prevent an allowlisted or
 * compromised name from rebinding to a different address between validation and connect.
 */
@Component
public class WebhookDestinationPolicy {

  /** mask, network — checked as {@code (address & mask) == network}. */
  private static final int[][] PROHIBITED_IPV4_RANGES = {
      {0xFF000000, 0x00000000}, // 0.0.0.0/8
      {0xFF000000, 0x0A000000}, // 10.0.0.0/8
      {0xFFC00000, 0x64400000}, // 100.64.0.0/10 CGNAT
      {0xFF000000, 0x7F000000}, // 127.0.0.0/8
      {0xFFFF0000, 0xA9FE0000}, // 169.254.0.0/16 link-local / cloud metadata
      {0xFFF00000, 0xAC100000}, // 172.16.0.0/12
      {0xFFFFFF00, 0xC0000000}, // 192.0.0.0/24 IETF
      {0xFFFFFF00, 0xC0000200}, // 192.0.2.0/24 TEST-NET-1
      {0xFFFF0000, 0xC0A80000}, // 192.168.0.0/16
      {0xFFFE0000, 0xC6120000}, // 198.18.0.0/15 benchmarking
      {0xFFFFFF00, 0xC6336400}, // 198.51.100.0/24 TEST-NET-2
      {0xFFFFFF00, 0xCB007100}, // 203.0.113.0/24 TEST-NET-3
      {0xF0000000, 0xE0000000}, // 224.0.0.0/4 multicast
      {0xF0000000, 0xF0000000}, // 240.0.0.0/4 reserved
  };

  private final WebhookDestinationProperties properties;
  private final WebhookHostResolver hostResolver;

  public WebhookDestinationPolicy(
      WebhookDestinationProperties properties,
      WebhookHostResolver hostResolver) {
    this.properties = properties;
    this.hostResolver = hostResolver;
  }

  public void validate(String rawUrl) {
    URI uri = parseAbsoluteUri(rawUrl);
    assertAllowedScheme(uri.getScheme());
    String canonicalHost = extractUnbracketedHost(uri);
    assertSafeAuthority(uri, canonicalHost);
    String normalizedHost = normalizeOrReject(canonicalHost);
    assertAllowlisted(normalizedHost);
    assertResolvedAddressesAllowed(resolveAddresses(normalizedHost));
  }

  private static URI parseAbsoluteUri(String rawUrl) {
    if (!StringUtils.hasText(rawUrl)) {
      throw new WebhookDestinationRejectedException();
    }
    String trimmed = rawUrl.trim();
    if (!trimmed.equals(rawUrl) || containsDisallowedCharacters(trimmed)) {
      throw new WebhookDestinationRejectedException();
    }
    try {
      URI uri = new URI(trimmed);
      if (!uri.isAbsolute()) {
        throw new WebhookDestinationRejectedException();
      }
      return uri;
    } catch (URISyntaxException e) {
      throw new WebhookDestinationRejectedException(e);
    }
  }

  private void assertAllowedScheme(String scheme) {
    if (scheme == null) {
      throw new WebhookDestinationRejectedException();
    }
    String normalized = scheme.toLowerCase(Locale.ROOT);
    if ("https".equals(normalized)) {
      return;
    }
    if ("http".equals(normalized) && properties.isAllowHttp()) {
      return;
    }
    throw new WebhookDestinationRejectedException();
  }

  /**
   * Returns the URI host with at most one layer of IPv6 brackets removed. On JDK 25+,
   * {@link URI#getHost()} already returns bracketed IPv6 literals.
   */
  private static String extractUnbracketedHost(URI uri) {
    String host = uri.getHost();
    if (!StringUtils.hasText(host)) {
      throw new WebhookDestinationRejectedException();
    }
    try {
      return unbracketHost(host);
    } catch (IllegalArgumentException e) {
      throw new WebhookDestinationRejectedException(e);
    }
  }

  private static void assertSafeAuthority(URI uri, String unbracketedHost) {
    if (uri.getRawUserInfo() != null || uri.getUserInfo() != null) {
      throw new WebhookDestinationRejectedException();
    }
    if (uri.getRawFragment() != null || uri.getFragment() != null) {
      throw new WebhookDestinationRejectedException();
    }
    if (unbracketedHost.indexOf('%') >= 0) {
      throw new WebhookDestinationRejectedException();
    }
    if (uri.getAuthority() != null && !authorityMatchesHostAndPort(uri, unbracketedHost)) {
      throw new WebhookDestinationRejectedException();
    }
    int port = uri.getPort();
    if (port != -1 && (port < 1 || port > 65535)) {
      throw new WebhookDestinationRejectedException();
    }
  }

  private String normalizeOrReject(String host) {
    try {
      return normalizeHost(host);
    } catch (IllegalArgumentException e) {
      throw new WebhookDestinationRejectedException(e);
    }
  }

  private void assertAllowlisted(String normalizedHost) {
    if (!isAllowedByHostAllowlist(normalizedHost)) {
      throw new WebhookDestinationRejectedException();
    }
  }

  private void assertResolvedAddressesAllowed(List<InetAddress> addresses) {
    if (addresses == null || addresses.isEmpty()) {
      throw new WebhookDestinationRejectedException();
    }
    if (properties.isAllowPrivateNetworks()) {
      return;
    }
    for (InetAddress address : addresses) {
      if (isProhibitedAddress(address)) {
        throw new WebhookDestinationRejectedException();
      }
    }
  }

  private static boolean containsDisallowedCharacters(String value) {
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (Character.isISOControl(c) || Character.isWhitespace(c) || c == '\\') {
        return true;
      }
    }
    return false;
  }

  private static boolean authorityMatchesHostAndPort(URI uri, String unbracketedHost) {
    String authority = uri.getAuthority();
    if (unbracketedHost == null || authority == null) {
      return false;
    }
    String expected = formatAuthorityHost(unbracketedHost);
    int port = uri.getPort();
    if (port != -1) {
      expected = expected + ":" + port;
    }
    return expected.equalsIgnoreCase(authority);
  }

  private static String formatAuthorityHost(String unbracketedHost) {
    if (unbracketedHost.contains(":")) {
      return "[" + unbracketedHost + "]";
    }
    return unbracketedHost;
  }

  /**
   * Normalizes a host for policy checks. Accepts optional single-layer IPv6 brackets (URI or
   * allowlist entry form). Rejects zone/scope ids, alternative IP literals, and ambiguous brackets.
   */
  static String normalizeHost(String host) {
    String unbracketed = unbracketHost(host);
    String stripped = stripTrailingDots(unbracketed);
    if (!StringUtils.hasText(stripped)) {
      throw new IllegalArgumentException("empty host");
    }
    if (stripped.indexOf('%') >= 0 || isAlternativeIpLiteral(stripped)) {
      throw new IllegalArgumentException("rejected host form");
    }
    if (isDottedDecimalIpv4(stripped)) {
      parseStrictIpv4(stripped);
      return stripped.toLowerCase(Locale.ROOT);
    }
    if (stripped.contains(":")) {
      requireValidIpv6Literal(stripped);
      return stripped.toLowerCase(Locale.ROOT);
    }
    String ascii = IDN.toASCII(stripped, IDN.USE_STD3_ASCII_RULES);
    if (!StringUtils.hasText(ascii)) {
      throw new IllegalArgumentException("empty idn");
    }
    return ascii.toLowerCase(Locale.ROOT);
  }

  static String unbracketHost(String host) {
    String result = host;
    if (result.startsWith("[") && result.endsWith("]") && result.length() >= 2) {
      result = result.substring(1, result.length() - 1);
    }
    if (result.indexOf('[') >= 0 || result.indexOf(']') >= 0) {
      throw new IllegalArgumentException("ambiguous brackets");
    }
    return result;
  }

  private static String stripTrailingDots(String host) {
    String stripped = host;
    while (stripped.endsWith(".")) {
      stripped = stripped.substring(0, stripped.length() - 1);
    }
    return stripped;
  }

  private static void requireValidIpv6Literal(String host) {
    try {
      InetAddress.getByName(host);
    } catch (UnknownHostException e) {
      throw new IllegalArgumentException("invalid ipv6", e);
    }
  }

  private boolean isAllowedByHostAllowlist(String normalizedHost) {
    List<String> allowedHosts = properties.getAllowedHosts();
    if (allowedHosts == null || allowedHosts.isEmpty()) {
      return true;
    }
    return allowedHosts.stream().anyMatch(entry -> matchesAllowlistEntry(normalizedHost, entry));
  }

  /**
   * Malformed allowlist entries fail closed for that entry (ignored / non-matching) rather than
   * aborting process startup; a destination is allowed only if some valid entry matches.
   */
  private static boolean matchesAllowlistEntry(String normalizedHost, String entry) {
    if (!StringUtils.hasText(entry)) {
      return false;
    }
    String pattern = entry.trim().toLowerCase(Locale.ROOT);
    if (pattern.startsWith("*.")) {
      String suffix = pattern.substring(1);
      return suffix.length() >= 2
          && normalizedHost.endsWith(suffix)
          && normalizedHost.length() > suffix.length();
    }
    if (pattern.contains("*")) {
      return false;
    }
    try {
      return normalizedHost.equals(normalizeHost(pattern));
    } catch (IllegalArgumentException _) {
      return false;
    }
  }

  private List<InetAddress> resolveAddresses(String normalizedHost) {
    if (isDottedDecimalIpv4(normalizedHost) || normalizedHost.contains(":")) {
      return List.of(literalAddress(normalizedHost));
    }
    try {
      List<InetAddress> resolved = hostResolver.resolveAll(normalizedHost);
      if (resolved == null || resolved.isEmpty()) {
        throw new WebhookDestinationRejectedException();
      }
      return resolved;
    } catch (UnknownHostException e) {
      throw new WebhookDestinationRejectedException(e);
    }
  }

  private static InetAddress literalAddress(String normalizedHost) {
    try {
      return InetAddress.getByName(normalizedHost);
    } catch (UnknownHostException e) {
      throw new WebhookDestinationRejectedException(e);
    }
  }

  static boolean isAlternativeIpLiteral(String host) {
    if (isAllDigits(host) || isHexIpv4Literal(host)) {
      return true;
    }
    if (isDottedDecimalIpv4(host)) {
      return hasIpv4LeadingZeros(host);
    }
    return looksLikeNumericDottedLiteral(host) && !isStrictIpv4(host);
  }

  private static boolean isAllDigits(String host) {
    if (host.isEmpty()) {
      return false;
    }
    for (int i = 0; i < host.length(); i++) {
      if (!Character.isDigit(host.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  private static boolean isHexIpv4Literal(String host) {
    if (host.length() < 3 || !(host.startsWith("0x") || host.startsWith("0X"))) {
      return false;
    }
    for (int i = 2; i < host.length(); i++) {
      if (Character.digit(host.charAt(i), 16) < 0) {
        return false;
      }
    }
    return true;
  }

  private static boolean looksLikeNumericDottedLiteral(String host) {
    boolean sawDot = false;
    for (int i = 0; i < host.length(); i++) {
      char c = host.charAt(i);
      if (c == '.') {
        sawDot = true;
      } else if (c != 'x' && c != 'X' && !Character.isDigit(c)) {
        return false;
      }
    }
    return sawDot;
  }

  private static boolean isDottedDecimalIpv4(String host) {
    int dots = 0;
    int octetDigits = 0;
    for (int i = 0; i < host.length(); i++) {
      char c = host.charAt(i);
      if (c == '.') {
        if (octetDigits == 0 || octetDigits > 3) {
          return false;
        }
        dots++;
        octetDigits = 0;
      } else if (!Character.isDigit(c)) {
        return false;
      } else {
        octetDigits++;
      }
    }
    return dots == 3 && octetDigits >= 1 && octetDigits <= 3;
  }

  private static boolean isStrictIpv4(String host) {
    try {
      parseStrictIpv4(host);
      return true;
    } catch (IllegalArgumentException _) {
      return false;
    }
  }

  private static boolean hasIpv4LeadingZeros(String host) {
    String[] parts = host.split("\\.", -1);
    if (parts.length != 4) {
      return true;
    }
    for (String part : parts) {
      if (part.length() > 1 && part.startsWith("0")) {
        return true;
      }
    }
    return false;
  }

  private static byte[] parseStrictIpv4(String host) {
    String[] parts = host.split("\\.", -1);
    if (parts.length != 4) {
      throw new IllegalArgumentException("ipv4 parts");
    }
    byte[] bytes = new byte[4];
    for (int i = 0; i < 4; i++) {
      bytes[i] = (byte) parseIpv4Octet(parts[i]);
    }
    return bytes;
  }

  private static int parseIpv4Octet(String part) {
    if (part.isEmpty() || (part.length() > 1 && part.startsWith("0"))) {
      throw new IllegalArgumentException("ipv4 leading zero");
    }
    int value;
    try {
      value = Integer.parseInt(part, 10);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("ipv4 octet", e);
    }
    if (value < 0 || value > 255) {
      throw new IllegalArgumentException("ipv4 range");
    }
    return value;
  }

  static boolean isProhibitedAddress(InetAddress address) {
    if (isProhibitedByInetFlags(address)) {
      return true;
    }
    byte[] raw = address.getAddress();
    if (raw.length == 4) {
      return isProhibitedIpv4(raw);
    }
    if (raw.length == 16) {
      return isProhibitedIpv6(raw);
    }
    return true;
  }

  private static boolean isProhibitedIpv4(byte[] raw) {
    int address = ipv4ToInt(raw);
    for (int[] range : PROHIBITED_IPV4_RANGES) {
      if ((address & range[0]) == range[1]) {
        return true;
      }
    }
    return false;
  }

  private static boolean isProhibitedIpv6(byte[] raw) {
    if (isIpv4Mapped(raw)) {
      return isEmbeddedIpv4Prohibited(raw, 12);
    }
    if (isAllZero(raw) || isIpv6Loopback(raw)) {
      return true;
    }
    // IPv4-compatible ::/96 (excluding :: and ::1 handled above): classify embedded IPv4.
    if (isIpv4CompatiblePrefix(raw)) {
      return isEmbeddedIpv4Prohibited(raw, 12);
    }
    // Well-known NAT64 64:ff9b::/96 and local-use NAT64 64:ff9b:1::/48 — reject 64:ff9b::/32.
    if (isNat64Prefix(raw)) {
      return true;
    }
    // 6to4 2002::/16 — reject transition prefix wholesale.
    if (isSixToFourPrefix(raw)) {
      return true;
    }
    // Teredo 2001:0000::/32 — reject transition prefix wholesale.
    if (isTeredoPrefix(raw)) {
      return true;
    }
    int b0 = raw[0] & 0xff;
    int b1 = raw[1] & 0xff;
    if ((b0 & 0xFE) == 0xFC) {
      return true; // ULA fc00::/7
    }
    if (b0 == 0xFE && (b1 & 0xC0) == 0x80) {
      return true; // link-local fe80::/10
    }
    if (b0 == 0xFF) {
      return true; // multicast
    }
    if (b0 == 0x20 && b1 == 0x01 && (raw[2] & 0xff) == 0x0d && (raw[3] & 0xff) == 0xb8) {
      return true; // documentation 2001:db8::/32
    }
    return b0 == 0xFE && (b1 & 0xC0) == 0xC0; // deprecated site-local fec0::/10
  }

  private static boolean isEmbeddedIpv4Prohibited(byte[] raw, int offset) {
    byte[] ipv4 = new byte[]{raw[offset], raw[offset + 1], raw[offset + 2], raw[offset + 3]};
    return isProhibitedIpv4(ipv4) || isProhibitedByInetFlags(inet4(ipv4));
  }

  private static boolean isAllZero(byte[] raw) {
    for (byte b : raw) {
      if (b != 0) {
        return false;
      }
    }
    return true;
  }

  private static boolean isIpv6Loopback(byte[] raw) {
    for (int i = 0; i < 15; i++) {
      if (raw[i] != 0) {
        return false;
      }
    }
    return raw[15] == 1;
  }

  private static boolean isIpv4Mapped(byte[] raw) {
    for (int i = 0; i < 10; i++) {
      if (raw[i] != 0) {
        return false;
      }
    }
    return raw[10] == (byte) 0xFF && raw[11] == (byte) 0xFF;
  }

  /** First 96 bits zero (IPv4-compatible), after :: / ::1 already excluded by callers. */
  private static boolean isIpv4CompatiblePrefix(byte[] raw) {
    for (int i = 0; i < 12; i++) {
      if (raw[i] != 0) {
        return false;
      }
    }
    return true;
  }

  /** NAT64 well-known {@code 64:ff9b::/96} and local-use {@code 64:ff9b:1::/48} under {@code 64:ff9b::/32}. */
  private static boolean isNat64Prefix(byte[] raw) {
    return (raw[0] & 0xff) == 0x00
        && (raw[1] & 0xff) == 0x64
        && (raw[2] & 0xff) == 0xff
        && (raw[3] & 0xff) == 0x9b;
  }

  private static boolean isSixToFourPrefix(byte[] raw) {
    return (raw[0] & 0xff) == 0x20 && (raw[1] & 0xff) == 0x02;
  }

  private static boolean isTeredoPrefix(byte[] raw) {
    return (raw[0] & 0xff) == 0x20
        && (raw[1] & 0xff) == 0x01
        && raw[2] == 0
        && raw[3] == 0;
  }

  private static boolean isProhibitedByInetFlags(InetAddress address) {
    return address.isAnyLocalAddress()
        || address.isLoopbackAddress()
        || address.isLinkLocalAddress()
        || address.isSiteLocalAddress()
        || address.isMulticastAddress();
  }

  private static InetAddress inet4(byte[] ipv4) {
    try {
      return InetAddress.getByAddress(ipv4);
    } catch (UnknownHostException e) {
      throw new IllegalStateException(e);
    }
  }

  private static int ipv4ToInt(byte[] raw) {
    return ((raw[0] & 0xff) << 24)
        | ((raw[1] & 0xff) << 16)
        | ((raw[2] & 0xff) << 8)
        | (raw[3] & 0xff);
  }
}
