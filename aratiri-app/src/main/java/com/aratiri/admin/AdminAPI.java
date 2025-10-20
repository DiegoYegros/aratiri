package com.aratiri.admin;

import com.aratiri.admin.application.port.in.AdminPort;
import com.aratiri.shared.exception.AratiriException;
import com.aratiri.dto.admin.ChannelBalanceResponseDTO;
import com.aratiri.dto.admin.CloseChannelRequestDTO;
import com.aratiri.dto.admin.ConnectPeerRequestDTO;
import com.aratiri.dto.admin.ListChannelsResponseDTO;
import com.aratiri.dto.admin.NodeInfoResponseDTO;
import com.aratiri.dto.admin.NodeSettingsDTO;
import com.aratiri.dto.admin.OpenChannelRequestDTO;
import com.aratiri.dto.admin.PeerDTO;
import com.aratiri.dto.admin.RemotesResponseDTO;
import com.aratiri.dto.admin.TransactionStatsResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lnrpc.CloseStatusUpdate;
import lnrpc.GetInfoResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/v1/admin")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@Tag(name = "Admin", description = "Administrative operations for the Lightning node")
public class AdminAPI {

    private final AdminPort adminPort;

    public AdminAPI(AdminPort adminPort) {
        this.adminPort = adminPort;
    }

    @PostMapping("/connect-peer")
    @Operation(
            summary = "Connect to a Lightning peer",
            description = "Establishes a network connection to a remote peer."
    )
    public ResponseEntity<Void> connectPeer(@RequestBody ConnectPeerRequestDTO request) {
        adminPort.connectPeer(request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/peers")
    @Operation(summary = "List all connected peers")
    public ResponseEntity<List<PeerDTO>> listPeers() {
        List<PeerDTO> peerDTOs = adminPort.listPeers().stream().map(PeerDTO::fromGrpc).collect(Collectors.toList());
        return ResponseEntity.ok(peerDTOs);
    }

    @GetMapping("/node-info")
    @Operation(
            summary = "Get Lightning Node information",
            description = "Gets general information about the connected Lightning node"
    )
    public ResponseEntity<NodeInfoResponseDTO> getNodeInfo() {
        GetInfoResponse info = adminPort.getNodeInfo();
        List<com.aratiri.dto.admin.ChainDTO> chains = info.getChainsList().stream()
                .map(chain -> new com.aratiri.dto.admin.ChainDTO(chain.getChain(), chain.getNetwork()))
                .collect(Collectors.toList());
        NodeInfoResponseDTO response = NodeInfoResponseDTO.builder()
                .version(info.getVersion())
                .commitHash(info.getCommitHash())
                .identityPubkey(info.getIdentityPubkey())
                .alias(info.getAlias())
                .color(info.getColor())
                .numPendingChannels(info.getNumPendingChannels())
                .numActiveChannels(info.getNumActiveChannels())
                .numInactiveChannels(info.getNumInactiveChannels())
                .numPeers(info.getNumPeers())
                .blockHeight(info.getBlockHeight())
                .blockHash(info.getBlockHash())
                .syncedToChain(info.getSyncedToChain())
                .syncedToGraph(info.getSyncedToGraph())
                .chains(chains)
                .uris(info.getUrisList())
                .build();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/channels")
    @Operation(
            summary = "List all open Lightning channels",
            description = "Retrieves all currently open channels for the node, including local and remote balances, " +
                    "capacity, channel points, and channel status.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "List of open channels",
                            content = @Content(schema = @Schema(implementation = ListChannelsResponseDTO.class))
                    )
            }
    )
    public ResponseEntity<ListChannelsResponseDTO> listChannels() {
        return ResponseEntity.ok(adminPort.listChannels());
    }

    @PostMapping("/channels/open")
    @Operation(
            summary = "Open a new Lightning channel",
            description = "Opens a new channel to the specified remote node with the requested funding amount. " +
                    "Optionally pushes some satoshis to the remote node to provide inbound liquidity. " +
                    "Can be marked as private.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Returns the funding transaction ID of the newly opened channel",
                            content = @Content(schema = @Schema(implementation = String.class))
                    )
            }
    )
    public ResponseEntity<String> openChannel(@RequestBody OpenChannelRequestDTO request) {
        return ResponseEntity.ok(adminPort.openChannel(request));
    }

    @PostMapping("/channels/close")
    @Operation(
            summary = "Close a Lightning channel",
            description = "Closes an existing channel. Can request a cooperative close or a forced close. " +
                    "Returns the status of the close operation.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Close status update returned",
                            content = @Content(schema = @Schema(implementation = Object.class))
                    )
            }
    )
    public ResponseEntity<CloseStatusUpdate> closeChannel(@RequestBody CloseChannelRequestDTO request) {
        return ResponseEntity.ok(adminPort.closeChannel(request));
    }

    @GetMapping("/remotes")
    public ResponseEntity<RemotesResponseDTO> listNodes() {
        return ResponseEntity.ok(adminPort.listNodes());
    }

    @GetMapping("/channel-balance")
    @Operation(summary = "Get channel balance", description = "Gets the channel balance of the LND node")
    public ResponseEntity<ChannelBalanceResponseDTO> getChannelBalance() {
        return ResponseEntity.ok(adminPort.getChannelBalance());
    }

    @GetMapping("/transaction-stats")
    @Operation(summary = "Get transaction statistics", description = "Gets transaction data for a date range")
    public ResponseEntity<TransactionStatsResponseDTO> getTransactionStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Instant fromInstant = from.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant toInstant = to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        return ResponseEntity.ok(adminPort.getTransactionStats(fromInstant, toInstant));
    }

    @GetMapping("/settings")
    @Operation(summary = "Get current node settings")
    public ResponseEntity<NodeSettingsDTO> getNodeSettings() {
        return ResponseEntity.ok(adminPort.getNodeSettings());
    }

    @PutMapping("/settings/auto-manage-peers")
    @Operation(summary = "Enable or disable automatic peer management")
    public ResponseEntity<NodeSettingsDTO> updateAutoManagePeers(@RequestBody Map<String, Boolean> payload) {
        Boolean enabled = payload.get("enabled");
        if (enabled == null) {
            throw new AratiriException("Request body must contain 'enabled' field (true/false)", HttpStatus.BAD_REQUEST);
        }
        return ResponseEntity.ok(adminPort.updateAutoManagePeers(enabled));
    }
}
