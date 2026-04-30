package com.aratiri.accounts.application.dto;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AccountsDTOsTest {

    @Test
    void accountDTO_builder() {
        AccountDTO dto = AccountDTO.builder()
                .id("acc-1")
                .userId("user-1")
                .balance(100000L)
                .bitcoinAddress("bc1qabc")
                .alias("test@aratiri.example.com")
                .lnurl("LNURL1...")
                .lnurlQrCode("base64lnurl")
                .bitcoinAddressQrCode("base64btc")
                .fiatEquivalents(Map.of("USD", BigDecimal.valueOf(600)))
                .build();

        assertEquals("acc-1", dto.getId());
        assertEquals("user-1", dto.getUserId());
        assertEquals(100000L, dto.getBalance());
        assertEquals("bc1qabc", dto.getBitcoinAddress());
        assertEquals("test@aratiri.example.com", dto.getAlias());
        assertEquals("LNURL1...", dto.getLnurl());
        assertEquals("base64lnurl", dto.getLnurlQrCode());
        assertEquals("base64btc", dto.getBitcoinAddressQrCode());
        assertEquals(BigDecimal.valueOf(600), dto.getFiatEquivalents().get("USD"));
    }

    @Test
    void accountTransactionDTO_builder() {
        AccountTransactionDTO dto = AccountTransactionDTO.builder()
                .id("tx-1")
                .date(java.time.OffsetDateTime.now())
                .amount(50000L)
                .type(AccountTransactionType.CREDIT)
                .status(AccountTransactionStatus.COMPLETED)
                .fiatEquivalents(Map.of("USD", BigDecimal.valueOf(300)))
                .build();

        assertEquals("tx-1", dto.getId());
        assertEquals(50000L, dto.getAmount());
        assertEquals(AccountTransactionType.CREDIT, dto.getType());
        assertEquals(AccountTransactionStatus.COMPLETED, dto.getStatus());
        assertEquals(BigDecimal.valueOf(300), dto.getFiatEquivalents().get("USD"));
    }

    @Test
    void accountTransactionType_enumValues() {
        assertEquals(2, AccountTransactionType.values().length);
        assertEquals(AccountTransactionType.CREDIT, AccountTransactionType.valueOf("CREDIT"));
        assertEquals(AccountTransactionType.DEBIT, AccountTransactionType.valueOf("DEBIT"));
    }

    @Test
    void accountTransactionStatus_enumValues() {
        assertEquals(3, AccountTransactionStatus.values().length);
        assertEquals(AccountTransactionStatus.PENDING, AccountTransactionStatus.valueOf("PENDING"));
        assertEquals(AccountTransactionStatus.COMPLETED, AccountTransactionStatus.valueOf("COMPLETED"));
    }

    @Test
    void createAccountRequestDTO_allFields() {
        CreateAccountRequestDTO dto = new CreateAccountRequestDTO();
        dto.setUserId("user-1");
        dto.setAlias("myalias");

        assertEquals("user-1", dto.getUserId());
        assertEquals("myalias", dto.getAlias());
    }

    @Test
    void accountTransactionsDTOResponse_builder() {
        AccountTransactionsDTOResponse response = AccountTransactionsDTOResponse.builder()
                .transactions(java.util.List.of())
                .build();

        assertNotNull(response.getTransactions());
        assertEquals(0, response.getTransactions().size());
    }
}
