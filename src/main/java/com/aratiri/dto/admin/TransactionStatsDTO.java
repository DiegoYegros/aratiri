package com.aratiri.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@AllArgsConstructor
public class TransactionStatsDTO {
    private LocalDate date;
    private String type;
    private BigDecimal totalAmount;
    private long count;
}