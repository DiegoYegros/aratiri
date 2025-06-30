package com.aratiri.aratiri.dto.accounts;

import com.aratiri.aratiri.enums.AccountTransactionType;
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
    private long amount;
    private OffsetDateTime date;
}