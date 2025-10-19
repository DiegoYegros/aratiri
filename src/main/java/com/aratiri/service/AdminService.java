package com.aratiri.service;

import com.aratiri.dto.admin.*;
import lnrpc.*;

import java.time.Instant;
import java.util.List;

public interface AdminService {
    ListChannelsResponseDTO listChannels();
    String openChannel(OpenChannelRequestDTO request);
    CloseStatusUpdate closeChannel(CloseChannelRequestDTO request);
    List<NodeInfoDTO> listNodes();
    GetInfoResponse getNodeInfo();
    ChannelBalanceResponse getChannelBalance();
    List<TransactionStatsDTO> getTransactionStats(Instant from, Instant to);
    void connectPeer(String pubkey, String host);
    List<Peer> listPeers();
}