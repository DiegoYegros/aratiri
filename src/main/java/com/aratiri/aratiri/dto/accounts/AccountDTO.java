package com.aratiri.aratiri.dto.accounts;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
@AllArgsConstructor
public class AccountDTO {
    private String id;
    @JsonProperty(value = "user_id")
    private String userId;
    private long balance;
    private String bitcoinAddress;
    private String alias;
    private String lnurl;
}