package com.aratiri.admin.application.port.in;

import com.aratiri.admin.application.dto.*;
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

    WalletBalanceResponseDTO getWalletBalance();

    NewAddressResponseDTO generateTaprootAddress();

    TransactionStatsResponseDTO getTransactionStats(Instant from, Instant to);

    NodeSettingsDTO getNodeSettings();

    NodeSettingsDTO updateAutoManagePeers(boolean enabled);
}
