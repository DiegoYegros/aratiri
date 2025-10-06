package com.aratiri.aratiri.service.impl;

import com.aratiri.aratiri.dto.admin.CloseChannelRequestDTO;
import com.aratiri.aratiri.dto.admin.OpenChannelRequestDTO;
import com.aratiri.aratiri.exception.AratiriException;
import com.aratiri.aratiri.service.AdminService;
import com.google.protobuf.ByteString;
import io.grpc.StatusRuntimeException;
import lnrpc.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class AdminServiceImpl implements AdminService {

    private final LightningGrpc.LightningBlockingStub lightningStub;

    public AdminServiceImpl(LightningGrpc.LightningBlockingStub lightningStub) {
        this.lightningStub = lightningStub;
    }

    @Override
    public List<Channel> listChannels() {
        ListChannelsRequest request = ListChannelsRequest.newBuilder().build();
        ListChannelsResponse response = lightningStub.listChannels(request);
        return response.getChannelsList();
    }

    @Override
    public String openChannel(OpenChannelRequestDTO request) {
        OpenChannelRequest openChannelRequest = OpenChannelRequest.newBuilder()
                .setNodePubkey(ByteString.copyFrom(request.getNodePubkey(), StandardCharsets.UTF_8))
                .setLocalFundingAmount(request.getLocalFundingAmount())
                .setPushSat(request.getPushSat())
                .setPrivate(request.isPrivateChannel())
                .build();
        try{
            ChannelPoint channelPoint = lightningStub.openChannelSync(openChannelRequest);
            return channelPoint.getFundingTxidStr();
        } catch (StatusRuntimeException e){
            throw new AratiriException(e.getStatus().getDescription(), HttpStatus.BAD_REQUEST);
        }
    }

    @Override
    public CloseStatusUpdate closeChannel(CloseChannelRequestDTO request) {
        String[] parts = request.getChannelPoint().split(":");
        ChannelPoint channelPoint = ChannelPoint.newBuilder()
                .setFundingTxidStr(parts[0])
                .setOutputIndex(Integer.parseInt(parts[1]))
                .build();

        CloseChannelRequest closeChannelRequest = CloseChannelRequest.newBuilder()
                .setChannelPoint(channelPoint)
                .setForce(request.isForce())
                .build();
        return lightningStub.closeChannel(closeChannelRequest).next();
    }
}