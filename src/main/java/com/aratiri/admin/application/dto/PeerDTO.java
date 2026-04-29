package com.aratiri.admin.application.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PeerDTO {
    private String pubKey;
    private String address;
}
