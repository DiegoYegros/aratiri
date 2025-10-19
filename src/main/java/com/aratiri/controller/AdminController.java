package com.aratiri.controller;

import com.aratiri.dto.admin.*;
import com.aratiri.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lnrpc.Channel;
import lnrpc.ChannelBalanceResponse;
import lnrpc.CloseStatusUpdate;
import lnrpc.GetInfoResponse;
import lnrpc.PendingChannelsResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/v1/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }


    @PostMapping("/connect-peer")
    @Operation(
            summary = "Connect to a Lightning peer",
            description = "Establishes a network connection to a remote peer."
    )
    public ResponseEntity<Void> connectPeer(@RequestBody ConnectPeerRequestDTO request) {
        adminService.connectPeer(request.getPubkey(), request.getHost());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/peers")
    @Operation(summary = "List all connected peers")
    public ResponseEntity<List<PeerDTO>> listPeers() {
        List<PeerDTO> peerDTOs = adminService.listPeers().stream().map(PeerDTO::fromGrpc).collect(Collectors.toList());
        return ResponseEntity.ok(peerDTOs);
    }

    @GetMapping("/node-info")
    @Operation(
            summary = "Get Lightning Node information",
            description = "Gets general information about the connected Lightning node"
    )
    public ResponseEntity<NodeInfoResponseDTO> getNodeInfo() {
        GetInfoResponse info = adminService.getNodeInfo();
        List<ChainDTO> chains = info.getChainsList().stream()
                .map(chain -> new ChainDTO(chain.getChain(), chain.getNetwork()))
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
        return ResponseEntity.ok(adminService.listChannels());
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
        return ResponseEntity.ok(adminService.openChannel(request));
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
        return ResponseEntity.ok(adminService.closeChannel(request));
    }

    @GetMapping("/remotes")
    public ResponseEntity<RemotesResponseDTO> listNodes() {
        return ResponseEntity.ok(new RemotesResponseDTO(adminService.listNodes()));
    }

    @GetMapping("/channel-balance")
    @Operation(summary = "Get channel balance", description = "Gets the channel balance of the LND node")
    public ResponseEntity<ChannelBalanceResponseDTO> getChannelBalance() {
        ChannelBalanceResponse balance = adminService.getChannelBalance();
        ChannelBalanceResponseDTO response = ChannelBalanceResponseDTO.builder()
                .localBalance(new AmountDTO(balance.getLocalBalance().getSat(), balance.getLocalBalance().getMsat()))
                .remoteBalance(new AmountDTO(balance.getRemoteBalance().getSat(), balance.getRemoteBalance().getMsat()))
                .build();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/transaction-stats")
    @Operation(summary = "Get transaction statistics", description = "Gets transaction data for a date range")
    public ResponseEntity<TransactionStatsResponseDTO> getTransactionStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        Instant fromInstant = from.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant toInstant = to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
        List<TransactionStatsDTO> stats = adminService.getTransactionStats(fromInstant, toInstant);
        return ResponseEntity.ok(new TransactionStatsResponseDTO(stats));
    }
}