package com.aratiri.service;

import com.aratiri.dto.admin.CloseChannelRequestDTO;
import com.aratiri.dto.admin.NodeInfoDTO;
import com.aratiri.dto.admin.OpenChannelRequestDTO;
import com.aratiri.dto.admin.TransactionStatsDTO;
import lnrpc.*;

import java.time.Instant;
import java.util.List;

public interface AdminService {
    List<Channel> listChannels();
    String openChannel(OpenChannelRequestDTO request);
    CloseStatusUpdate closeChannel(CloseChannelRequestDTO request);
    List<NodeInfoDTO> listNodes();
    GetInfoResponse getNodeInfo();
    ChannelBalanceResponse getChannelBalance();
    List<TransactionStatsDTO> getTransactionStats(Instant from, Instant to);
    void connectPeer(String pubkey, String host);
    List<Peer> listPeers();
}