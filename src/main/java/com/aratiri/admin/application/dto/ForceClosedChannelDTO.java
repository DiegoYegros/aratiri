package com.aratiri.admin.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lnrpc.PendingChannelsResponse;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.List;
import java.util.stream.Collectors;

@Getter
@Setter
@ToString
@NoArgsConstructor
@Schema(description = "Details about a channel that has been force closed and is awaiting resolution.")
public class ForceClosedChannelDTO {

    @Schema(description = "Base pending channel metadata.")
    private PendingChannelDTO channel;

    @Schema(description = "Transaction id of the force close transaction.")
    private String closingTxid;

    @Schema(description = "Balance in satoshis currently locked in limbo for this channel.")
    private long limboBalance;

    @Schema(description = "Block height at which funds can be swept into the wallet.")
    private long maturityHeight;

    @Schema(description = "Remaining number of blocks until the commitment output can be swept. Negative means matured.")
    private int blocksTilMaturity;

    @Schema(description = "Total value of funds that have already been recovered from this channel in satoshis.")
    private long recoveredBalance;

    @Schema(description = "Pending HTLCs tied to this force closed channel.")
    private List<PendingHtlcDTO> pendingHtlcs;

    @Schema(description = "State of the anchor output if present.")
    private String anchor;

    public ForceClosedChannelDTO(PendingChannelsResponse.ForceClosedChannel forceClosedChannel) {
        this.channel = new PendingChannelDTO(forceClosedChannel.getChannel());
        this.closingTxid = forceClosedChannel.getClosingTxid();
        this.limboBalance = forceClosedChannel.getLimboBalance();
        this.maturityHeight = forceClosedChannel.getMaturityHeight();
        this.blocksTilMaturity = forceClosedChannel.getBlocksTilMaturity();
        this.recoveredBalance = forceClosedChannel.getRecoveredBalance();
        this.pendingHtlcs = forceClosedChannel.getPendingHtlcsList().stream()
                .map(PendingHtlcDTO::new)
                .collect(Collectors.toList());
        this.anchor = forceClosedChannel.getAnchor().name();
    }
}
