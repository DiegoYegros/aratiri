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
@Schema(description = "Channel that is cooperatively closing and waiting for the closing transaction to confirm.")
public class WaitingCloseChannelDTO {

    @Schema(description = "Base pending channel metadata.")
    private PendingChannelDTO channel;

    @Schema(description = "Balance in satoshis currently limbo in this channel.")
    private long limboBalance;

    @Schema(description = "Commitment transactions associated with the channel while waiting to close.")
    private CommitmentsDTO commitments;

    @Schema(description = "Transaction id of the closing transaction.")
    private String closingTxid;

    @Schema(description = "Raw hex of the closing transaction when requested.")
    private String closingTxHex;

    public WaitingCloseChannelDTO(PendingChannelsResponse.WaitingCloseChannel waitingCloseChannel) {
        this.channel = new PendingChannelDTO(waitingCloseChannel.getChannel());
        this.limboBalance = waitingCloseChannel.getLimboBalance();
        if (waitingCloseChannel.hasCommitments()) {
            this.commitments = new CommitmentsDTO(waitingCloseChannel.getCommitments());
        }
        this.closingTxid = waitingCloseChannel.getClosingTxid();
        this.closingTxHex = waitingCloseChannel.getClosingTxHex();
    }
}
