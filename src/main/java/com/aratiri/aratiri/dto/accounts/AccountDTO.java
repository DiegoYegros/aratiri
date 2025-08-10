package com.aratiri.aratiri.dto.accounts;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;

@Data
@RequiredArgsConstructor
@AllArgsConstructor
@Builder
public class AccountDTO {
    private String id;
    @JsonProperty(value = "user_id")
    private String userId;
    private long balance;
    @JsonProperty("bitcoin_address")
    private String bitcoinAddress;
    private String alias;
    private String lnurl;
    @JsonProperty("qr_code")
    private String qrCode;
    @JsonProperty("fiat_equivalents")
    private Map<String, BigDecimal> fiatEquivalents;
}