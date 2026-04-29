package com.aratiri.admin.application;

import com.aratiri.admin.application.dto.ChainDTO;
import com.aratiri.admin.application.dto.NodeInfoResponseDTO;
import com.aratiri.admin.application.dto.PeerDTO;
import com.aratiri.admin.application.port.out.LightningNodeAdminPort;
import com.aratiri.admin.application.port.out.NodeSettingsPort;
import com.aratiri.admin.application.port.out.TransactionStatsPort;
import com.aratiri.infrastructure.persistence.jpa.repository.NodeOperationsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

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
}
