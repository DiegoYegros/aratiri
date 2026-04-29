package com.aratiri.admin.application.port.in;

import com.aratiri.admin.application.dto.*;
import com.aratiri.infrastructure.persistence.jpa.entity.NodeOperationStatus;
import com.aratiri.infrastructure.persistence.jpa.entity.NodeOperationType;
import lnrpc.CloseStatusUpdate;

import java.time.Instant;
import java.util.List;

public interface AdminPort {

    void connectPeer(ConnectPeerRequestDTO request);

    List<PeerDTO> listPeers();

    NodeInfoResponseDTO getNodeInfo();

    ListChannelsResponseDTO listChannels();

    String openChannel(OpenChannelRequestDTO request);

    CloseStatusUpdate closeChannel(CloseChannelRequestDTO request);

    RemotesResponseDTO listNodes();

    ChannelBalanceResponseDTO getChannelBalance();

    WalletBalanceResponseDTO getWalletBalance();

    NewAddressResponseDTO generateTaprootAddress();

    TransactionStatsResponseDTO getTransactionStats(Instant from, Instant to);

    NodeSettingsDTO getNodeSettings();

    NodeSettingsDTO updateNodeSettings(UpdateNodeSettingsRequestDTO request);

    NodeSettingsDTO updateAutoManagePeers(boolean enabled);

    List<NodeOperationResponseDTO> listNodeOperations(NodeOperationStatus status, NodeOperationType type, String transactionId, int limit);
}
