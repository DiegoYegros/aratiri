package com.aratiri.aratiri.service;

import com.aratiri.aratiri.dto.admin.CloseChannelRequestDTO;
import com.aratiri.aratiri.dto.admin.OpenChannelRequestDTO;
import lnrpc.Channel;
import lnrpc.CloseStatusUpdate;

import java.util.List;

public interface AdminService {
    List<Channel> listChannels();
    String openChannel(OpenChannelRequestDTO request);
    CloseStatusUpdate closeChannel(CloseChannelRequestDTO request);
}