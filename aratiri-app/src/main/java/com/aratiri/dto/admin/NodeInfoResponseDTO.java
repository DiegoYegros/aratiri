package com.aratiri.dto.admin;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class NodeInfoResponseDTO {
    private String version;
    private String commitHash;
    private String identityPubkey;
    private String alias;
    private String color;
    private int numPendingChannels;
    private int numActiveChannels;
    private int numInactiveChannels;
    private int numPeers;
    private int blockHeight;
    private String blockHash;
    private boolean syncedToChain;
    private boolean syncedToGraph;
    private List<ChainDTO> chains;
    private List<String> uris;
}