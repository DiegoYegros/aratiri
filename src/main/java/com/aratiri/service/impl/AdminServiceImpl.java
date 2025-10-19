package com.aratiri.service.impl;

import com.aratiri.dto.admin.*;
import com.aratiri.exception.AratiriException;
import com.aratiri.repository.TransactionsRepository;
import com.aratiri.service.AdminService;
import com.google.protobuf.ByteString;
import io.grpc.StatusRuntimeException;
import lnrpc.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminServiceImpl implements AdminService {

    private final LightningGrpc.LightningBlockingStub lightningStub;
    private final TransactionsRepository transactionsRepository;

    public AdminServiceImpl(LightningGrpc.LightningBlockingStub lightningStub, TransactionsRepository transactionsRepository) {
        this.lightningStub = lightningStub;
        this.transactionsRepository = transactionsRepository;
    }

    @Override
    public ListChannelsResponseDTO listChannels() {
        ListChannelsRequest request = ListChannelsRequest.newBuilder().build();
        ListChannelsResponse response = lightningStub.listChannels(request);
        PendingChannelsRequest pending = PendingChannelsRequest.newBuilder().build();
        PendingChannelsResponse pendingChannelsResponse = lightningStub.pendingChannels(pending);
        return new ListChannelsResponseDTO(response.getChannelsList(), pendingChannelsResponse);
    }

    @Override
    public String openChannel(OpenChannelRequestDTO request) {
        byte[] pubkeyBytes = HexFormat.of().parseHex(request.getNodePubkey());
        OpenChannelRequest openChannelRequest = OpenChannelRequest.newBuilder()
                .setNodePubkey(ByteString.copyFrom(pubkeyBytes))
                .setLocalFundingAmount(request.getLocalFundingAmount())
                .setPushSat(request.getPushSat())
                .setPrivate(request.isPrivateChannel())
                .build();
        try {
            ChannelPoint channelPoint = lightningStub.openChannelSync(openChannelRequest);
            return channelPoint.getFundingTxidStr();
        } catch (StatusRuntimeException e) {
            throw new AratiriException(e.getStatus().getDescription(), HttpStatus.BAD_REQUEST);
        }
    }

    @Override
    public CloseStatusUpdate closeChannel(CloseChannelRequestDTO request) {
        String[] parts = request.getChannelPoint().split(":");
        ChannelPoint channelPoint = ChannelPoint.newBuilder()
                .setFundingTxidStr(parts[0])
                .setOutputIndex(Integer.parseInt(parts[1]))
                .build();

        CloseChannelRequest closeChannelRequest = CloseChannelRequest.newBuilder()
                .setChannelPoint(channelPoint)
                .setForce(request.isForce())
                .build();
        return lightningStub.closeChannel(closeChannelRequest).next();
    }

    /**
     * Retrieves all nodes from the Lightning Network graph, enriched with key metrics.
     * Each node's information includes its betweenness centrality (a measure of its
     * importance in routing), total channel capacity, and the number of channels it has.
     *
     * @return A list of {@link NodeInfoDTO} objects with comprehensive details
     * for each node, useful for identifying potential channel peers.
     */
    @Override
    public List<NodeInfoDTO> listNodes() {
        ChannelGraph channelGraph = lightningStub.describeGraph(ChannelGraphRequest.newBuilder().build());
        NodeMetricsResponse nodeMetrics = lightningStub.getNodeMetrics(NodeMetricsRequest.newBuilder()
                .addTypes(NodeMetricType.BETWEENNESS_CENTRALITY)
                .build());
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
        for (ChannelEdge edge : channelGraph.getEdgesList()) {
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
        return nodeInfoMap.values().stream()
                .filter(e -> e.getBetweennessCentrality() !=0.0)
                .sorted(Comparator.comparing(NodeInfoDTO::getBetweennessCentrality).reversed()
                        .thenComparing(NodeInfoDTO::getCapacity).reversed()
                        .thenComparing(NodeInfoDTO::getNumChannels).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public GetInfoResponse getNodeInfo() {
        return lightningStub.getInfo(GetInfoRequest.newBuilder().build());
    }

    @Override
    public ChannelBalanceResponse getChannelBalance() {
        return lightningStub.channelBalance(ChannelBalanceRequest.newBuilder().build());
    }

    @Override
    public List<TransactionStatsDTO> getTransactionStats(Instant from, Instant to) {
        return transactionsRepository.findTransactionStats(from, to);
    }

    @Override
    public void connectPeer(String pubkey, String host) {
        try {
            ConnectPeerRequest request = ConnectPeerRequest.newBuilder()
                    .setAddr(LightningAddress.newBuilder().setPubkey(pubkey).setHost(host).build())
                    .build();
            lightningStub.connectPeer(request);
        } catch (StatusRuntimeException e) {
            throw new AratiriException("Failed to connect to peer: " + e.getStatus().getDescription(), HttpStatus.BAD_GATEWAY);
        }
    }

    @Override
    public List<Peer> listPeers() {
        return lightningStub.listPeers(ListPeersRequest.newBuilder().build()).getPeersList();
    }

}