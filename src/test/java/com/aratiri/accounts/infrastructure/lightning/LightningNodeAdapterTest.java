package com.aratiri.accounts.infrastructure.lightning;

import lnrpc.LightningGrpc;
import lnrpc.NewAddressRequest;
import lnrpc.NewAddressResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LightningNodeAdapterTest {

    @Mock
    private LightningGrpc.LightningBlockingStub lightningStub;

    @Test
    void generateTaprootAddress_returnsAddressFromLnd() {
        when(lightningStub.newAddress(any(NewAddressRequest.class)))
                .thenReturn(NewAddressResponse.newBuilder().setAddress("bc1qtest").build());

        LightningNodeAdapter adapter = new LightningNodeAdapter(lightningStub);
        String address = adapter.generateTaprootAddress();

        assertEquals("bc1qtest", address);
    }
}
