package com.aratiri.admin.application.port.out;

import com.aratiri.admin.application.dto.NodeInfoResponseDTO;
import com.aratiri.admin.application.dto.PeerDTO;
import lnrpc.*;

import java.util.List;

public interface LightningNodeAdminPort {

    ListChannelsResponse listChannels();

    PendingChannelsResponse listPendingChannels();

    ChannelPoint openChannel(String nodePubkey, long localFundingAmount, long pushSat, boolean privateChannel);

    CloseStatusUpdate closeChannel(String fundingTxid, int outputIndex, boolean force);

    ChannelGraph describeGraph();

    NodeMetricsResponse getNodeMetrics();

    NodeInfoResponseDTO getNodeInfo();

    ChannelBalanceResponse getChannelBalance();

    WalletBalanceResponse getWalletBalance();

    String generateAddress(AddressType type);

    void connectPeer(String pubkey, String host);

    List<PeerDTO> listPeers();
}
