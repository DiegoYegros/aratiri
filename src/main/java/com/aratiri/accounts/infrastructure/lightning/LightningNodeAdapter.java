package com.aratiri.accounts.infrastructure.lightning;

import com.aratiri.accounts.application.port.out.LightningAddressPort;
import com.aratiri.infrastructure.grpc.GrpcDeadlines;
import lnrpc.AddressType;
import lnrpc.LightningGrpc;
import lnrpc.NewAddressRequest;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class LightningNodeAdapter implements LightningAddressPort {

    private final LightningGrpc.LightningBlockingStub lightningStub;

    public LightningNodeAdapter(LightningGrpc.LightningBlockingStub lightningStub) {
        this.lightningStub = lightningStub;
    }

    @Override
    public String generateTaprootAddress() {
        NewAddressRequest request = NewAddressRequest.newBuilder()
                .setType(AddressType.TAPROOT_PUBKEY)
                .build();
        return lightningStub
                .withDeadlineAfter(GrpcDeadlines.ADMIN.toMillis(), TimeUnit.MILLISECONDS)
                .newAddress(request).getAddress();
    }
}
