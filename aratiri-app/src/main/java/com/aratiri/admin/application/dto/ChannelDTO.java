package com.aratiri.admin.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Represents a Lightning channel with key information about balances and state.")
public class ChannelDTO {

    @Schema(
            description = "The channel point identifying the channel, in the format <fundingTxId>:<outputIndex>.",
            example = "abcd1234ef567890abcd1234ef567890abcd1234ef567890abcd1234ef567890:0"
    )
    private String channelPoint;

    @Schema(
            description = "Public key of the remote node that this channel is connected to.",
            example = "032c1234abcd5678ef90abcdef1234567890abcdef1234567890abcdef1234567890"
    )
    private String remotePubkey;

    @Schema(
            description = "The total capacity of the channel in satoshis.",
            example = "1000000"
    )
    private long capacity;

    @Schema(
            description = "Local balance in satoshis (funds owned by this node).",
            example = "900000"
    )
    private long localBalance;

    @Schema(
            description = "Remote balance in satoshis (funds owned by the remote node).",
            example = "100000"
    )
    private long remoteBalance;

    @Schema(
            description = "Whether the channel is active and can route payments.",
            example = "true"
    )
    private boolean active;

    @Schema(
            description = "Whether the channel is private (not announced to the network).",
            example = "false"
    )
    private boolean privateChannel;
}