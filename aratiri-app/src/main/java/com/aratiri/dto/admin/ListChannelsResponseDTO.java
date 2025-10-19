package com.aratiri.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lnrpc.Channel;
import lnrpc.PendingChannelsResponse;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.List;
import java.util.stream.Collectors;

@Getter
@Setter
@ToString
@NoArgsConstructor
@Schema(description = "Aggregated response containing the list of open and pending Lightning channels for the node.")
public class ListChannelsResponseDTO {

    @Schema(
            description = "List of open channels with their details as returned by LND. " +
                    "Each channel includes information such as channel point, balances, remote node pubkey, and other metadata.",
            implementation = ChannelDTO.class
    )
    private List<ChannelDTO> openChannels;

    @Schema(
            description = "Grouped representation of the node's pending channels, categorized by their current state.",
            implementation = PendingChannelsResponseDTO.class
    )
    private PendingChannelsResponseDTO pendingChannels;

    public ListChannelsResponseDTO(List<Channel> channels, PendingChannelsResponse pendingChannels) {
        this.openChannels = channels.stream().map(channel -> new ChannelDTO(
                channel.getChannelPoint(),
                channel.getRemotePubkey(),
                channel.getCapacity(),
                channel.getLocalBalance(),
                channel.getRemoteBalance(),
                channel.getActive(),
                channel.getPrivate()
        )).collect(Collectors.toList());
        this.pendingChannels = new PendingChannelsResponseDTO(pendingChannels);
    }
}