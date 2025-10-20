package com.aratiri.accounts.application.dto;

import lombok.Builder;
import lombok.Data;
import lombok.ToString;

import java.util.List;

@Data
@ToString
@Builder
public class AccountTransactionsDTOResponse {
    private List<AccountTransactionDTO> transactions;
}
