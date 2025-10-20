package com.aratiri.admin.application.port.in;

import com.aratiri.admin.application.dto.ChannelBalanceResponseDTO;
import com.aratiri.admin.application.dto.CloseChannelRequestDTO;
import com.aratiri.admin.application.dto.ConnectPeerRequestDTO;
import com.aratiri.admin.application.dto.ListChannelsResponseDTO;
import com.aratiri.admin.application.dto.NodeSettingsDTO;
import com.aratiri.admin.application.dto.OpenChannelRequestDTO;
import com.aratiri.admin.application.dto.RemotesResponseDTO;
import com.aratiri.admin.application.dto.TransactionStatsResponseDTO;
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
