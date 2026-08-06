package com.aratiri.infrastructure.http.destination;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Destination policy for outbound user-influenced HTTP (webhooks, LNURL metadata/callbacks, NIP-05).
 *
 * <p>Defaults are fail-closed for production: HTTPS only, no private/special networks.
 * Escape hatches are explicit, default {@code false}, and intended for isolated lab use only.
 */
@Setter
@Getter
@ConfigurationProperties(prefix = "aratiri.outbound.destination")
public class OutboundDestinationProperties {

  /**
   * When true, allows {@code http://} destinations. Unsafe / lab-only. Default false.
   */
  private boolean allowHttp = false;

  /**
   * When true, skips private/special address rejection (loopback, RFC1918, link-local, etc.).
   * Unsafe / lab-only. Default false. Does not disable URI structure or scheme checks.
   */
  private boolean allowPrivateNetworks = false;

  /**
   * Optional allowlist of exact hosts or {@code *.suffix} wildcard suffixes.
   * When non-empty, destinations whose normalized host is outside the list are rejected.
   * Entries are not regex; user-supplied regex is not supported.
   *
   * <p>Hosts are compared after the same normalization as destinations (IDN→punycode, case,
   * trailing dots, optional single-layer IPv6 brackets). Malformed entries are ignored per
   * request (fail closed for matching) rather than failing process startup.
   *
   * <p>Empty list (default) allows any host that passes scheme/structure/private-network checks —
   * required for Lightning Address / LNURL / NIP-05 against the open Lightning ecosystem.
   * A non-empty list is mainly for locked-down webhook labs; applying it globally would reject
   * arbitrary public LNURL/NIP-05 hosts.
   *
   * <p>An allowlist narrows which names may be chosen; it does not pin DNS or prevent rebinding
   * of an allowlisted name.
   */
  private List<String> allowedHosts = new ArrayList<>();
}
