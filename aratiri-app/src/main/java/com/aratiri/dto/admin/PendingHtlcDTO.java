package com.aratiri.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lnrpc.PendingHTLC;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@NoArgsConstructor
@Schema(description = "HTLC that is still pending resolution within a force closed channel.")
public class PendingHtlcDTO {

    @Schema(description = "Whether the HTLC was incoming through the channel.")
    private boolean incoming;

    @Schema(description = "Total value of the HTLC in satoshis.")
    private long amount;

    @Schema(description = "Outpoint that will be swept back to the wallet once resolved.")
    private String outpoint;

    @Schema(description = "Block height at which the next stage can be spent.")
    private long maturityHeight;

    @Schema(description = "Blocks remaining until the HTLC output can be swept. Negative means matured.")
    private int blocksTilMaturity;

    @Schema(description = "Current stage of recovery for the HTLC (1 or 2).")
    private long stage;

    public PendingHtlcDTO(PendingHTLC pendingHTLC) {
        this.incoming = pendingHTLC.getIncoming();
        this.amount = pendingHTLC.getAmount();
        this.outpoint = pendingHTLC.getOutpoint();
        this.maturityHeight = pendingHTLC.getMaturityHeight();
        this.blocksTilMaturity = pendingHTLC.getBlocksTilMaturity();
        this.stage = pendingHTLC.getStage();
    }
}
