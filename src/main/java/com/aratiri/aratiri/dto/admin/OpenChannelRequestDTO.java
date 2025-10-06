package com.aratiri.aratiri.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Request payload to open a new Lightning channel with a remote node.")
public class OpenChannelRequestDTO {

    @Schema(
            description = "Public key of the remote node to open the channel with.",
            example = "032c1234abcd5678ef90abcdef1234567890abcdef1234567890abcdef1234567890"
    )
    private String nodePubkey;

    @Schema(
            description = "Amount (in satoshis) you are funding into the channel. " +
                    "This determines the total capacity of the channel on-chain.",
            example = "1000000"
    )
    private long localFundingAmount;

    @Schema(
            description = "Amount (in satoshis) to push to the remote peer at channel creation. " +
                    "This amount is immediately credited to the remote side's balance, " +
                    "creating inbound liquidity for you. " +
                    "For example, setting pushSat=100000 on a 1,000,000 sat channel gives " +
                    "the remote node 100k sats and leaves you with 900k sats locally.",
            example = "100000",
            defaultValue = "0"
    )
    private long pushSat;

    @Schema(
            description = "Whether the channel should be private (not announced to the network). " +
                    "Private channels are only usable between you and the remote peer.",
            example = "false"
    )
    private boolean privateChannel;
}
