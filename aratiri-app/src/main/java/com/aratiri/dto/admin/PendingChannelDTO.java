package com.aratiri.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lnrpc.PendingChannelsResponse;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Base64;

@Getter
@Setter
@ToString
@NoArgsConstructor
@Schema(description = "Common metadata shared by all pending channel states.")
public class PendingChannelDTO {

    @Schema(description = "Public key of the remote node associated with the pending channel.")
    private String remoteNodePub;

    @Schema(description = "Funding outpoint identifying the channel, formatted as <fundingTxId>:<outputIndex>.")
    private String channelPoint;

    @Schema(description = "Total capacity of the channel in satoshis.")
    private long capacity;

    @Schema(description = "Local balance in satoshis that belongs to this node.")
    private long localBalance;

    @Schema(description = "Remote balance in satoshis that belongs to the channel peer.")
    private long remoteBalance;

    @Schema(description = "Reserved local balance required for this channel in satoshis.")
    private long localChanReserveSat;

    @Schema(description = "Reserved remote balance required for the peer in satoshis.")
    private long remoteChanReserveSat;

    @Schema(description = "Indicates which party initiated the channel.")
    private String initiator;

    @Schema(description = "Commitment type that the pending channel will use once active.")
    private String commitmentType;

    @Schema(description = "Total number of forwarding packages created for this channel.")
    private long numForwardingPackages;

    @Schema(description = "Channel status flags reported by LND.")
    private String chanStatusFlags;

    @Schema(description = "Whether the channel is private.")
    private boolean privateChannel;

    @Schema(description = "Optional note-to-self stored locally for the channel.")
    private String memo;

    @Schema(description = "Custom channel data encoded as a base64 string if present.")
    private String customChannelData;

    public PendingChannelDTO(PendingChannelsResponse.PendingChannel channel) {
        this.remoteNodePub = channel.getRemoteNodePub();
        this.channelPoint = channel.getChannelPoint();
        this.capacity = channel.getCapacity();
        this.localBalance = channel.getLocalBalance();
        this.remoteBalance = channel.getRemoteBalance();
        this.localChanReserveSat = channel.getLocalChanReserveSat();
        this.remoteChanReserveSat = channel.getRemoteChanReserveSat();
        this.initiator = channel.getInitiator().name();
        this.commitmentType = channel.getCommitmentType().name();
        this.numForwardingPackages = channel.getNumForwardingPackages();
        this.chanStatusFlags = channel.getChanStatusFlags();
        this.privateChannel = channel.getPrivate();
        this.memo = channel.getMemo();
        if (!channel.getCustomChannelData().isEmpty()) {
            this.customChannelData = Base64.getEncoder().encodeToString(channel.getCustomChannelData().toByteArray());
        }
    }
}
