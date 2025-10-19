package com.aratiri.admin.application.port.in;

import com.aratiri.dto.admin.ChannelBalanceResponseDTO;
import com.aratiri.dto.admin.CloseChannelRequestDTO;
import com.aratiri.dto.admin.ConnectPeerRequestDTO;
import com.aratiri.dto.admin.ListChannelsResponseDTO;
import com.aratiri.dto.admin.NodeSettingsDTO;
import com.aratiri.dto.admin.OpenChannelRequestDTO;
import com.aratiri.dto.admin.RemotesResponseDTO;
import com.aratiri.dto.admin.TransactionStatsResponseDTO;
import lnrpc.CloseStatusUpdate;
import lnrpc.GetInfoResponse;
import lnrpc.Peer;

import java.time.Instant;
import java.util.List;

public interface AdminPort {

    void connectPeer(ConnectPeerRequestDTO request);

    List<Peer> listPeers();

    GetInfoResponse getNodeInfo();

    ListChannelsResponseDTO listChannels();

    String openChannel(OpenChannelRequestDTO request);

    CloseStatusUpdate closeChannel(CloseChannelRequestDTO request);

    RemotesResponseDTO listNodes();

    ChannelBalanceResponseDTO getChannelBalance();

    TransactionStatsResponseDTO getTransactionStats(Instant from, Instant to);

    NodeSettingsDTO getNodeSettings();

    NodeSettingsDTO updateAutoManagePeers(boolean enabled);
}
