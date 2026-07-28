package com.aratiri.invoices.infrastructure.lightning;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.aratiri.invoices.application.port.out.LightningNodePort;
import lnrpc.LightningGrpc;
import lnrpc.PayReq;
import lnrpc.PayReqString;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class Bolt11DecodeCacheTest {

    @Configuration
    @EnableCaching
    static class TestCacheConfig {

        @Bean
        CacheManager cacheManager() {
            SimpleCacheManager manager = new SimpleCacheManager();
            manager.setCaches(List.of(new CaffeineCache(
                    "bolt11Decode",
                    Caffeine.newBuilder().expireAfterWrite(Duration.ofHours(24)).maximumSize(10_000).build()
            )));
            return manager;
        }

        @Bean
        LightningNodeAdapter lightningNodeAdapter(LightningGrpc.LightningBlockingStub stub) {
            return new LightningNodeAdapter(stub, 3600L);
        }
    }

    @Test
    void decodePaymentRequest_secondCallWithSameInvoiceIsServedFromCache() {
        LightningGrpc.LightningBlockingStub stub = mock(LightningGrpc.LightningBlockingStub.class);
        when(stub.withDeadlineAfter(anyLong(), any(TimeUnit.class))).thenReturn(stub);
        when(stub.decodePayReq(any(PayReqString.class)))
                .thenReturn(PayReq.newBuilder().setPaymentHash("hash").setNumSatoshis(1000L).build());

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean("lightningStub", LightningGrpc.LightningBlockingStub.class, () -> stub);
            context.register(TestCacheConfig.class);
            context.refresh();

            LightningNodePort port = context.getBean(LightningNodePort.class);
            port.decodePaymentRequest("lnbc1sameinvoice");
            port.decodePaymentRequest("lnbc1sameinvoice");

            verify(stub, times(1)).decodePayReq(any(PayReqString.class));
        }
    }

    @Test
    void decodePaymentRequest_cacheKeyIsCaseInsensitive() {
        LightningGrpc.LightningBlockingStub stub = mock(LightningGrpc.LightningBlockingStub.class);
        when(stub.withDeadlineAfter(anyLong(), any(TimeUnit.class))).thenReturn(stub);
        when(stub.decodePayReq(any(PayReqString.class)))
                .thenReturn(PayReq.newBuilder().setPaymentHash("hash").setNumSatoshis(1000L).build());

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean("lightningStub", LightningGrpc.LightningBlockingStub.class, () -> stub);
            context.register(TestCacheConfig.class);
            context.refresh();

            LightningNodePort port = context.getBean(LightningNodePort.class);
            port.decodePaymentRequest("LNBC1UPPER");
            port.decodePaymentRequest("lnbc1upper");

            verify(stub, times(1)).decodePayReq(any(PayReqString.class));
        }
    }
}
