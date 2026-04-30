package com.aratiri.lnurl.application;

import com.aratiri.accounts.application.port.in.AccountsPort;
import com.aratiri.invoices.application.dto.GenerateInvoiceDTO;
import com.aratiri.invoices.application.port.in.InvoicesPort;
import com.aratiri.infrastructure.configuration.AratiriProperties;
import com.aratiri.lnurl.application.command.LnurlPaymentCommandService;
import com.aratiri.lnurl.application.dto.LnurlCallbackResponseDTO;
import com.aratiri.lnurl.application.dto.LnurlPayRequestDTO;
import com.aratiri.lnurl.application.dto.LnurlpResponseDTO;
import com.aratiri.lnurl.application.port.out.LnurlRemotePort;
import com.aratiri.payments.application.dto.PaymentResponseDTO;
import com.aratiri.payments.application.port.in.PaymentsPort;
import com.aratiri.shared.exception.AratiriException;
import com.aratiri.transactions.application.dto.TransactionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LnurlAdapterTest {

    @Mock
    private AccountsPort accountsPort;

    @Mock
    private InvoicesPort invoicesPort;

    @Mock
    private PaymentsPort paymentsPort;

    @Mock
    private AratiriProperties properties;

    @Mock
    private LnurlRemotePort lnurlRemotePort;

    @Mock
    private LnurlPaymentCommandService lnurlPaymentCommand;

    private LnurlAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new LnurlAdapter(accountsPort, invoicesPort, paymentsPort, properties, lnurlRemotePort, lnurlPaymentCommand);
    }

    @Test
    void getLnurlMetadata_returnsMetadataForValidAlias() {
        when(properties.getAratiriBaseUrl()).thenReturn("https://aratiri.example.com");
        when(accountsPort.existsByAlias("testuser")).thenReturn(true);

        LnurlpResponseDTO result = adapter.getLnurlMetadata("testuser");

        assertEquals("https://aratiri.example.com/lnurl/callback/testuser", result.getCallback());
        assertEquals(1000L, result.getMinSendable());
        assertEquals("payRequest", result.getTag());
        assertEquals("OK", result.getStatus());
    }

    @Test
    void getLnurlMetadata_throwsWhenAliasNotFound() {
        when(accountsPort.existsByAlias("unknown")).thenReturn(false);

        assertThrows(AratiriException.class, () -> adapter.getLnurlMetadata("unknown"));
    }

    @Test
    void getExternalLnurlMetadata_returnsFromRemote() {
        LnurlpResponseDTO expected = new LnurlpResponseDTO();
        expected.setCallback("https://ext.com/callback");
        when(lnurlRemotePort.fetchMetadata("https://ext.com")).thenReturn(expected);

        LnurlpResponseDTO result = adapter.getExternalLnurlMetadata("https://ext.com");

        assertEquals(expected, result);
    }

    @Test
    void getExternalLnurlMetadata_throwsOnRemoteFailure() {
        when(lnurlRemotePort.fetchMetadata("https://ext.com")).thenThrow(new RuntimeException("error"));

        assertThrows(AratiriException.class, () -> adapter.getExternalLnurlMetadata("https://ext.com"));
    }

    @Test
    void lnurlCallback_generatesInvoice() {
        GenerateInvoiceDTO invoice = new GenerateInvoiceDTO("lnbc1...");
        when(accountsPort.existsByAlias("testuser")).thenReturn(true);
        when(invoicesPort.generateInvoice(eq("testuser"), eq(5000L), eq("thanks"), isNull(), isNull()))
                .thenReturn(invoice);

        Object result = adapter.lnurlCallback("testuser", 5_000_000L, "thanks");

        assertNotNull(result);
        assertTrue(result instanceof java.util.Map);
        assertEquals("lnbc1...", ((java.util.Map<?, ?>) result).get("pr"));
    }

    @Test
    void lnurlCallback_usesDefaultMemoWhenCommentNull() {
        GenerateInvoiceDTO invoice = new GenerateInvoiceDTO("lnbc1...");
        when(accountsPort.existsByAlias("testuser")).thenReturn(true);
        when(invoicesPort.generateInvoice(eq("testuser"), eq(1000L), eq("No description"), isNull(), isNull()))
                .thenReturn(invoice);

        Object result = adapter.lnurlCallback("testuser", 1_000_000L, null);

        assertNotNull(result);
    }

    @Test
    void lnurlCallback_throwsWhenAliasNotFound() {
        when(accountsPort.existsByAlias("unknown")).thenReturn(false);

        assertThrows(AratiriException.class, () -> adapter.lnurlCallback("unknown", 1000L, null));
    }

    @Test
    void handlePayRequest_delegatesToCommand() {
        LnurlPayRequestDTO request = new LnurlPayRequestDTO();
        request.setCallback("https://ext.com/callback");
        request.setAmountMsat(5_000_000L);
        PaymentResponseDTO expected = PaymentResponseDTO.builder()
                .transactionId("tx-1").status(TransactionStatus.PENDING).build();
        when(lnurlPaymentCommand.execute(eq("user-1"), eq("key-1"), eq(request), any()))
                .thenReturn(expected);

        PaymentResponseDTO result = adapter.handlePayRequest(request, "user-1", "key-1");

        assertEquals(expected, result);
    }

    @Test
    void handlePayRequest_executesLambdaAndPaysInvoice() {
        LnurlPayRequestDTO request = new LnurlPayRequestDTO();
        request.setCallback("https://ext.com/callback");
        request.setAmountMsat(5_000_000L);

        LnurlCallbackResponseDTO callbackResponse = new LnurlCallbackResponseDTO();
        callbackResponse.setPaymentRequest("lnbc500n1...");

        PaymentResponseDTO paymentResponse = PaymentResponseDTO.builder()
                .transactionId("tx-1").status(TransactionStatus.PENDING).build();

        doAnswer(invocation -> {
            Supplier<PaymentResponseDTO> supplier = invocation.getArgument(3);
            return supplier.get();
        }).when(lnurlPaymentCommand).execute(eq("user-1"), eq("key-1"), eq(request), any());

        when(lnurlRemotePort.fetchCallbackInvoice(anyString())).thenReturn(callbackResponse);
        when(paymentsPort.payLightningInvoiceInternal(any(), eq("user-1"))).thenReturn(paymentResponse);

        PaymentResponseDTO result = adapter.handlePayRequest(request, "user-1", "key-1");

        assertEquals("tx-1", result.getTransactionId());
        verify(lnurlRemotePort).fetchCallbackInvoice(anyString());
        verify(paymentsPort).payLightningInvoiceInternal(any(), eq("user-1"));
    }

    @Test
    void executeLnurlPayment_throwsWhenRemoteFails() {
        LnurlPayRequestDTO request = new LnurlPayRequestDTO();
        request.setCallback("https://ext.com/callback");
        request.setAmountMsat(5_000_000L);

        doAnswer(invocation -> {
            Supplier<PaymentResponseDTO> supplier = invocation.getArgument(3);
            return supplier.get();
        }).when(lnurlPaymentCommand).execute(eq("user-1"), eq("key-1"), eq(request), any());

        when(lnurlRemotePort.fetchCallbackInvoice(anyString())).thenThrow(new RuntimeException("remote error"));

        assertThrows(AratiriException.class, () -> adapter.handlePayRequest(request, "user-1", "key-1"));
    }

    @Test
    void executeLnurlPayment_throwsWhenCallbackResponseNull() {
        LnurlPayRequestDTO request = new LnurlPayRequestDTO();
        request.setCallback("https://ext.com/callback");
        request.setAmountMsat(5_000_000L);

        doAnswer(invocation -> {
            Supplier<PaymentResponseDTO> supplier = invocation.getArgument(3);
            return supplier.get();
        }).when(lnurlPaymentCommand).execute(eq("user-1"), eq("key-1"), eq(request), any());

        when(lnurlRemotePort.fetchCallbackInvoice(anyString())).thenReturn(null);

        assertThrows(AratiriException.class, () -> adapter.handlePayRequest(request, "user-1", "key-1"));
    }

    @Test
    void executeLnurlPayment_throwsWhenPaymentRequestEmpty() {
        LnurlPayRequestDTO request = new LnurlPayRequestDTO();
        request.setCallback("https://ext.com/callback");
        request.setAmountMsat(5_000_000L);

        LnurlCallbackResponseDTO callbackResponse = new LnurlCallbackResponseDTO();
        callbackResponse.setPaymentRequest("");

        doAnswer(invocation -> {
            Supplier<PaymentResponseDTO> supplier = invocation.getArgument(3);
            return supplier.get();
        }).when(lnurlPaymentCommand).execute(eq("user-1"), eq("key-1"), eq(request), any());

        when(lnurlRemotePort.fetchCallbackInvoice(anyString())).thenReturn(callbackResponse);

        assertThrows(AratiriException.class, () -> adapter.handlePayRequest(request, "user-1", "key-1"));
    }

    @Test
    void executeLnurlPayment_buildsUrlWithComment() {
        LnurlPayRequestDTO request = new LnurlPayRequestDTO();
        request.setCallback("https://ext.com/callback");
        request.setAmountMsat(5_000_000L);
        request.setComment("test comment");

        LnurlCallbackResponseDTO callbackResponse = new LnurlCallbackResponseDTO();
        callbackResponse.setPaymentRequest("lnbc500n1...");

        PaymentResponseDTO paymentResponse = PaymentResponseDTO.builder()
                .transactionId("tx-1").status(TransactionStatus.PENDING).build();

        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Supplier<PaymentResponseDTO> supplier = invocation.getArgument(3);
            return supplier.get();
        }).when(lnurlPaymentCommand).execute(eq("user-1"), eq("key-1"), eq(request), any());

        when(lnurlRemotePort.fetchCallbackInvoice(contains("comment=test%20comment")))
                .thenReturn(callbackResponse);
        when(paymentsPort.payLightningInvoiceInternal(any(), eq("user-1"))).thenReturn(paymentResponse);

        PaymentResponseDTO result = adapter.handlePayRequest(request, "user-1", "key-1");

        assertEquals("tx-1", result.getTransactionId());
    }

    @Test
    void executeLnurlPayment_buildsUrlWithoutCommentWhenEmpty() {
        LnurlPayRequestDTO request = new LnurlPayRequestDTO();
        request.setCallback("https://ext.com/callback");
        request.setAmountMsat(5_000_000L);
        request.setComment("");

        LnurlCallbackResponseDTO callbackResponse = new LnurlCallbackResponseDTO();
        callbackResponse.setPaymentRequest("lnbc500n1...");

        PaymentResponseDTO paymentResponse = PaymentResponseDTO.builder()
                .transactionId("tx-2").status(TransactionStatus.PENDING).build();

        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Supplier<PaymentResponseDTO> supplier = invocation.getArgument(3);
            return supplier.get();
        }).when(lnurlPaymentCommand).execute(eq("user-1"), eq("key-1"), eq(request), any());

        when(lnurlRemotePort.fetchCallbackInvoice(anyString()))
                .thenReturn(callbackResponse);
        when(paymentsPort.payLightningInvoiceInternal(any(), eq("user-1"))).thenReturn(paymentResponse);

        PaymentResponseDTO result = adapter.handlePayRequest(request, "user-1", "key-1");

        assertEquals("tx-2", result.getTransactionId());
    }

    @Test
    void getLnurlMetadata_includesCorrectMetadata() {
        when(properties.getAratiriBaseUrl()).thenReturn("https://aratiri.example.com");
        when(accountsPort.existsByAlias("testuser")).thenReturn(true);

        LnurlpResponseDTO result = adapter.getLnurlMetadata("testuser");

        assertEquals("payRequest", result.getTag());
        assertEquals(1000L, result.getMinSendable());
        assertNotNull(result.getMaxSendable());
        assertTrue(result.getMaxSendable() > 0);
        assertEquals("OK", result.getStatus());
        assertTrue(result.getCommentAllowed() > 0);
        assertNotNull(result.getMetadata());
    }

    @Test
    void executeLnurlPayment_throwsWhenFetchThrowsCheckedException() {
        LnurlPayRequestDTO request = new LnurlPayRequestDTO();
        request.setCallback("https://ext.com/callback");
        request.setAmountMsat(5_000_000L);

        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Supplier<PaymentResponseDTO> supplier = invocation.getArgument(3);
            return supplier.get();
        }).when(lnurlPaymentCommand).execute(eq("user-1"), eq("key-1"), eq(request), any());

        when(lnurlRemotePort.fetchCallbackInvoice(anyString()))
                .thenThrow(new RuntimeException(new java.io.IOException("network error")));

        assertThrows(AratiriException.class, () -> adapter.handlePayRequest(request, "user-1", "key-1"));
    }

    @Test
    void executeLnurlPayment_throwsWhenCallbackResponseHasNullPaymentRequest() {
        LnurlPayRequestDTO request = new LnurlPayRequestDTO();
        request.setCallback("https://ext.com/callback");
        request.setAmountMsat(5_000_000L);

        LnurlCallbackResponseDTO callbackResponse = new LnurlCallbackResponseDTO();
        callbackResponse.setPaymentRequest(null);

        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Supplier<PaymentResponseDTO> supplier = invocation.getArgument(3);
            return supplier.get();
        }).when(lnurlPaymentCommand).execute(eq("user-1"), eq("key-1"), eq(request), any());

        when(lnurlRemotePort.fetchCallbackInvoice(anyString())).thenReturn(callbackResponse);

        assertThrows(AratiriException.class, () -> adapter.handlePayRequest(request, "user-1", "key-1"));
    }
}
