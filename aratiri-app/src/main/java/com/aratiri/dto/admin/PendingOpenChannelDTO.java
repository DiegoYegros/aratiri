package com.aratiri.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lnrpc.PendingChannelsResponse;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
@Schema(description = "Details about a channel that is in the process of being opened.")
public class PendingOpenChannelDTO {

    @Schema(description = "Base pending channel metadata.")
    private PendingChannelDTO channel;

    @Schema(description = "Amount, in satoshis, currently allocated to commitment transaction fees.")
    private long commitFee;

    @Schema(description = "Weight of the commitment transaction in weight units.")
    private long commitWeight;

    @Schema(description = "Fee rate in satoshis per kilo-weight that will be paid for the channel.")
    private long feePerKw;

    @Schema(description = "Number of blocks remaining before the funding transaction expires.")
    private int fundingExpiryBlocks;

    public PendingOpenChannelDTO(PendingChannelsResponse.PendingOpenChannel pendingOpenChannel) {
        this.channel = new PendingChannelDTO(pendingOpenChannel.getChannel());
        this.commitFee = pendingOpenChannel.getCommitFee();
        this.commitWeight = pendingOpenChannel.getCommitWeight();
        this.feePerKw = pendingOpenChannel.getFeePerKw();
        this.fundingExpiryBlocks = pendingOpenChannel.getFundingExpiryBlocks();
    }
}
