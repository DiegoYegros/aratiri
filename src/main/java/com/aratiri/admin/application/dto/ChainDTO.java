package com.aratiri.admin.application.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChainDTO {
    private String chain;
    private String network;
}