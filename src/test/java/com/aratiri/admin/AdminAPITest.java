package com.aratiri.admin;

import com.aratiri.admin.application.dto.*;
import com.aratiri.admin.application.port.in.AdminPort;
import com.aratiri.infrastructure.persistence.jpa.entity.NodeOperationStatus;
import com.aratiri.infrastructure.persistence.jpa.entity.NodeOperationType;
import com.aratiri.shared.exception.AratiriException;
import lnrpc.CloseStatusUpdate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminAPITest {

    @Mock
    private AdminPort adminPort;

    private AdminAPI adminAPI;

    @BeforeEach
    void setUp() {
        adminAPI = new AdminAPI(adminPort);
    }

    @Test
    void connectPeer() {
        ConnectPeerRequestDTO request = new ConnectPeerRequestDTO();
        request.setPubkey("02abc");
        request.setHost("127.0.0.1:9735");

        ResponseEntity<Void> response = adminAPI.connectPeer(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(adminPort).connectPeer(request);
    }

    @Test
    void listPeers() {
        PeerDTO peer = PeerDTO.builder().pubKey("02abc").address("127.0.0.1:9735").build();
        when(adminPort.listPeers()).thenReturn(List.of(peer));

        ResponseEntity<List<PeerDTO>> response = adminAPI.listPeers();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1, response.getBody().size());
        assertEquals("02abc", response.getBody().getFirst().getPubKey());
    }

    @Test
    void getNodeInfo() {
        NodeInfoResponseDTO dto = NodeInfoResponseDTO.builder()
                .version("0.18.0")
                .alias("test-node")
                .identityPubkey("02abc")
                .syncedToChain(true)
                .build();
        when(adminPort.getNodeInfo()).thenReturn(dto);

        ResponseEntity<NodeInfoResponseDTO> response = adminAPI.getNodeInfo();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("test-node", response.getBody().getAlias());
        assertEquals("0.18.0", response.getBody().getVersion());
    }

    @Test
    void listChannels() {
        ListChannelsResponseDTO dto = new ListChannelsResponseDTO();
        dto.setOpenChannels(List.of());
        dto.setPendingChannels(new PendingChannelsResponseDTO());
        when(adminPort.listChannels()).thenReturn(dto);

        ResponseEntity<ListChannelsResponseDTO> response = adminAPI.listChannels();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void openChannel() {
        OpenChannelRequestDTO request = new OpenChannelRequestDTO();
        request.setNodePubkey("02abc");
        request.setLocalFundingAmount(1000000L);
        when(adminPort.openChannel(any())).thenReturn("txid-abc");

        ResponseEntity<String> response = adminAPI.openChannel(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("txid-abc", response.getBody());
        verify(adminPort).openChannel(request);
    }

    @Test
    void closeChannel() {
        CloseChannelRequestDTO request = new CloseChannelRequestDTO();
        request.setChannelPoint("abc:0");
        CloseStatusUpdate status = CloseStatusUpdate.getDefaultInstance();
        when(adminPort.closeChannel(any())).thenReturn(status);

        ResponseEntity<CloseStatusUpdate> response = adminAPI.closeChannel(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(adminPort).closeChannel(request);
    }

    @Test
    void listNodes() {
        when(adminPort.listNodes()).thenReturn(new RemotesResponseDTO(List.of()));

        ResponseEntity<RemotesResponseDTO> response = adminAPI.listNodes();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    void channelBalance() {
        ChannelBalanceResponseDTO dto = new ChannelBalanceResponseDTO();
        AmountDTO local = new AmountDTO();
        local.setSat(500000L);
        dto.setLocalBalance(local);
        when(adminPort.getChannelBalance()).thenReturn(dto);

        ResponseEntity<ChannelBalanceResponseDTO> response = adminAPI.getChannelBalance();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(500000L, response.getBody().getLocalBalance().getSat());
    }

    @Test
    void walletBalance() {
        WalletBalanceResponseDTO dto = new WalletBalanceResponseDTO();
        dto.setConfirmedBalance(1000000L);
        when(adminPort.getWalletBalance()).thenReturn(dto);

        ResponseEntity<WalletBalanceResponseDTO> response = adminAPI.getWalletBalance();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1000000L, response.getBody().getConfirmedBalance());
    }

    @Test
    void generateTaprootAddress() {
        NewAddressResponseDTO dto = new NewAddressResponseDTO();
        dto.setAddress("bc1qabc");
        when(adminPort.generateTaprootAddress()).thenReturn(dto);

        ResponseEntity<NewAddressResponseDTO> response = adminAPI.generateTaprootAddress();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("bc1qabc", response.getBody().getAddress());
    }

    @Test
    void transactionStats() {
        TransactionStatsResponseDTO dto = new TransactionStatsResponseDTO(List.of());
        when(adminPort.getTransactionStats(any(), any())).thenReturn(dto);

        LocalDate from = LocalDate.of(2024, 1, 1);
        LocalDate to = LocalDate.of(2024, 1, 31);

        ResponseEntity<TransactionStatsResponseDTO> response = adminAPI.getTransactionStats(from, to);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(adminPort).getTransactionStats(any(), any());
    }

    @Test
    void getNodeSettings() {
        NodeSettingsDTO dto = new NodeSettingsDTO();
        dto.setAutoManagePeers(true);
        dto.setCreatedAt(Instant.now());
        dto.setUpdatedAt(Instant.now());
        when(adminPort.getNodeSettings()).thenReturn(dto);

        ResponseEntity<NodeSettingsDTO> response = adminAPI.getNodeSettings();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isAutoManagePeers());
    }

    @Test
    void updateSettings() {
        UpdateNodeSettingsRequestDTO request = new UpdateNodeSettingsRequestDTO();
        request.setAutoManagePeers(false);
        NodeSettingsDTO dto = new NodeSettingsDTO();
        when(adminPort.updateNodeSettings(any())).thenReturn(dto);

        ResponseEntity<NodeSettingsDTO> response = adminAPI.updateSettings(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(adminPort).updateNodeSettings(request);
    }

    @Test
    void updateAutoManagePeers_success() {
        NodeSettingsDTO dto = new NodeSettingsDTO();
        dto.setAutoManagePeers(false);
        when(adminPort.updateAutoManagePeers(false)).thenReturn(dto);

        ResponseEntity<NodeSettingsDTO> response = adminAPI.updateAutoManagePeers(Map.of("enabled", false));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertFalse(response.getBody().isAutoManagePeers());
    }

    @Test
    void updateAutoManagePeers_successTrue() {
        NodeSettingsDTO dto = new NodeSettingsDTO();
        dto.setAutoManagePeers(true);
        when(adminPort.updateAutoManagePeers(true)).thenReturn(dto);

        ResponseEntity<NodeSettingsDTO> response = adminAPI.updateAutoManagePeers(Map.of("enabled", true));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().isAutoManagePeers());
    }

    @Test
    void updateAutoManagePeers_missingEnabled() {
        Map<String, Boolean> payload = Map.of();

        assertThrows(AratiriException.class, () -> adminAPI.updateAutoManagePeers(payload));
    }

    @Test
    void updateAutoManagePeers_nullEnabled() {
        Map<String, Boolean> payload = new java.util.HashMap<>();
        payload.put("enabled", null);
        assertThrows(AratiriException.class, () -> adminAPI.updateAutoManagePeers(payload));
    }

    @Test
    void listNodeOperations_withAllFilters() {
        when(adminPort.listNodeOperations(NodeOperationStatus.PENDING, NodeOperationType.LIGHTNING_PAYMENT, "tx-1", 50))
                .thenReturn(List.of());

        ResponseEntity<List<NodeOperationResponseDTO>> response = adminAPI.listNodeOperations(
                NodeOperationStatus.PENDING, NodeOperationType.LIGHTNING_PAYMENT, "tx-1", 50);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(0, response.getBody().size());
    }
}
