package com.aratiri.dto.admin;

import lombok.Data;

@Data
public class ConnectPeerRequestDTO {
    private String pubkey;
    private String host;
}