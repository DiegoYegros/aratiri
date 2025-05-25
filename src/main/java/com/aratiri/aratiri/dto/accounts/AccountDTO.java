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

    private String bitcoinAddress;

    private long balance;
    @JsonProperty(value = "user_id")
    private String userId;
}