package com.aratiri.admin.infrastructure.lightning;

import com.aratiri.admin.application.port.out.LightningNodeAdminPort;
import com.google.protobuf.ByteString;
import lnrpc.ChannelBalanceRequest;
import lnrpc.ChannelBalanceResponse;
import lnrpc.ChannelGraph;
import lnrpc.ChannelGraphRequest;
import lnrpc.ChannelPoint;
import lnrpc.CloseChannelRequest;
import lnrpc.CloseStatusUpdate;
import lnrpc.ConnectPeerRequest;
import lnrpc.GetInfoRequest;
import lnrpc.GetInfoResponse;
import lnrpc.LightningAddress;
import lnrpc.LightningGrpc;
import lnrpc.ListChannelsRequest;
import lnrpc.ListChannelsResponse;
import lnrpc.ListPeersRequest;
import lnrpc.NodeMetricType;
import lnrpc.NodeMetricsRequest;
import lnrpc.NodeMetricsResponse;
import lnrpc.OpenChannelRequest;
import lnrpc.PendingChannelsRequest;
import lnrpc.PendingChannelsResponse;
import lnrpc.Peer;
import org.springframework.stereotype.Component;

import java.util.HexFormat;
import java.util.List;

@Component
public class LightningNodeAdminAdapter implements LightningNodeAdminPort {

    private final LightningGrpc.LightningBlockingStub lightningStub;

    public LightningNodeAdminAdapter(LightningGrpc.LightningBlockingStub lightningStub) {
        this.lightningStub = lightningStub;
    }

    @Override
    public ListChannelsResponse listChannels() {
        return lightningStub.listChannels(ListChannelsRequest.newBuilder().build());
    }

    @Override
    public PendingChannelsResponse listPendingChannels() {
        return lightningStub.pendingChannels(PendingChannelsRequest.newBuilder().build());
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
        return lightningStub.openChannelSync(openChannelRequest);
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
        return lightningStub.closeChannel(closeChannelRequest).next();
    }

    @Override
    public ChannelGraph describeGraph() {
        return lightningStub.describeGraph(ChannelGraphRequest.newBuilder().build());
    }

    @Override
    public NodeMetricsResponse getNodeMetrics() {
        NodeMetricsRequest request = NodeMetricsRequest.newBuilder()
                .addTypes(NodeMetricType.BETWEENNESS_CENTRALITY)
                .build();
        return lightningStub.getNodeMetrics(request);
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
    public void connectPeer(String pubkey, String host) {
        ConnectPeerRequest request = ConnectPeerRequest.newBuilder()
                .setAddr(LightningAddress.newBuilder().setPubkey(pubkey).setHost(host).build())
                .build();
        lightningStub.connectPeer(request);
    }

    @Override
    public List<Peer> listPeers() {
        return lightningStub.listPeers(ListPeersRequest.newBuilder().build()).getPeersList();
    }
}
