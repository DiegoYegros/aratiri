package com.aratiri.aratiri.controller;

import com.aratiri.aratiri.dto.admin.CloseChannelRequestDTO;
import com.aratiri.aratiri.dto.admin.ListChannelsResponseDTO;
import com.aratiri.aratiri.dto.admin.NodeInfoDTO;
import com.aratiri.aratiri.dto.admin.OpenChannelRequestDTO;
import com.aratiri.aratiri.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lnrpc.Channel;
import lnrpc.CloseStatusUpdate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
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
        List<Channel> channels = adminService.listChannels();
        return ResponseEntity.ok(new ListChannelsResponseDTO(channels));
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
    public ResponseEntity<List<NodeInfoDTO>> listNodes() {
        return ResponseEntity.ok(adminService.listNodes());
    }
}