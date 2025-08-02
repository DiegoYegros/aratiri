package com.aratiri.aratiri.dto.accounts;

import lombok.*;

import java.time.OffsetDateTime;

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
}