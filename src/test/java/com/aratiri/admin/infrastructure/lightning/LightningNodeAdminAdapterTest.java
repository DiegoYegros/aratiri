package com.aratiri.admin.infrastructure.lightning;

import com.aratiri.admin.application.dto.NodeInfoResponseDTO;
import com.aratiri.admin.application.dto.PeerDTO;
import lnrpc.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Iterator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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

    @Test
    void listChannels_callsLightningStub() {
        when(lightningStub.listChannels(any(ListChannelsRequest.class)))
                .thenReturn(ListChannelsResponse.newBuilder().build());

        ListChannelsResponse result = adapter.listChannels();

        assertNotNull(result);
    }

    @Test
    void listPendingChannels_callsLightningStub() {
        when(lightningStub.pendingChannels(any(PendingChannelsRequest.class)))
                .thenReturn(PendingChannelsResponse.newBuilder().build());

        PendingChannelsResponse result = adapter.listPendingChannels();

        assertNotNull(result);
    }

    @Test
    void openChannel_callsLightningStub() {
        ChannelPoint mockChannelPoint = ChannelPoint.newBuilder()
                .setFundingTxidStr("deadbeef")
                .build();
        when(lightningStub.openChannelSync(any(OpenChannelRequest.class)))
                .thenReturn(mockChannelPoint);

        ChannelPoint result = adapter.openChannel(
                "02aabbccddeeff00112233445566778899aabbccddeeff001122334455667788",
                1000000L, 100000L, true);

        assertEquals("deadbeef", result.getFundingTxidStr());
    }

    @Test
    @SuppressWarnings("unchecked")
    void closeChannel_callsLightningStub() {
        CloseStatusUpdate mockUpdate = CloseStatusUpdate.newBuilder().build();
        Iterator<CloseStatusUpdate> iterator = mock(Iterator.class);
        when(iterator.next()).thenReturn(mockUpdate);
        when(lightningStub.closeChannel(any(CloseChannelRequest.class))).thenReturn(iterator);

        CloseStatusUpdate result = adapter.closeChannel("deadbeef", 0, false);

        assertNotNull(result);
    }

    @Test
    void describeGraph_callsLightningStub() {
        when(lightningStub.describeGraph(any(ChannelGraphRequest.class)))
                .thenReturn(ChannelGraph.newBuilder().build());

        ChannelGraph result = adapter.describeGraph();

        assertNotNull(result);
    }

    @Test
    void getNodeMetrics_callsLightningStub() {
        when(lightningStub.getNodeMetrics(any(NodeMetricsRequest.class)))
                .thenReturn(NodeMetricsResponse.newBuilder().build());

        NodeMetricsResponse result = adapter.getNodeMetrics();

        assertNotNull(result);
    }

    @Test
    void getChannelBalance_callsLightningStub() {
        when(lightningStub.channelBalance(any(ChannelBalanceRequest.class)))
                .thenReturn(ChannelBalanceResponse.newBuilder().build());

        ChannelBalanceResponse result = adapter.getChannelBalance();

        assertNotNull(result);
    }

    @Test
    void getWalletBalance_callsLightningStub() {
        when(lightningStub.walletBalance(any(WalletBalanceRequest.class)))
                .thenReturn(WalletBalanceResponse.newBuilder().build());

        WalletBalanceResponse result = adapter.getWalletBalance();

        assertNotNull(result);
    }

    @Test
    void generateAddress_callsLightningStub() {
        NewAddressResponse mockResponse = NewAddressResponse.newBuilder()
                .setAddress("bc1ptaprootaddress")
                .build();
        when(lightningStub.newAddress(any(NewAddressRequest.class)))
                .thenReturn(mockResponse);

        String result = adapter.generateAddress(AddressType.TAPROOT_PUBKEY);

        assertEquals("bc1ptaprootaddress", result);
    }

    @Test
    void connectPeer_callsLightningStub() {
        when(lightningStub.connectPeer(any(ConnectPeerRequest.class)))
                .thenReturn(ConnectPeerResponse.newBuilder().build());

        assertDoesNotThrow(() -> adapter.connectPeer("02abc", "127.0.0.1:9735"));
    }
}
