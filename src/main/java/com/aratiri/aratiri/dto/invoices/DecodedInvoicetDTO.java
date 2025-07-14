package com.aratiri.aratiri.dto.invoices;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DecodedInvoicetDTO {
    private String destination;
    @JsonProperty("payment_hash")
    private String paymentHash;
    @JsonProperty("num_satoshis")
    private long numSatoshis;
    private String timestamp;
    private long expiry;
    private String description;
    @JsonProperty("description_hash")
    private String descriptionHash;
    @JsonProperty("fallback_addr")
    private String fallbackAddr;
    @JsonProperty("cltv_expiry")
    private long cltvExpiry;
    @JsonProperty("route_hints")
    private List<Object> routeHints;
    @JsonProperty("payment_addr")
    private String paymentAddr;
    @JsonProperty("num_msat")
    private long numMsat;
    @JsonProperty("blindedPaths")
    private List<Object> blindedPaths;

    @Data
    public static class Feature {
        private String name;
        @JsonProperty("is_required")
        private boolean isRequired;
        @JsonProperty("is_known")
        private boolean isKnown;
    }
}