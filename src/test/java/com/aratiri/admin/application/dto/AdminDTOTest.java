package com.aratiri.admin.application.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AdminDTOTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void amountDTO_serialization() throws Exception {
        AmountDTO dto = new AmountDTO();
        dto.setSat(1000L);
        dto.setMsat(1000000L);
        String json = objectMapper.writeValueAsString(dto);
        AmountDTO deserialized = objectMapper.readValue(json, AmountDTO.class);
        assertEquals(1000L, deserialized.getSat());
        assertEquals(1000000L, deserialized.getMsat());
    }

    @Test
    void chainDTO_serialization() throws Exception {
        ChainDTO dto = new ChainDTO("bitcoin", "mainnet");
        String json = objectMapper.writeValueAsString(dto);
        assertTrue(json.contains("bitcoin"));
        assertEquals("bitcoin", dto.getChain());
        assertEquals("mainnet", dto.getNetwork());
    }

    @Test
    void channelBalanceResponseDTO_serialization() throws Exception {
        AmountDTO local = new AmountDTO();
        local.setSat(600000L);
        local.setMsat(600000000000L);
        AmountDTO remote = new AmountDTO();
        remote.setSat(400000L);
        remote.setMsat(400000000000L);

        ChannelBalanceResponseDTO dto = new ChannelBalanceResponseDTO();
        dto.setLocalBalance(local);
        dto.setRemoteBalance(remote);

        String json = objectMapper.writeValueAsString(dto);
        assertTrue(json.contains("local_balance"));
        ChannelBalanceResponseDTO deserialized = objectMapper.readValue(json, ChannelBalanceResponseDTO.class);
        assertEquals(600000L, deserialized.getLocalBalance().getSat());
        assertEquals(400000L, deserialized.getRemoteBalance().getSat());
    }

    @Test
    void channelDTO_serialization() throws Exception {
        ChannelDTO dto = new ChannelDTO();
        dto.setChannelPoint("abc:0");
        dto.setRemotePubkey("02abc");
        dto.setCapacity(1000000L);
        dto.setLocalBalance(600000L);
        dto.setRemoteBalance(400000L);
        dto.setActive(true);
        dto.setPrivateChannel(true);
        String json = objectMapper.writeValueAsString(dto);
        ChannelDTO deserialized = objectMapper.readValue(json, ChannelDTO.class);
        assertEquals("abc:0", deserialized.getChannelPoint());
        assertEquals("02abc", deserialized.getRemotePubkey());
        assertTrue(deserialized.isActive());
        assertTrue(deserialized.isPrivateChannel());
    }

    @Test
    void closeChannelRequestDTO_serialization() throws Exception {
        CloseChannelRequestDTO dto = new CloseChannelRequestDTO();
        dto.setChannelPoint("abc:0");
        dto.setForce(false);
        String json = objectMapper.writeValueAsString(dto);
        CloseChannelRequestDTO deserialized = objectMapper.readValue(json, CloseChannelRequestDTO.class);
        assertEquals("abc:0", deserialized.getChannelPoint());
        assertFalse(deserialized.isForce());
    }

    @Test
    void closedChannelDTO_serialization() throws Exception {
        ClosedChannelDTO dto = new ClosedChannelDTO();
        PendingChannelDTO channel = new PendingChannelDTO();
        channel.setRemoteNodePub("02abc");
        dto.setChannel(channel);
        dto.setClosingTxid("deadbeef");
        String json = objectMapper.writeValueAsString(dto);
        ClosedChannelDTO deserialized = objectMapper.readValue(json, ClosedChannelDTO.class);
        assertEquals("deadbeef", deserialized.getClosingTxid());
        assertNotNull(deserialized.getChannel());
    }

    @Test
    void commitmentsDTO_serialization() throws Exception {
        CommitmentsDTO dto = new CommitmentsDTO();
        dto.setLocalTxid("abc123");
        dto.setRemoteTxid("def456");
        dto.setRemotePendingTxid("ghi789");
        dto.setLocalCommitFeeSat(100L);
        dto.setRemoteCommitFeeSat(200L);
        dto.setRemotePendingCommitFeeSat(50L);
        String json = objectMapper.writeValueAsString(dto);
        CommitmentsDTO deserialized = objectMapper.readValue(json, CommitmentsDTO.class);
        assertEquals("abc123", deserialized.getLocalTxid());
        assertEquals(100L, deserialized.getLocalCommitFeeSat());
    }

    @Test
    void connectPeerRequestDTO_serialization() throws Exception {
        ConnectPeerRequestDTO dto = new ConnectPeerRequestDTO();
        dto.setPubkey("02abc");
        dto.setHost("127.0.0.1:9735");
        String json = objectMapper.writeValueAsString(dto);
        ConnectPeerRequestDTO deserialized = objectMapper.readValue(json, ConnectPeerRequestDTO.class);
        assertEquals("02abc", deserialized.getPubkey());
        assertEquals("127.0.0.1:9735", deserialized.getHost());
    }

    @Test
    void forceClosedChannelDTO_serialization() throws Exception {
        ForceClosedChannelDTO dto = new ForceClosedChannelDTO();
        PendingChannelDTO channel = new PendingChannelDTO();
        channel.setRemoteNodePub("02abc");
        dto.setChannel(channel);
        dto.setClosingTxid("deadbeef");
        dto.setLimboBalance(50000L);
        dto.setMaturityHeight(800000L);
        dto.setBlocksTilMaturity(40);
        dto.setRecoveredBalance(10000L);
        dto.setAnchor("ANCHOR");
        dto.setPendingHtlcs(List.of());
        String json = objectMapper.writeValueAsString(dto);
        ForceClosedChannelDTO deserialized = objectMapper.readValue(json, ForceClosedChannelDTO.class);
        assertEquals("deadbeef", deserialized.getClosingTxid());
        assertEquals(50000L, deserialized.getLimboBalance());
    }

    @Test
    void listChannelsResponseDTO_serialization() throws Exception {
        ChannelDTO channel = new ChannelDTO();
        channel.setChannelPoint("abc:0");
        channel.setRemotePubkey("02abc");
        channel.setCapacity(1000000L);
        channel.setLocalBalance(600000L);
        channel.setRemoteBalance(400000L);
        channel.setActive(true);
        channel.setPrivateChannel(false);

        PendingChannelsResponseDTO pending = new PendingChannelsResponseDTO();
        pending.setTotalLimboBalance(50000L);
        pending.setPendingOpenChannels(List.of());
        pending.setPendingClosingChannels(List.of());
        pending.setPendingForceClosingChannels(List.of());
        pending.setWaitingCloseChannels(List.of());

        ListChannelsResponseDTO dto = new ListChannelsResponseDTO();
        dto.setOpenChannels(List.of(channel));
        dto.setPendingChannels(pending);

        String json = objectMapper.writeValueAsString(dto);
        ListChannelsResponseDTO deserialized = objectMapper.readValue(json, ListChannelsResponseDTO.class);
        assertEquals(1, deserialized.getOpenChannels().size());
        assertNotNull(deserialized.getPendingChannels());
    }

    @Test
    void newAddressResponseDTO_serialization() throws Exception {
        NewAddressResponseDTO dto = new NewAddressResponseDTO();
        dto.setAddress("bc1qabc123");
        String json = objectMapper.writeValueAsString(dto);
        NewAddressResponseDTO deserialized = objectMapper.readValue(json, NewAddressResponseDTO.class);
        assertEquals("bc1qabc123", deserialized.getAddress());
    }

    @Test
    void nodeInfoDTO_serialization() throws Exception {
        NodeInfoDTO dto = NodeInfoDTO.builder()
                .pubKey("02abc123")
                .alias("aratiri-node")
                .color("#ff0000")
                .addresses(List.of("127.0.0.1:9735"))
                .capacity(10000000L)
                .numChannels(42)
                .betweennessCentrality(0.5)
                .build();
        String json = objectMapper.writeValueAsString(dto);
        assertTrue(json.contains("aratiri-node"));
        assertEquals("02abc123", dto.getPubKey());
        assertEquals(42, dto.getNumChannels());
    }

    @Test
    void nodeInfoResponseDTO_builder() {
        NodeInfoResponseDTO dto = NodeInfoResponseDTO.builder()
                .version("0.18.0")
                .commitHash("abc123")
                .identityPubkey("02abc")
                .alias("aratiri")
                .color("#ff0000")
                .numPendingChannels(1)
                .numActiveChannels(5)
                .numInactiveChannels(0)
                .numPeers(10)
                .blockHeight(800000)
                .blockHash("0000000000000000000abc")
                .syncedToChain(true)
                .syncedToGraph(true)
                .chains(List.of())
                .uris(List.of())
                .build();

        assertNotNull(dto.getVersion());
        assertEquals("0.18.0", dto.getVersion());
        assertTrue(dto.isSyncedToChain());
        assertEquals(5, dto.getNumActiveChannels());
    }

    @Test
    void nodeOperationResponseDTO_builder() throws Exception {
        NodeOperationResponseDTO dto = NodeOperationResponseDTO.builder()
                .id("op-001")
                .userId("user-1")
                .operationType("open_channel")
                .status("success")
                .referenceId("ref-1")
                .build();
        String json = objectMapper.writeValueAsString(dto);
        assertTrue(json.contains("success"));
        assertEquals("open_channel", dto.getOperationType());
    }

    @Test
    void nodeSettingsDTO_serialization() throws Exception {
        Instant now = Instant.now();
        NodeSettingsDTO dto = new NodeSettingsDTO();
        dto.setAutoManagePeers(true);
        dto.setTransactionReconciliationMinAgeMs(60000L);
        dto.setCreatedAt(now);
        dto.setUpdatedAt(now);
        String json = objectMapper.writeValueAsString(dto);
        assertTrue(json.contains("auto_manage_peers"));
        NodeSettingsDTO deserialized = objectMapper.readValue(json, NodeSettingsDTO.class);
        assertTrue(deserialized.isAutoManagePeers());
        assertEquals(60000L, deserialized.getTransactionReconciliationMinAgeMs());
    }

    @Test
    void openChannelRequestDTO_serialization() throws Exception {
        OpenChannelRequestDTO dto = new OpenChannelRequestDTO();
        dto.setNodePubkey("02abc123");
        dto.setLocalFundingAmount(1000000L);
        dto.setPushSat(100000L);
        dto.setPrivateChannel(true);
        String json = objectMapper.writeValueAsString(dto);
        OpenChannelRequestDTO deserialized = objectMapper.readValue(json, OpenChannelRequestDTO.class);
        assertEquals("02abc123", deserialized.getNodePubkey());
        assertEquals(1000000L, deserialized.getLocalFundingAmount());
        assertTrue(deserialized.isPrivateChannel());
    }

    @Test
    void peerDTO_builder() {
        PeerDTO dto = PeerDTO.builder()
                .pubKey("02abc")
                .address("127.0.0.1:9735")
                .build();
        assertEquals("02abc", dto.getPubKey());
        assertEquals("127.0.0.1:9735", dto.getAddress());
    }

    @Test
    void pendingChannelDTO_serialization() throws Exception {
        PendingChannelDTO dto = new PendingChannelDTO();
        dto.setRemoteNodePub("02abc");
        dto.setChannelPoint("abc:0");
        dto.setCapacity(1000000L);
        dto.setLocalBalance(600000L);
        dto.setRemoteBalance(400000L);
        dto.setLocalChanReserveSat(10000L);
        dto.setRemoteChanReserveSat(10000L);
        dto.setInitiator("INITIATOR_LOCAL");
        dto.setCommitmentType("ANCHORS");
        dto.setNumForwardingPackages(0);
        dto.setChanStatusFlags("");
        dto.setPrivateChannel(false);
        dto.setMemo("test channel");
        dto.setCustomChannelData("base64data");
        String json = objectMapper.writeValueAsString(dto);
        PendingChannelDTO deserialized = objectMapper.readValue(json, PendingChannelDTO.class);
        assertEquals("02abc", deserialized.getRemoteNodePub());
        assertEquals("ANCHORS", deserialized.getCommitmentType());
        assertEquals("test channel", deserialized.getMemo());
    }

    @Test
    void pendingChannelsResponseDTO_serialization() throws Exception {
        PendingChannelsResponseDTO dto = new PendingChannelsResponseDTO();
        dto.setTotalLimboBalance(50000L);
        dto.setPendingOpenChannels(List.of());
        dto.setPendingClosingChannels(List.of());
        dto.setPendingForceClosingChannels(List.of());
        dto.setWaitingCloseChannels(List.of());
        String json = objectMapper.writeValueAsString(dto);
        PendingChannelsResponseDTO deserialized = objectMapper.readValue(json, PendingChannelsResponseDTO.class);
        assertEquals(50000L, deserialized.getTotalLimboBalance());
        assertNotNull(deserialized.getPendingOpenChannels());
    }

    @Test
    void pendingHtlcDTO_serialization() throws Exception {
        PendingHtlcDTO dto = new PendingHtlcDTO();
        dto.setIncoming(true);
        dto.setAmount(1000L);
        dto.setOutpoint("abc:0");
        dto.setMaturityHeight(800000L);
        dto.setBlocksTilMaturity(40);
        dto.setStage(1);
        String json = objectMapper.writeValueAsString(dto);
        PendingHtlcDTO deserialized = objectMapper.readValue(json, PendingHtlcDTO.class);
        assertTrue(deserialized.isIncoming());
        assertEquals(1000L, deserialized.getAmount());
    }

    @Test
    void pendingOpenChannelDTO_serialization() throws Exception {
        PendingOpenChannelDTO dto = new PendingOpenChannelDTO();
        PendingChannelDTO channel = new PendingChannelDTO();
        channel.setRemoteNodePub("02abc");
        dto.setChannel(channel);
        dto.setCommitFee(1000L);
        dto.setCommitWeight(724);
        dto.setFeePerKw(253);
        dto.setFundingExpiryBlocks(2016);
        String json = objectMapper.writeValueAsString(dto);
        PendingOpenChannelDTO deserialized = objectMapper.readValue(json, PendingOpenChannelDTO.class);
        assertEquals(1000L, deserialized.getCommitFee());
        assertNotNull(deserialized.getChannel());
    }

    @Test
    void remotesResponseDTO_serialization() throws Exception {
        NodeInfoDTO node = NodeInfoDTO.builder()
                .pubKey("02abc")
                .alias("aratiri-remote")
                .build();
        RemotesResponseDTO dto = new RemotesResponseDTO(List.of(node));
        String json = objectMapper.writeValueAsString(dto);
        assertTrue(json.contains("02abc"));
        assertEquals(1, dto.getNodes().size());
    }

    @Test
    void transactionStatsDTO_serialization() throws Exception {
        TransactionStatsDTO dto = new TransactionStatsDTO(
                LocalDate.of(2024, 1, 1), "PAYMENT", BigDecimal.valueOf(1000), 42);
        String json = objectMapper.writeValueAsString(dto);
        assertTrue(json.contains("PAYMENT"));
        assertEquals(42, dto.getCount());
    }

    @Test
    void transactionStatsResponseDTO_serialization() throws Exception {
        TransactionStatsDTO stats = new TransactionStatsDTO(
                LocalDate.of(2024, 1, 1), "PAYMENT", BigDecimal.valueOf(1000), 42);

        TransactionStatsResponseDTO dto = new TransactionStatsResponseDTO(List.of(stats));
        String json = objectMapper.writeValueAsString(dto);
        assertTrue(json.contains("PAYMENT"));
        assertEquals(1, dto.getStats().size());
    }

    @Test
    void updateNodeSettingsRequestDTO_serialization() throws Exception {
        UpdateNodeSettingsRequestDTO dto = new UpdateNodeSettingsRequestDTO();
        dto.setAutoManagePeers(false);
        dto.setTransactionReconciliationMinAgeMs(30000L);
        String json = objectMapper.writeValueAsString(dto);
        assertTrue(json.contains("auto_manage_peers"));
        UpdateNodeSettingsRequestDTO deserialized = objectMapper.readValue(json, UpdateNodeSettingsRequestDTO.class);
        assertFalse(deserialized.getAutoManagePeers());
        assertEquals(Long.valueOf(30000L), deserialized.getTransactionReconciliationMinAgeMs());
    }

    @Test
    void waitingCloseChannelDTO_serialization() throws Exception {
        WaitingCloseChannelDTO dto = new WaitingCloseChannelDTO();
        PendingChannelDTO channel = new PendingChannelDTO();
        channel.setRemoteNodePub("02abc");
        dto.setChannel(channel);
        dto.setLimboBalance(50000L);
        dto.setClosingTxid("abc123");
        CommitmentsDTO commitments = new CommitmentsDTO();
        commitments.setLocalTxid("localtx");
        dto.setCommitments(commitments);
        String json = objectMapper.writeValueAsString(dto);
        WaitingCloseChannelDTO deserialized = objectMapper.readValue(json, WaitingCloseChannelDTO.class);
        assertEquals(50000L, deserialized.getLimboBalance());
        assertEquals("abc123", deserialized.getClosingTxid());
    }

    @Test
    void walletBalanceResponseDTO_serialization() throws Exception {
        WalletBalanceResponseDTO dto = new WalletBalanceResponseDTO();
        dto.setConfirmedBalance(800000L);
        dto.setUnconfirmedBalance(200000L);
        String json = objectMapper.writeValueAsString(dto);
        assertTrue(json.contains("confirmed_balance"));
        WalletBalanceResponseDTO deserialized = objectMapper.readValue(json, WalletBalanceResponseDTO.class);
        assertEquals(800000L, deserialized.getConfirmedBalance());
        assertEquals(200000L, deserialized.getUnconfirmedBalance());
    }
}
