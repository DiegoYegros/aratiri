package com.aratiri.admin.infrastructure.lightning;

import com.aratiri.admin.application.dto.NodeInfoResponseDTO;
import com.aratiri.admin.application.dto.PeerDTO;
import lnrpc.Chain;
import lnrpc.GetInfoRequest;
import lnrpc.GetInfoResponse;
import lnrpc.LightningGrpc;
import lnrpc.ListPeersRequest;
import lnrpc.ListPeersResponse;
import lnrpc.Peer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LightningNodeAdminAdapterTest {

    @Mock
    private LightningGrpc.LightningBlockingStub lightningStub;

    private LightningNodeAdminAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new LightningNodeAdminAdapter(lightningStub);
    }

    @Test
    void listPeers_mapsLndPeersToAratiriDtos() {
        when(lightningStub.listPeers(any(ListPeersRequest.class))).thenReturn(ListPeersResponse.newBuilder()
                .addPeers(Peer.newBuilder()
                        .setPubKey("peer-pubkey")
                        .setAddress("203.0.113.10:9735")
                        .build())
                .build());

        List<PeerDTO> result = adapter.listPeers();

        assertEquals(1, result.size());
        assertEquals("peer-pubkey", result.getFirst().getPubKey());
        assertEquals("203.0.113.10:9735", result.getFirst().getAddress());
    }

    @Test
    void getNodeInfo_mapsLndInfoToStableAratiriResponse() {
        when(lightningStub.getInfo(any(GetInfoRequest.class))).thenReturn(GetInfoResponse.newBuilder()
                .setVersion("0.18.0-beta")
                .setCommitHash("commit")
                .setIdentityPubkey("identity")
                .setAlias("aratiri")
                .setColor("#3399ff")
                .setNumPendingChannels(1)
                .setNumActiveChannels(2)
                .setNumInactiveChannels(3)
                .setNumPeers(4)
                .setBlockHeight(850_000)
                .setBlockHash("block-hash")
                .setSyncedToChain(true)
                .setSyncedToGraph(false)
                .addChains(Chain.newBuilder()
                        .setNetwork("mainnet")
                        .build())
                .addUris("identity@example.com:9735")
                .build());

        NodeInfoResponseDTO result = adapter.getNodeInfo();

        assertEquals("0.18.0-beta", result.getVersion());
        assertEquals("commit", result.getCommitHash());
        assertEquals("identity", result.getIdentityPubkey());
        assertEquals("aratiri", result.getAlias());
        assertEquals("#3399ff", result.getColor());
        assertEquals(1, result.getNumPendingChannels());
        assertEquals(2, result.getNumActiveChannels());
        assertEquals(3, result.getNumInactiveChannels());
        assertEquals(4, result.getNumPeers());
        assertEquals(850_000, result.getBlockHeight());
        assertEquals("block-hash", result.getBlockHash());
        assertTrue(result.isSyncedToChain());
        assertFalse(result.isSyncedToGraph());
        assertEquals("bitcoin", result.getChains().getFirst().getChain());
        assertEquals("mainnet", result.getChains().getFirst().getNetwork());
        assertEquals(List.of("identity@example.com:9735"), result.getUris());
    }
}
