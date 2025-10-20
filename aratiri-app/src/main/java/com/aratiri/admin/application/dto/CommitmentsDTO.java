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
@Schema(description = "Set of commitment transactions associated with a waiting close channel.")
public class CommitmentsDTO {

    @Schema(description = "Transaction id of the local commitment transaction.")
    private String localTxid;

    @Schema(description = "Transaction id of the remote commitment transaction.")
    private String remoteTxid;

    @Schema(description = "Transaction id of the pending remote commitment transaction.")
    private String remotePendingTxid;

    @Schema(description = "Fee in satoshis paid for the local commitment transaction.")
    private long localCommitFeeSat;

    @Schema(description = "Fee in satoshis paid for the remote commitment transaction.")
    private long remoteCommitFeeSat;

    @Schema(description = "Fee in satoshis paid for the remote pending commitment transaction.")
    private long remotePendingCommitFeeSat;

    public CommitmentsDTO(PendingChannelsResponse.Commitments commitments) {
        this.localTxid = commitments.getLocalTxid();
        this.remoteTxid = commitments.getRemoteTxid();
        this.remotePendingTxid = commitments.getRemotePendingTxid();
        this.localCommitFeeSat = commitments.getLocalCommitFeeSat();
        this.remoteCommitFeeSat = commitments.getRemoteCommitFeeSat();
        this.remotePendingCommitFeeSat = commitments.getRemotePendingCommitFeeSat();
    }
}
