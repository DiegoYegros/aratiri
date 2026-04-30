package com.aratiri.admin.application;

import com.aratiri.admin.application.dto.*;
import com.aratiri.admin.application.port.out.LightningNodeAdminPort;
import com.aratiri.admin.application.port.out.NodeSettingsPort;
import com.aratiri.admin.application.port.out.TransactionStatsPort;
import com.aratiri.admin.domain.NodeSettings;
import com.aratiri.infrastructure.persistence.jpa.entity.NodeOperationEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.NodeOperationStatus;
import com.aratiri.infrastructure.persistence.jpa.entity.NodeOperationType;
import com.aratiri.infrastructure.persistence.jpa.repository.NodeOperationsRepository;
import com.aratiri.shared.exception.AratiriException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lnrpc.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminAdapterTest {

    @Mock
    private LightningNodeAdminPort lightningNodeAdminPort;

    @Mock
    private TransactionStatsPort transactionStatsPort;

    @Mock
    private NodeSettingsPort nodeSettingsPort;

    @Mock
    private NodeOperationsRepository nodeOperationsRepository;

    private AdminAdapter adminAdapter;

    @BeforeEach
    void setUp() {
        adminAdapter = new AdminAdapter(
                lightningNodeAdminPort,
                transactionStatsPort,
                nodeSettingsPort,
                nodeOperationsRepository
        );
    }

    @Test
    void listPeers_returnsAratiriPeerDtosFromLightningPort() {
        List<PeerDTO> peers = List.of(PeerDTO.builder()
                .pubKey("peer-pubkey")
                .address("203.0.113.10:9735")
                .build());
        when(lightningNodeAdminPort.listPeers()).thenReturn(peers);

        List<PeerDTO> result = adminAdapter.listPeers();

        assertEquals(peers, result);
        assertEquals("peer-pubkey", result.getFirst().getPubKey());
        assertEquals("203.0.113.10:9735", result.getFirst().getAddress());
    }

    @Test
    void getNodeInfo_returnsAratiriNodeInfoFromLightningPort() {
        NodeInfoResponseDTO nodeInfo = NodeInfoResponseDTO.builder()
                .version("0.18.0-beta")
                .commitHash("commit")
                .identityPubkey("identity")
                .alias("aratiri")
                .color("#3399ff")
                .numPendingChannels(1)
                .numActiveChannels(2)
                .numInactiveChannels(3)
                .numPeers(4)
                .blockHeight(850_000)
                .blockHash("block-hash")
                .syncedToChain(true)
                .syncedToGraph(false)
                .chains(List.of(new ChainDTO("bitcoin", "mainnet")))
                .uris(List.of("identity@example.com:9735"))
                .build();
        when(lightningNodeAdminPort.getNodeInfo()).thenReturn(nodeInfo);

        NodeInfoResponseDTO result = adminAdapter.getNodeInfo();

        assertEquals(nodeInfo, result);
        assertEquals("bitcoin", result.getChains().getFirst().getChain());
        assertEquals("mainnet", result.getChains().getFirst().getNetwork());
    }

    @Test
    void connectPeer_success() {
        ConnectPeerRequestDTO request = new ConnectPeerRequestDTO();
        request.setPubkey("02abc");
        request.setHost("127.0.0.1:9735");
        doNothing().when(lightningNodeAdminPort).connectPeer("02abc", "127.0.0.1:9735");

        assertDoesNotThrow(() -> adminAdapter.connectPeer(request));
    }

    @Test
    void connectPeer_grpcError_throwsAratiriException() {
        ConnectPeerRequestDTO request = new ConnectPeerRequestDTO();
        request.setPubkey("02abc");
        request.setHost("127.0.0.1:9735");
        StatusRuntimeException grpcError = new StatusRuntimeException(
                Status.UNAVAILABLE.withDescription("peer offline"));
        doThrow(grpcError).when(lightningNodeAdminPort).connectPeer(anyString(), anyString());

        AratiriException ex = assertThrows(AratiriException.class, () -> adminAdapter.connectPeer(request));
        assertTrue(ex.getMessage().contains("peer offline"));
    }

    @Test
    void openChannel_success() {
        OpenChannelRequestDTO request = new OpenChannelRequestDTO();
        request.setNodePubkey("02abc");
        request.setLocalFundingAmount(1000000L);
        request.setPushSat(100000L);
        request.setPrivateChannel(true);

        ChannelPoint mockChannelPoint = mock(ChannelPoint.class);
        when(mockChannelPoint.getFundingTxidStr()).thenReturn("deadbeef");
        when(lightningNodeAdminPort.openChannel("02abc", 1000000L, 100000L, true))
                .thenReturn(mockChannelPoint);

        String result = adminAdapter.openChannel(request);
        assertEquals("deadbeef", result);
    }

    @Test
    void openChannel_grpcError_throwsAratiriException() {
        OpenChannelRequestDTO request = new OpenChannelRequestDTO();
        request.setNodePubkey("02abc");
        request.setLocalFundingAmount(1000000L);
        request.setPushSat(0);
        request.setPrivateChannel(false);

        StatusRuntimeException grpcError = new StatusRuntimeException(
                Status.INVALID_ARGUMENT.withDescription("insufficient funds"));
        when(lightningNodeAdminPort.openChannel(anyString(), anyLong(), anyLong(), anyBoolean()))
                .thenThrow(grpcError);

        AratiriException ex = assertThrows(AratiriException.class, () -> adminAdapter.openChannel(request));
        assertTrue(ex.getMessage().contains("insufficient funds"));
    }

    @Test
    void closeChannel_success() {
        CloseChannelRequestDTO request = new CloseChannelRequestDTO();
        request.setChannelPoint("deadbeef:0");
        request.setForce(false);

        CloseStatusUpdate mockUpdate = mock(CloseStatusUpdate.class);
        when(lightningNodeAdminPort.closeChannel("deadbeef", 0, false)).thenReturn(mockUpdate);

        CloseStatusUpdate result = adminAdapter.closeChannel(request);
        assertNotNull(result);
    }

    @Test
    void closeChannel_invalidFormat_throwsAratiriException() {
        CloseChannelRequestDTO request = new CloseChannelRequestDTO();
        request.setChannelPoint("invalid-format");
        request.setForce(false);

        AratiriException ex = assertThrows(AratiriException.class, () -> adminAdapter.closeChannel(request));
        assertTrue(ex.getMessage().contains("Invalid channel point format"));
    }

    @Test
    void closeChannel_invalidOutputIndex_throwsAratiriException() {
        CloseChannelRequestDTO request = new CloseChannelRequestDTO();
        request.setChannelPoint("deadbeef:notanumber");
        request.setForce(false);

        AratiriException ex = assertThrows(AratiriException.class, () -> adminAdapter.closeChannel(request));
        assertTrue(ex.getMessage().contains("Invalid channel point"));
    }

    @Test
    void getChannelBalance_returnsMappedDto() {
        ChannelBalanceResponse balance = mock(ChannelBalanceResponse.class);
        Amount localAmount = mock(Amount.class);
        Amount remoteAmount = mock(Amount.class);
        when(localAmount.getSat()).thenReturn(600000L);
        when(localAmount.getMsat()).thenReturn(600000000000L);
        when(remoteAmount.getSat()).thenReturn(400000L);
        when(remoteAmount.getMsat()).thenReturn(400000000000L);
        when(balance.getLocalBalance()).thenReturn(localAmount);
        when(balance.getRemoteBalance()).thenReturn(remoteAmount);
        when(lightningNodeAdminPort.getChannelBalance()).thenReturn(balance);

        ChannelBalanceResponseDTO result = adminAdapter.getChannelBalance();
        assertEquals(600000L, result.getLocalBalance().getSat());
        assertEquals(400000L, result.getRemoteBalance().getSat());
    }

    @Test
    void getWalletBalance_returnsMappedDto() {
        WalletBalanceResponse balance = mock(WalletBalanceResponse.class);
        when(balance.getConfirmedBalance()).thenReturn(800000L);
        when(balance.getUnconfirmedBalance()).thenReturn(200000L);
        when(lightningNodeAdminPort.getWalletBalance()).thenReturn(balance);

        WalletBalanceResponseDTO result = adminAdapter.getWalletBalance();
        assertEquals(800000L, result.getConfirmedBalance());
        assertEquals(200000L, result.getUnconfirmedBalance());
    }

    @Test
    void generateTaprootAddress_returnsAddress() {
        when(lightningNodeAdminPort.generateAddress(AddressType.TAPROOT_PUBKEY))
                .thenReturn("bc1ptaprootaddress");

        NewAddressResponseDTO result = adminAdapter.generateTaprootAddress();
        assertEquals("bc1ptaprootaddress", result.getAddress());
    }

    @Test
    void getTransactionStats_returnsMappedDto() {
        Instant from = Instant.now().minusSeconds(86400);
        Instant to = Instant.now();
        TransactionStatsDTO stats = new TransactionStatsDTO(
                LocalDate.of(2024, 1, 1), "PAYMENT", BigDecimal.valueOf(1000), 42);
        when(transactionStatsPort.findTransactionStats(from, to)).thenReturn(List.of(stats));

        TransactionStatsResponseDTO result = adminAdapter.getTransactionStats(from, to);
        assertEquals(1, result.getStats().size());
        assertEquals(42, result.getStats().get(0).getCount());
    }

    @Test
    void getNodeSettings_returnsMappedDto() {
        Instant now = Instant.now();
        NodeSettings settings = new NodeSettings(true, 60000L, now, now);
        when(nodeSettingsPort.loadSettings()).thenReturn(settings);

        NodeSettingsDTO result = adminAdapter.getNodeSettings();
        assertNotNull(result);
        assertTrue(result.isAutoManagePeers());
        assertEquals(60000L, result.getTransactionReconciliationMinAgeMs());
    }

    @Test
    void updateNodeSettings_updatesPartial() {
        Instant now = Instant.now();
        NodeSettings current = new NodeSettings(true, 60000L, now, now);
        when(nodeSettingsPort.loadSettings()).thenReturn(current);

        NodeSettings updatedSettings = new NodeSettings(false, 30000L, now, now);
        when(nodeSettingsPort.saveSettings(any(NodeSettings.class))).thenReturn(updatedSettings);

        UpdateNodeSettingsRequestDTO request = new UpdateNodeSettingsRequestDTO();
        request.setAutoManagePeers(false);
        request.setTransactionReconciliationMinAgeMs(30000L);

        NodeSettingsDTO result = adminAdapter.updateNodeSettings(request);
        assertFalse(result.isAutoManagePeers());
        assertEquals(30000L, result.getTransactionReconciliationMinAgeMs());
    }

    @Test
    void updateNodeSettings_preservesExistingWhenNull() {
        Instant now = Instant.now();
        NodeSettings current = new NodeSettings(true, 60000L, now, now);
        when(nodeSettingsPort.loadSettings()).thenReturn(current);

        when(nodeSettingsPort.saveSettings(any(NodeSettings.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateNodeSettingsRequestDTO request = new UpdateNodeSettingsRequestDTO();
        request.setAutoManagePeers(null);
        request.setTransactionReconciliationMinAgeMs(null);

        adminAdapter.updateNodeSettings(request);

        ArgumentCaptor<NodeSettings> captor = ArgumentCaptor.forClass(NodeSettings.class);
        verify(nodeSettingsPort).saveSettings(captor.capture());
        assertTrue(captor.getValue().autoManagePeers());
        assertEquals(60000L, captor.getValue().transactionReconciliationMinAgeMs());
    }

    @Test
    void updateAutoManagePeers_updatesAndReturnsDto() {
        Instant now = Instant.now();
        NodeSettings current = new NodeSettings(false, 60000L, now, now);
        when(nodeSettingsPort.loadSettings()).thenReturn(current);

        NodeSettings updated = new NodeSettings(true, 60000L, now, now);
        when(nodeSettingsPort.saveSettings(any(NodeSettings.class))).thenReturn(updated);

        NodeSettingsDTO result = adminAdapter.updateAutoManagePeers(true);
        assertTrue(result.isAutoManagePeers());
        assertEquals(60000L, result.getTransactionReconciliationMinAgeMs());
    }

    @Test
    void listNodeOperations_returnsMappedDtos() {
        UUID id = UUID.randomUUID();
        NodeOperationEntity entity = NodeOperationEntity.builder()
                .operationType(NodeOperationType.LIGHTNING_PAYMENT)
                .status(NodeOperationStatus.PENDING)
                .transactionId("tx-001")
                .userId("user-1")
                .referenceId("ref-1")
                .attemptCount(1)
                .requestPayload("{}")
                .nextAttemptAt(Instant.now())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .id(id)
                .build();

        when(nodeOperationsRepository.findByFilters(isNull(), isNull(), isNull(), any()))
                .thenReturn(List.of(entity));

        List<NodeOperationResponseDTO> results = adminAdapter.listNodeOperations(null, null, null, 10);
        assertEquals(1, results.size());
        assertEquals("LIGHTNING_PAYMENT", results.get(0).getOperationType());
        assertEquals("PENDING", results.get(0).getStatus());
    }

    @Test
    void listChannels_returnsMappedDto() {
        Channel channel = Channel.newBuilder()
                .setChannelPoint("tx:0")
                .setRemotePubkey("02remote")
                .setCapacity(1000000)
                .setLocalBalance(900000)
                .setRemoteBalance(100000)
                .setActive(true)
                .setPrivate(false)
                .build();
        ListChannelsResponse channelsResponse = ListChannelsResponse.newBuilder()
                .addChannels(channel)
                .build();
        PendingChannelsResponse pendingResponse = PendingChannelsResponse.newBuilder()
                .setTotalLimboBalance(5000)
                .build();

        when(lightningNodeAdminPort.listChannels()).thenReturn(channelsResponse);
        when(lightningNodeAdminPort.listPendingChannels()).thenReturn(pendingResponse);

        ListChannelsResponseDTO result = adminAdapter.listChannels();
        assertEquals(1, result.getOpenChannels().size());
        assertEquals("tx:0", result.getOpenChannels().get(0).getChannelPoint());
        assertEquals(5000L, result.getPendingChannels().getTotalLimboBalance());
    }

    @Test
    void listNodes_returnsSortedRemotesResponse() {
        LightningNode node1 = LightningNode.newBuilder()
                .setPubKey("pk1")
                .setAlias("node1")
                .setColor("#ffffff")
                .addAddresses(NodeAddress.newBuilder().setAddr("127.0.0.1:9735"))
                .build();
        LightningNode node2 = LightningNode.newBuilder()
                .setPubKey("pk2")
                .setAlias("node2")
                .setColor("#000000")
                .addAddresses(NodeAddress.newBuilder().setAddr("127.0.0.2:9735"))
                .build();
        ChannelGraph channelGraph = ChannelGraph.newBuilder()
                .addNodes(node1)
                .addNodes(node2)
                .addEdges(ChannelEdge.newBuilder()
                        .setNode1Pub("pk1")
                        .setNode2Pub("pk2")
                        .setCapacity(5000000)
                        .build())
                .build();

        NodeMetricsResponse metrics = NodeMetricsResponse.newBuilder()
                .putBetweennessCentrality("pk1",
                        FloatMetric.newBuilder().setNormalizedValue(0.8).build())
                .putBetweennessCentrality("pk2",
                        FloatMetric.newBuilder().setNormalizedValue(0.5).build())
                .build();

        when(lightningNodeAdminPort.describeGraph()).thenReturn(channelGraph);
        when(lightningNodeAdminPort.getNodeMetrics()).thenReturn(metrics);

        RemotesResponseDTO result = adminAdapter.listNodes();
        assertEquals(2, result.getNodes().size());
        assertEquals("pk1", result.getNodes().get(0).getPubKey());
        assertEquals("pk2", result.getNodes().get(1).getPubKey());
        assertEquals(5000000L, result.getNodes().get(0).getCapacity());
        assertEquals(1, result.getNodes().get(0).getNumChannels());
        assertEquals(5000000L, result.getNodes().get(1).getCapacity());
        assertEquals(1, result.getNodes().get(1).getNumChannels());
    }

    @Test
    void listNodes_filtersZeroCentralityNodes() {
        LightningNode node1 = LightningNode.newBuilder()
                .setPubKey("pk1")
                .setAlias("node1")
                .setColor("#ffffff")
                .build();
        LightningNode node2 = LightningNode.newBuilder()
                .setPubKey("pk2")
                .setAlias("node2")
                .setColor("#000000")
                .build();
        ChannelGraph channelGraph = ChannelGraph.newBuilder()
                .addNodes(node1)
                .addNodes(node2)
                .build();

        NodeMetricsResponse metrics = NodeMetricsResponse.newBuilder()
                .putBetweennessCentrality("pk1",
                        FloatMetric.newBuilder().setNormalizedValue(0.3).build())
                .putBetweennessCentrality("pk2",
                        FloatMetric.newBuilder().setNormalizedValue(0.0).build())
                .build();

        when(lightningNodeAdminPort.describeGraph()).thenReturn(channelGraph);
        when(lightningNodeAdminPort.getNodeMetrics()).thenReturn(metrics);

        RemotesResponseDTO result = adminAdapter.listNodes();
        assertEquals(1, result.getNodes().size());
        assertEquals("pk1", result.getNodes().get(0).getPubKey());
    }

    @Test
    void listNodeOperations_withCompletedAt_returnsMappedDtoWithCompletedAt() {
        Instant now = Instant.now();
        Instant completed = now.plusSeconds(60);
        UUID id = UUID.randomUUID();
        NodeOperationEntity entity = NodeOperationEntity.builder()
                .operationType(NodeOperationType.LIGHTNING_PAYMENT)
                .status(NodeOperationStatus.SUCCEEDED)
                .transactionId("tx-002")
                .userId("user-2")
                .referenceId("ref-2")
                .externalId("ext-2")
                .attemptCount(2)
                .lastError(null)
                .requestPayload("{}")
                .nextAttemptAt(now)
                .createdAt(now)
                .updatedAt(now)
                .completedAt(completed)
                .id(id)
                .build();

        when(nodeOperationsRepository.findByFilters(isNull(), isNull(), isNull(), any()))
                .thenReturn(List.of(entity));

        List<NodeOperationResponseDTO> results = adminAdapter.listNodeOperations(null, null, null, 10);
        assertEquals(1, results.size());
        assertEquals("SUCCEEDED", results.get(0).getStatus());
        assertNotNull(results.get(0).getCompletedAt());
        assertEquals("ext-2", results.get(0).getExternalId());
    }

    @Test
    void listNodeOperations_withFilters_appliesFilters() {
        UUID id = UUID.randomUUID();
        NodeOperationEntity entity = NodeOperationEntity.builder()
                .operationType(NodeOperationType.ONCHAIN_SEND)
                .status(NodeOperationStatus.FAILED)
                .transactionId("tx-003")
                .userId("user-3")
                .attemptCount(1)
                .requestPayload("{}")
                .nextAttemptAt(Instant.now())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .id(id)
                .build();

        when(nodeOperationsRepository.findByFilters(
                eq(NodeOperationStatus.FAILED),
                eq(NodeOperationType.ONCHAIN_SEND),
                eq("tx-003"),
                any()))
                .thenReturn(List.of(entity));

        List<NodeOperationResponseDTO> results = adminAdapter.listNodeOperations(
                NodeOperationStatus.FAILED, NodeOperationType.ONCHAIN_SEND, "tx-003", 10);
        assertEquals(1, results.size());
        assertEquals("ONCHAIN_SEND", results.get(0).getOperationType());
        assertEquals("FAILED", results.get(0).getStatus());
    }
}
