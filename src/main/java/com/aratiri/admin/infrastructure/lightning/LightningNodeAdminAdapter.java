package com.aratiri.admin.infrastructure.lightning;

import com.aratiri.admin.application.dto.ChainDTO;
import com.aratiri.infrastructure.grpc.GrpcDeadlines;
import com.aratiri.admin.application.dto.NodeInfoResponseDTO;
import com.aratiri.admin.application.dto.PeerDTO;
import com.aratiri.admin.application.port.out.LightningNodeAdminPort;
import com.google.protobuf.ByteString;
import lnrpc.*;
import org.springframework.stereotype.Component;

import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class LightningNodeAdminAdapter implements LightningNodeAdminPort {

    private final LightningGrpc.LightningBlockingStub lightningStub;

    public LightningNodeAdminAdapter(LightningGrpc.LightningBlockingStub lightningStub) {
        this.lightningStub = lightningStub;
    }

    private LightningGrpc.LightningBlockingStub deadlined() {
        return lightningStub.withDeadlineAfter(GrpcDeadlines.ADMIN.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public ListChannelsResponse listChannels() {
        return deadlined().listChannels(ListChannelsRequest.newBuilder().build());
    }

    @Override
    public PendingChannelsResponse listPendingChannels() {
        return deadlined().pendingChannels(PendingChannelsRequest.newBuilder().build());
    }

    @Override
    public ChannelPoint openChannel(String nodePubkey, long localFundingAmount, long pushSat, boolean privateChannel) {
        byte[] pubkeyBytes = HexFormat.of().parseHex(nodePubkey);
        OpenChannelRequest openChannelRequest = OpenChannelRequest.newBuilder()
                .setNodePubkey(ByteString.copyFrom(pubkeyBytes))
                .setLocalFundingAmount(localFundingAmount)
                .setPushSat(pushSat)
                .setPrivate(privateChannel)
                .build();
        return deadlined().openChannelSync(openChannelRequest);
    }

    @Override
    public CloseStatusUpdate closeChannel(String fundingTxid, int outputIndex, boolean force) {
        ChannelPoint channelPoint = ChannelPoint.newBuilder()
                .setFundingTxidStr(fundingTxid)
                .setOutputIndex(outputIndex)
                .build();

        CloseChannelRequest closeChannelRequest = CloseChannelRequest.newBuilder()
                .setChannelPoint(channelPoint)
                .setForce(force)
                .build();
        return deadlined().closeChannel(closeChannelRequest).next();
    }

    @Override
    public ChannelGraph describeGraph() {
        return deadlined().describeGraph(ChannelGraphRequest.newBuilder().build());
    }

    @Override
    public NodeMetricsResponse getNodeMetrics() {
        NodeMetricsRequest request = NodeMetricsRequest.newBuilder()
                .addTypes(NodeMetricType.BETWEENNESS_CENTRALITY)
                .build();
        return deadlined().getNodeMetrics(request);
    }

    @Override
    public NodeInfoResponseDTO getNodeInfo() {
        GetInfoResponse info = deadlined().getInfo(GetInfoRequest.newBuilder().build());
        return toNodeInfoResponseDTO(info);
    }

    @Override
    public ChannelBalanceResponse getChannelBalance() {
        return deadlined().channelBalance(ChannelBalanceRequest.newBuilder().build());
    }

    @Override
    public WalletBalanceResponse getWalletBalance() {
        return deadlined().walletBalance(WalletBalanceRequest.newBuilder().build());
    }

    @Override
    public String generateAddress(AddressType type) {
        NewAddressRequest request = NewAddressRequest.newBuilder()
                .setType(type)
                .build();
        return deadlined().newAddress(request).getAddress();
    }

    @Override
    public void connectPeer(String pubkey, String host) {
        ConnectPeerRequest request = ConnectPeerRequest.newBuilder()
                .setAddr(LightningAddress.newBuilder().setPubkey(pubkey).setHost(host).build())
                .build();
        deadlined().connectPeer(request);
    }

    @Override
    public List<PeerDTO> listPeers() {
        return deadlined().listPeers(ListPeersRequest.newBuilder().build()).getPeersList().stream()
                .map(this::toPeerDTO)
                .toList();
    }

    private PeerDTO toPeerDTO(Peer peer) {
        return PeerDTO.builder()
                .pubKey(peer.getPubKey())
                .address(peer.getAddress())
                .build();
    }

    private NodeInfoResponseDTO toNodeInfoResponseDTO(GetInfoResponse info) {
        // lnrpc.Chain#chain is deprecated upstream (bitcoin is implied); expose a stable value for API clients.
        List<ChainDTO> chains = info.getChainsList().stream()
                .map(chain -> new ChainDTO("bitcoin", chain.getNetwork()))
                .toList();
        return NodeInfoResponseDTO.builder()
                .version(info.getVersion())
                .commitHash(info.getCommitHash())
                .identityPubkey(info.getIdentityPubkey())
                .alias(info.getAlias())
                .color(info.getColor())
                .numPendingChannels(info.getNumPendingChannels())
                .numActiveChannels(info.getNumActiveChannels())
                .numInactiveChannels(info.getNumInactiveChannels())
                .numPeers(info.getNumPeers())
                .blockHeight(info.getBlockHeight())
                .blockHash(info.getBlockHash())
                .syncedToChain(info.getSyncedToChain())
                .syncedToGraph(info.getSyncedToGraph())
                .chains(chains)
                .uris(info.getUrisList())
                .build();
    }
}
