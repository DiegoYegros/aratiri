package com.aratiri.admin.application.dto;

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
@Schema(description = "Information about a channel that is in the process of cooperatively closing.")
public class ClosedChannelDTO {

    @Schema(description = "Base pending channel metadata.")
    private PendingChannelDTO channel;

    @Schema(description = "Transaction id of the closing transaction.")
    private String closingTxid;

    public ClosedChannelDTO(PendingChannelsResponse.ClosedChannel closedChannel) {
        this.channel = new PendingChannelDTO(closedChannel.getChannel());
        this.closingTxid = closedChannel.getClosingTxid();
    }
}
