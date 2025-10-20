package com.aratiri.admin.application.dto;

import lombok.Data;

@Data
public class ConnectPeerRequestDTO {
    private String pubkey;
    private String host;
}