package com.aratiri.aratiri.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Request payload to close an existing Lightning channel.")
public class CloseChannelRequestDTO {

    @Schema(
            description = "The channel point identifying the channel to close, in the format <fundingTxId>:<outputIndex>.",
            example = "abcd1234ef567890abcd1234ef567890abcd1234ef567890abcd1234ef567890:0"
    )
    private String channelPoint;

    @Schema(
            description = "Whether to force close the channel. " +
                    "If true, the channel is closed immediately on-chain, potentially losing pending payments. " +
                    "If false, the channel will attempt a cooperative close with the remote peer.",
            example = "false",
            defaultValue = "false"
    )
    private boolean force;
}