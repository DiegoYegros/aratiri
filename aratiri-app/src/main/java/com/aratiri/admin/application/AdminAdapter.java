package com.aratiri.admin.application;

import com.aratiri.admin.application.port.in.AdminPort;
import com.aratiri.admin.application.port.out.LightningNodeAdminPort;
import com.aratiri.admin.application.port.out.NodeSettingsPort;
import com.aratiri.admin.application.port.out.TransactionStatsPort;
import com.aratiri.admin.domain.NodeSettings;
import com.aratiri.shared.exception.AratiriException;
import com.aratiri.admin.application.dto.AmountDTO;
import com.aratiri.admin.application.dto.ChannelBalanceResponseDTO;
import com.aratiri.admin.application.dto.CloseChannelRequestDTO;
import com.aratiri.admin.application.dto.ConnectPeerRequestDTO;
import com.aratiri.admin.application.dto.ListChannelsResponseDTO;
import com.aratiri.admin.application.dto.NodeInfoDTO;
import com.aratiri.admin.application.dto.NodeSettingsDTO;
import com.aratiri.admin.application.dto.NewAddressResponseDTO;
import com.aratiri.admin.application.dto.OpenChannelRequestDTO;
import com.aratiri.admin.application.dto.RemotesResponseDTO;
import com.aratiri.admin.application.dto.TransactionStatsDTO;
import com.aratiri.admin.application.dto.TransactionStatsResponseDTO;
import com.aratiri.admin.application.dto.WalletBalanceResponseDTO;
import io.grpc.StatusRuntimeException;
import lnrpc.ChannelBalanceResponse;
import lnrpc.ChannelEdge;
import lnrpc.ChannelGraph;
import lnrpc.ChannelPoint;
import lnrpc.CloseStatusUpdate;
import lnrpc.FloatMetric;
import lnrpc.GetInfoResponse;
import lnrpc.LightningNode;
import lnrpc.NodeAddress;
import lnrpc.NodeMetricType;
import lnrpc.NodeMetricsResponse;
import lnrpc.Peer;
import lnrpc.WalletBalanceResponse;
import lnrpc.AddressType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class AdminAdapter implements AdminPort {

    private final LightningNodeAdminPort lightningNodeAdminPort;
    private final TransactionStatsPort transactionStatsPort;
    private final NodeSettingsPort nodeSettingsPort;

    public AdminAdapter(
            LightningNodeAdminPort lightningNodeAdminPort,
            TransactionStatsPort transactionStatsPort,
            NodeSettingsPort nodeSettingsPort
    ) {
        this.lightningNodeAdminPort = lightningNodeAdminPort;
        this.transactionStatsPort = transactionStatsPort;
        this.nodeSettingsPort = nodeSettingsPort;
    }

    @Override
    public void connectPeer(ConnectPeerRequestDTO request) {
        try {
            lightningNodeAdminPort.connectPeer(request.getPubkey(), request.getHost());
        } catch (StatusRuntimeException e) {
            throw new AratiriException(
                    "Failed to connect to peer: " + Objects.requireNonNullElse(e.getStatus().getDescription(), "Unknown error"),
                    HttpStatus.BAD_GATEWAY.value()
            );
        }
    }

    @Override
    public List<Peer> listPeers() {
        return lightningNodeAdminPort.listPeers();
    }

    @Override
    public GetInfoResponse getNodeInfo() {
        return lightningNodeAdminPort.getNodeInfo();
    }

    @Override
    public ListChannelsResponseDTO listChannels() {
        return new ListChannelsResponseDTO(
                lightningNodeAdminPort.listChannels().getChannelsList(),
                lightningNodeAdminPort.listPendingChannels()
        );
    }

    @Override
    public String openChannel(OpenChannelRequestDTO request) {
        try {
            ChannelPoint channelPoint = lightningNodeAdminPort.openChannel(
                    request.getNodePubkey(),
                    request.getLocalFundingAmount(),
                    request.getPushSat(),
                    request.isPrivateChannel()
            );
            return channelPoint.getFundingTxidStr();
        } catch (StatusRuntimeException e) {
            throw new AratiriException(
                    Objects.requireNonNullElse(e.getStatus().getDescription(), "Unable to open channel"),
                    HttpStatus.BAD_REQUEST.value()
            );
        }
    }

    @Override
    public CloseStatusUpdate closeChannel(CloseChannelRequestDTO request) {
        String[] parts = request.getChannelPoint().split(":");
        if (parts.length != 2) {
            throw new AratiriException("Invalid channel point format. Expected 'fundingTxid:index'", HttpStatus.BAD_REQUEST.value());
        }
        int outputIndex;
        try {
            outputIndex = Integer.parseInt(parts[1]);
        } catch (NumberFormatException ex) {
            throw new AratiriException("Invalid channel point output index", HttpStatus.BAD_REQUEST.value());
        }
        return lightningNodeAdminPort.closeChannel(parts[0], outputIndex, request.isForce());
    }

    @Override
    public RemotesResponseDTO listNodes() {
        ChannelGraph channelGraph = lightningNodeAdminPort.describeGraph();
        NodeMetricsResponse nodeMetrics = lightningNodeAdminPort.getNodeMetrics();
        Map<String, NodeInfoDTO> nodeInfoMap = channelGraph.getNodesList().stream()
                .collect(Collectors.toMap(
                        LightningNode::getPubKey,
                        node -> NodeInfoDTO.builder()
                                .pubKey(node.getPubKey())
                                .alias(node.getAlias())
                                .color(node.getColor())
                                .addresses(node.getAddressesList().stream().map(NodeAddress::getAddr).collect(Collectors.toList()))
                                .capacity(0L)
                                .numChannels(0)
                                .betweennessCentrality(nodeMetrics.getBetweennessCentralityOrDefault(
                                        node.getPubKey(),
                                        FloatMetric.newBuilder().setNormalizedValue(0.0f).build()
                                ).getNormalizedValue())
                                .build()
                ));

        channelGraph.getEdgesList().forEach(edge -> updateNodeStats(nodeInfoMap, edge));

        List<NodeInfoDTO> nodes = nodeInfoMap.values().stream()
                .filter(node -> node.getBetweennessCentrality() != 0.0)
                .sorted(Comparator.comparing(NodeInfoDTO::getBetweennessCentrality).reversed()
                        .thenComparing(NodeInfoDTO::getCapacity).reversed()
                        .thenComparing(NodeInfoDTO::getNumChannels).reversed())
                .collect(Collectors.toList());
        return new RemotesResponseDTO(nodes);
    }

    private void updateNodeStats(Map<String, NodeInfoDTO> nodeInfoMap, ChannelEdge edge) {
        NodeInfoDTO node1 = nodeInfoMap.get(edge.getNode1Pub());
        if (node1 != null) {
            node1.setNumChannels(node1.getNumChannels() + 1);
            node1.setCapacity(node1.getCapacity() + edge.getCapacity());
        }
        NodeInfoDTO node2 = nodeInfoMap.get(edge.getNode2Pub());
        if (node2 != null) {
            node2.setNumChannels(node2.getNumChannels() + 1);
            node2.setCapacity(node2.getCapacity() + edge.getCapacity());
        }
    }

    @Override
    public ChannelBalanceResponseDTO getChannelBalance() {
        ChannelBalanceResponse balance = lightningNodeAdminPort.getChannelBalance();
        return ChannelBalanceResponseDTO.builder()
                .localBalance(new AmountDTO(balance.getLocalBalance().getSat(), balance.getLocalBalance().getMsat()))
                .remoteBalance(new AmountDTO(balance.getRemoteBalance().getSat(), balance.getRemoteBalance().getMsat()))
                .build();
    }

    @Override
    public WalletBalanceResponseDTO getWalletBalance() {
        WalletBalanceResponse balance = lightningNodeAdminPort.getWalletBalance();
        return WalletBalanceResponseDTO.builder()
                .confirmedBalance(balance.getConfirmedBalance())
                .unconfirmedBalance(balance.getUnconfirmedBalance())
                .build();
    }

    @Override
    public NewAddressResponseDTO generateTaprootAddress() {
        String address = lightningNodeAdminPort.generateAddress(AddressType.TAPROOT_PUBKEY);
        return NewAddressResponseDTO.builder()
                .address(address)
                .build();
    }

    @Override
    public TransactionStatsResponseDTO getTransactionStats(Instant from, Instant to) {
        List<TransactionStatsDTO> stats = transactionStatsPort.findTransactionStats(from, to);
        return new TransactionStatsResponseDTO(stats);
    }

    @Override
    public NodeSettingsDTO getNodeSettings() {
        NodeSettings settings = nodeSettingsPort.loadSettings();
        return toDto(settings);
    }

    @Override
    public NodeSettingsDTO updateAutoManagePeers(boolean enabled) {
        NodeSettings settings = nodeSettingsPort.updateAutoManagePeers(enabled);
        return toDto(settings);
    }

    private NodeSettingsDTO toDto(NodeSettings settings) {
        NodeSettingsDTO dto = new NodeSettingsDTO();
        dto.setAutoManagePeers(settings.autoManagePeers());
        dto.setCreatedAt(settings.createdAt());
        dto.setUpdatedAt(settings.updatedAt());
        return dto;
    }
}
