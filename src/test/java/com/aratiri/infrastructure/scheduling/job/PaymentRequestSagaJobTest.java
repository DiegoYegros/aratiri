package com.aratiri.infrastructure.scheduling.job;

import com.aratiri.requests.application.PaymentRequestSagaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentRequestSagaJobTest {

    @Mock
    private PaymentRequestSagaService paymentRequestSagaService;

    @InjectMocks
    private PaymentRequestSagaJob job;

    @Test
    void processSagaWork_delegatesToService() {
        job.processSagaWork();

        verify(paymentRequestSagaService).processDueWork();
    }
}
