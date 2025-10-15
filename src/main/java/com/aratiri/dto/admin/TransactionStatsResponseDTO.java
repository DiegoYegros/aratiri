package com.aratiri.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class TransactionStatsResponseDTO {
    private List<TransactionStatsDTO> stats;
}