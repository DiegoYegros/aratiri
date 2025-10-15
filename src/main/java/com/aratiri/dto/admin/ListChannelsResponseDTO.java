package com.aratiri.dto.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import lnrpc.Channel;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;
import java.util.stream.Collectors;

@Getter
@Setter
@ToString
@Schema(description = "Response containing the list of all open Lightning channels for the node.")
public class ListChannelsResponseDTO {

    @Schema(
            description = "List of open channels with their details as returned by LND. " +
                    "Each Channel object includes information such as channel point, balances, " +
                    "remote node pubkey, and other channel metadata.",
            implementation = ChannelDTO.class
    )
    private List<ChannelDTO> channels;

    public ListChannelsResponseDTO(List<Channel> channels) {
        this.channels = channels.stream().map(channel -> new ChannelDTO(
                channel.getChannelPoint(),
                channel.getRemotePubkey(),
                channel.getCapacity(),
                channel.getLocalBalance(),
                channel.getRemoteBalance(),
                channel.getActive(),
                channel.getPrivate()
        )).collect(Collectors.toList());
    }
}