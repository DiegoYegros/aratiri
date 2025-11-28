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
@Schema(description = "Aggregated representation of pending Lightning channels grouped by their lifecycle stage.")
public class PendingChannelsResponseDTO {

    @Schema(description = "Total balance in satoshis currently locked across all pending channels.")
    private long totalLimboBalance;

    @Schema(description = "Channels that are in the process of being opened.")
    private List<PendingOpenChannelDTO> pendingOpenChannels;

    @Schema(description = "Channels that are in a cooperative close flow but awaiting on-chain confirmation. Deprecated upstream but included for completeness.")
    private List<ClosedChannelDTO> pendingClosingChannels;

    @Schema(description = "Channels that were force closed and are waiting for funds to mature.")
    private List<ForceClosedChannelDTO> pendingForceClosingChannels;

    @Schema(description = "Channels that are cooperatively closing and waiting for the closing transaction to confirm.")
    private List<WaitingCloseChannelDTO> waitingCloseChannels;

    public PendingChannelsResponseDTO(PendingChannelsResponse pendingChannels) {
        this.totalLimboBalance = pendingChannels.getTotalLimboBalance();
        this.pendingOpenChannels = pendingChannels.getPendingOpenChannelsList().stream()
                .map(PendingOpenChannelDTO::new)
                .collect(Collectors.toList());
        this.pendingClosingChannels = pendingChannels.getPendingClosingChannelsList().stream()
                .map(ClosedChannelDTO::new)
                .collect(Collectors.toList());
        this.pendingForceClosingChannels = pendingChannels.getPendingForceClosingChannelsList().stream()
                .map(ForceClosedChannelDTO::new)
                .collect(Collectors.toList());
        this.waitingCloseChannels = pendingChannels.getWaitingCloseChannelsList().stream()
                .map(WaitingCloseChannelDTO::new)
                .collect(Collectors.toList());
    }
}
