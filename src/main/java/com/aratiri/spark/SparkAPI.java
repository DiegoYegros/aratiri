package com.aratiri.spark;

import com.aratiri.infrastructure.web.context.AratiriContext;
import com.aratiri.infrastructure.web.context.AratiriCtx;
import com.aratiri.spark.application.dto.BackupVerifiedRequestDTO;
import com.aratiri.spark.application.dto.RegisterSparkWalletRequestDTO;
import com.aratiri.spark.application.dto.SparkWalletDTO;
import com.aratiri.spark.application.dto.UpdateSparkPrivacyRequestDTO;
import com.aratiri.spark.application.port.in.SparkWalletPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/**
 * Non-custodial Spark wallet metadata API. Keys, signing, balances and history
 * live in the browser (DefaultSparkSigner + SparkReadonlyClient.createPublic);
 * this controller stores and serves public metadata only — no balance or
 * transaction endpoints (design §7, decision #5).
 */
@RestController
@RequestMapping("/v1/spark")
@Tag(name = "Spark", description = "Non-custodial Spark wallet metadata")
public class SparkAPI {

    private final SparkWalletPort sparkWalletPort;

    public SparkAPI(SparkWalletPort sparkWalletPort) {
        this.sparkWalletPort = sparkWalletPort;
    }

    @GetMapping("/wallet")
    @Operation(
            summary = "Get Spark wallet",
            description = "Returns the authenticated user's Spark wallet metadata, or an empty body when none is registered."
    )
    public ResponseEntity<SparkWalletDTO> getWallet(@AratiriCtx AratiriContext ctx) {
        return ResponseEntity.ok(sparkWalletPort.get(ctx.user().getId()).orElse(null));
    }

    @PostMapping("/wallets")
    @Operation(
            summary = "Register Spark wallet",
            description = "Registers public Spark wallet metadata (identity public key, spark address, network, account index). "
                    + "One wallet per user; 409 when one already exists or the identity key is already registered."
    )
    public ResponseEntity<SparkWalletDTO> register(
            @Valid @RequestBody RegisterSparkWalletRequestDTO request,
            @AratiriCtx AratiriContext ctx
    ) {
        SparkWalletDTO created = sparkWalletPort.register(ctx.user().getId(), request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .replacePath("/v1/spark/wallet")
                .build()
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PostMapping("/backup-verified")
    @Operation(
            summary = "Set backup-verified flag",
            description = "Syncs the onboarding backup-verification UX flag. Never a custody claim."
    )
    public ResponseEntity<SparkWalletDTO> setBackupVerified(
            @Valid @RequestBody BackupVerifiedRequestDTO request,
            @AratiriCtx AratiriContext ctx
    ) {
        return ResponseEntity.ok(
                sparkWalletPort.setBackupVerified(ctx.user().getId(), request.getBackupVerified())
        );
    }

    @PostMapping("/privacy")
    @Operation(
            summary = "Set privacy mode",
            description = "Syncs the privacyEnabled flag — the authoritative render source for the locked dashboard "
                    + "(hidden vs readable), never inferred from readonly-balance emptiness."
    )
    public ResponseEntity<SparkWalletDTO> setPrivacyEnabled(
            @Valid @RequestBody UpdateSparkPrivacyRequestDTO request,
            @AratiriCtx AratiriContext ctx
    ) {
        return ResponseEntity.ok(
                sparkWalletPort.setPrivacyEnabled(ctx.user().getId(), request.getPrivacyEnabled())
        );
    }

    @DeleteMapping("/wallet")
    @Operation(
            summary = "Forget Spark wallet",
            description = "Deletes the backend Spark wallet metadata. Idempotent — 204 whether or not a wallet existed. "
                    + "This is not a backup: if the mnemonic is lost the funds are gone."
    )
    public ResponseEntity<Void> forget(@AratiriCtx AratiriContext ctx) {
        sparkWalletPort.forget(ctx.user().getId());
        return ResponseEntity.noContent().build();
    }
}
