package com.aratiri.admin.application.port.out;

import lnrpc.ChannelBalanceResponse;
import lnrpc.ChannelGraph;
import lnrpc.ChannelPoint;
import lnrpc.CloseStatusUpdate;
import lnrpc.GetInfoResponse;
import lnrpc.ListChannelsResponse;
import lnrpc.NodeMetricsResponse;
import lnrpc.PendingChannelsResponse;
import lnrpc.Peer;
import lnrpc.WalletBalanceResponse;
import lnrpc.AddressType;

import java.util.List;

public interface LightningNodeAdminPort {

    ListChannelsResponse listChannels();

    PendingChannelsResponse listPendingChannels();

    ChannelPoint openChannel(String nodePubkey, long localFundingAmount, long pushSat, boolean privateChannel);

    CloseStatusUpdate closeChannel(String fundingTxid, int outputIndex, boolean force);

    ChannelGraph describeGraph();

    NodeMetricsResponse getNodeMetrics();

    GetInfoResponse getNodeInfo();

    ChannelBalanceResponse getChannelBalance();

    WalletBalanceResponse getWalletBalance();

    String generateAddress(AddressType type);

    void connectPeer(String pubkey, String host);

    List<Peer> listPeers();
}
