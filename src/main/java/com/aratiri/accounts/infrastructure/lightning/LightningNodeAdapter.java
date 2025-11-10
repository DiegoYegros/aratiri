package com.aratiri.accounts.infrastructure.lightning;

import com.aratiri.accounts.application.port.out.LightningAddressPort;
import lnrpc.AddressType;
import lnrpc.LightningGrpc;
import lnrpc.NewAddressRequest;
import org.springframework.stereotype.Component;

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
        return lightningStub.newAddress(request).getAddress();
    }
}
