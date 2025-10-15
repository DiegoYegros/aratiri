package com.aratiri.dto.admin;

import lnrpc.Peer;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PeerDTO {
    private String pubKey;
    private String address;

    public static PeerDTO fromGrpc(Peer peer) {
        return PeerDTO.builder()
                .pubKey(peer.getPubKey())
                .address(peer.getAddress())
                .build();
    }
}