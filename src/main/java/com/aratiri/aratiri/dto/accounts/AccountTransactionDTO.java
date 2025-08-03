package com.aratiri.aratiri.dto.accounts;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

@Data
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountTransactionDTO {
    private String id;
    private AccountTransactionType type;
    private AccountTransactionStatus status;
    private long amount;
    private OffsetDateTime date;
    @JsonProperty("fiat_equivalents")
    private Map<String, BigDecimal> fiatEquivalents;
}