package com.aratiri.decoder.application;

import com.aratiri.decoder.application.dto.DecodedResultDTO;
import com.aratiri.decoder.application.port.out.InvoiceDecodingPort;
import com.aratiri.decoder.application.port.out.LnurlPort;
import com.aratiri.decoder.application.port.out.NostrPort;
import com.aratiri.infrastructure.configuration.AratiriProperties;
import com.aratiri.infrastructure.http.destination.OutboundDestinationRejectedException;
import com.aratiri.invoices.application.dto.DecodedInvoicetDTO;
import com.aratiri.lnurl.application.dto.LnurlpResponseDTO;
import com.aratiri.errors.ApplicationException;
import com.aratiri.bitcoin.Bech32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DecoderAdapterTest {

    @Mock
    private InvoiceDecodingPort invoiceDecodingPort;

    @Mock
    private LnurlPort lnurlPort;

    @Mock
    private NostrPort nostrPort;

    @Mock
    private AratiriProperties aratiriProperties;

    private DecoderAdapter decoderAdapter;

    @BeforeEach
    void setUp() {
        decoderAdapter = new DecoderAdapter(invoiceDecodingPort, lnurlPort, nostrPort, aratiriProperties);
    }

    private LnurlpResponseDTO buildLnurlMetadata() {
        LnurlpResponseDTO dto = new LnurlpResponseDTO();
        dto.setCallback("https://callback");
        dto.setMaxSendable(1000L);
        dto.setMinSendable(100L);
        dto.setMetadata("[]");
        return dto;
    }

    @Test
    void decode_lightningInvoice_bolt11() {
        DecodedInvoicetDTO decoded = DecodedInvoicetDTO.builder().build();
        when(invoiceDecodingPort.decodeInvoice(any())).thenReturn(decoded);
        DecodedResultDTO result = decoderAdapter.decode("lnbc1abc");
        assertEquals("lightning_invoice", result.getType());
        assertNotNull(result.getData());
    }

    @Test
    void decode_lightningInvoice_withPrefix() {
        DecodedInvoicetDTO decoded = DecodedInvoicetDTO.builder().build();
        when(invoiceDecodingPort.decodeInvoice(any())).thenReturn(decoded);
        DecodedResultDTO result = decoderAdapter.decode("lightning:lnbc1abc");
        assertEquals("lightning_invoice", result.getType());
    }

    @Test
    void decode_lightningInvoice_invalid() {
        when(invoiceDecodingPort.decodeInvoice(any())).thenThrow(new RuntimeException("invalid"));
        DecodedResultDTO result = decoderAdapter.decode("lnbc1invalid");
        assertEquals("error", result.getType());
        assertEquals("Invalid Lightning Invoice", result.getError());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "bc1qar0srrr7xfkvyusn8nqz50nw0jy5zkr28",
            "bitcoin:bc1qexample",
            "1A1zP1eP5QGefi2DMPTfTL5SLmv7DivfNa",
            "tb1qexample"
    })
    void decode_bitcoinAddress(String input) {
        DecodedResultDTO result = decoderAdapter.decode(input);
        assertEquals("bitcoin_address", result.getType());
    }

    @Test
    void decode_npub_withLud16() {
        String npubInput = "npub1examplekey";
        CompletableFuture<String> future = CompletableFuture.completedFuture("test@example.com");
        when(nostrPort.getLud16FromNpub(npubInput)).thenReturn(future);
        when(lnurlPort.getExternalMetadata("https://example.com/.well-known/lnurlp/test")).thenReturn(buildLnurlMetadata());

        DecodedResultDTO result = decoderAdapter.decode(npubInput);
        assertEquals("lnurl_params", result.getType());
    }

    @Test
    void decode_npub_noLud16() {
        CompletableFuture<String> future = CompletableFuture.completedFuture(null);
        when(nostrPort.getLud16FromNpub("npub1empty")).thenReturn(future);

        DecodedResultDTO result = decoderAdapter.decode("npub1empty");
        assertEquals("error", result.getType());
    }

    @Test
    void decode_npub_emptyLud16() {
        CompletableFuture<String> future = CompletableFuture.completedFuture("");
        when(nostrPort.getLud16FromNpub("npub1empty")).thenReturn(future);

        DecodedResultDTO result = decoderAdapter.decode("npub1empty");
        assertEquals("error", result.getType());
    }

    @Test
    @SuppressWarnings("unchecked")
    void decode_npub_interrupted() throws Exception {
        CompletableFuture<String> future = mock(CompletableFuture.class);
        when(future.get()).thenThrow(new InterruptedException());
        when(nostrPort.getLud16FromNpub("npub1interrupted")).thenReturn(future);

        DecodedResultDTO result = decoderAdapter.decode("npub1interrupted");
        assertEquals("error", result.getType());
    }

    @Test
    void decode_npub_executionException() {
        CompletableFuture<String> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("test"));
        when(nostrPort.getLud16FromNpub("npub1error")).thenReturn(future);

        DecodedResultDTO result = decoderAdapter.decode("npub1error");
        assertEquals("error", result.getType());
    }

    @Test
    void decode_lnurl_internal() {
        String url = "https://aratiri.example.com/lnurl/user123";
        when(aratiriProperties.getAratiriBaseUrl()).thenReturn("https://aratiri.example.com");
        when(lnurlPort.getInternalMetadata("user123")).thenReturn(buildLnurlMetadata());

        DecodedResultDTO result = decoderAdapter.decode(Bech32.encodeLnurl(url));
        assertEquals("lnurl_params", result.getType());
    }

    @Test
    void decode_lnurl_external() {
        String url = "https://external.example.com/lnurl/pay/invoice2";
        when(aratiriProperties.getAratiriBaseUrl()).thenReturn("https://aratiri.example.com");
        when(lnurlPort.getExternalMetadata(url)).thenReturn(buildLnurlMetadata());

        DecodedResultDTO result = decoderAdapter.decode(Bech32.encodeLnurl(url));
        assertEquals("lnurl_params", result.getType());
    }

    @Test
    void decode_lnurl_exception() {
        DecodedResultDTO result = decoderAdapter.decode(Bech32.encodeLnurl("https://error.example.com"));
        assertEquals("error", result.getType());
        assertEquals("Invalid or disallowed LNURL", result.getError());
    }

    @Test
    void decode_lnurl_policyReject_usesStableMessageWithoutIpLeak() {
        String url = "https://127.0.0.1/secret";
        when(aratiriProperties.getAratiriBaseUrl()).thenReturn("https://aratiri.example.com");
        when(lnurlPort.getExternalMetadata(url)).thenThrow(new ApplicationException(
                OutboundDestinationRejectedException.PUBLIC_MESSAGE,
                HttpStatus.BAD_REQUEST.value(),
                new OutboundDestinationRejectedException()));

        DecodedResultDTO result = decoderAdapter.decode(Bech32.encodeLnurl(url));
        assertEquals("error", result.getType());
        assertEquals(OutboundDestinationRejectedException.PUBLIC_MESSAGE, result.getError());
        assertFalse(result.getError().contains("127.0.0.1"));
        assertFalse(result.getError().contains("169.254"));
        assertFalse(result.getError().contains("SECRET"));
    }

    @Test
    void decode_lnurl_rawOutboundReject_usesStableMessage() {
        String url = "https://10.0.0.1/secret";
        when(aratiriProperties.getAratiriBaseUrl()).thenReturn("https://aratiri.example.com");
        when(lnurlPort.getExternalMetadata(url)).thenThrow(new OutboundDestinationRejectedException());

        DecodedResultDTO result = decoderAdapter.decode(Bech32.encodeLnurl(url));
        assertEquals("error", result.getType());
        assertEquals(OutboundDestinationRejectedException.PUBLIC_MESSAGE, result.getError());
    }

    @Test
    void decode_lnurl_nonOutboundApplicationException_usesGenericMessage() {
        String url = "https://external.example.com/lnurl";
        when(aratiriProperties.getAratiriBaseUrl()).thenReturn("https://aratiri.example.com");
        when(lnurlPort.getExternalMetadata(url))
                .thenThrow(new ApplicationException("gateway blew up", HttpStatus.BAD_GATEWAY.value()));

        DecodedResultDTO result = decoderAdapter.decode(Bech32.encodeLnurl(url));
        assertEquals("error", result.getType());
        assertEquals("Invalid or disallowed LNURL", result.getError());
        assertFalse(result.getError().contains("gateway blew up"));
    }

    @Test
    void decode_npub_outboundRejectViaExecutionException() {
        CompletableFuture<String> future = new CompletableFuture<>();
        future.completeExceptionally(new OutboundDestinationRejectedException());
        when(nostrPort.getLud16FromNpub("npub1ssrf")).thenReturn(future);

        DecodedResultDTO result = decoderAdapter.decode("npub1ssrf");
        assertEquals("error", result.getType());
        assertEquals(OutboundDestinationRejectedException.PUBLIC_MESSAGE, result.getError());
    }

    @Test
    void decode_npub_outboundRejectFromLightningAddressResolve() {
        when(nostrPort.getLud16FromNpub("npub1addr"))
                .thenReturn(CompletableFuture.completedFuture("user@10.0.0.2"));
        when(lnurlPort.getExternalMetadata("https://10.0.0.2/.well-known/lnurlp/user"))
                .thenThrow(new OutboundDestinationRejectedException());

        DecodedResultDTO result = decoderAdapter.decode("npub1addr");
        assertEquals("error", result.getType());
        assertEquals(OutboundDestinationRejectedException.PUBLIC_MESSAGE, result.getError());
    }

    @Test
    void decode_lightningAddress_rawOutboundReject_usesStableMessage() {
        when(lnurlPort.getInternalMetadata("user")).thenThrow(new ApplicationException("not found"));
        when(lnurlPort.getExternalMetadata("https://192.168.0.5/.well-known/lnurlp/user"))
                .thenThrow(new OutboundDestinationRejectedException());

        DecodedResultDTO result = decoderAdapter.decode("user@192.168.0.5");
        assertEquals("error", result.getType());
        assertEquals(OutboundDestinationRejectedException.PUBLIC_MESSAGE, result.getError());
    }

    @Test
    void decode_nip05_outboundRejectFromLightningAddressResolve() {
        when(lnurlPort.getInternalMetadata("test.domain"))
                .thenThrow(new ApplicationException("not found"));
        when(nostrPort.resolveNip05ToLud16("_@test.domain"))
                .thenReturn(CompletableFuture.completedFuture("user@10.1.1.1"));
        when(lnurlPort.getExternalMetadata("https://10.1.1.1/.well-known/lnurlp/user"))
                .thenThrow(new ApplicationException(
                        OutboundDestinationRejectedException.PUBLIC_MESSAGE,
                        HttpStatus.BAD_REQUEST.value(),
                        new OutboundDestinationRejectedException()));

        DecodedResultDTO result = decoderAdapter.decode("test.domain");
        assertEquals("error", result.getType());
        assertEquals(OutboundDestinationRejectedException.PUBLIC_MESSAGE, result.getError());
    }

    @Test
    void decode_lightningAddress_policyReject_usesStableMessage() {
        when(lnurlPort.getInternalMetadata("user")).thenThrow(new ApplicationException("not found"));
        when(lnurlPort.getExternalMetadata("https://169.254.169.254/.well-known/lnurlp/user"))
                .thenThrow(new ApplicationException(
                        OutboundDestinationRejectedException.PUBLIC_MESSAGE,
                        HttpStatus.BAD_REQUEST.value(),
                        new OutboundDestinationRejectedException()));

        DecodedResultDTO result = decoderAdapter.decode("user@169.254.169.254");
        assertEquals("error", result.getType());
        assertEquals(OutboundDestinationRejectedException.PUBLIC_MESSAGE, result.getError());
        assertFalse(result.getError().contains("169.254"));
    }

    @Test
    void decode_nip05_policyReject_usesStableMessage() {
        when(lnurlPort.getInternalMetadata("test.domain"))
                .thenThrow(new ApplicationException("not found"));
        CompletableFuture<String> future = new CompletableFuture<>();
        future.completeExceptionally(new OutboundDestinationRejectedException());
        when(nostrPort.resolveNip05ToLud16("_@test.domain")).thenReturn(future);

        DecodedResultDTO result = decoderAdapter.decode("test.domain");
        assertEquals("error", result.getType());
        assertEquals(OutboundDestinationRejectedException.PUBLIC_MESSAGE, result.getError());
    }

    @Test
    void decode_alias_found() {
        when(lnurlPort.getInternalMetadata("myalias")).thenReturn(buildLnurlMetadata());

        DecodedResultDTO result = decoderAdapter.decode("myalias");
        assertEquals("alias", result.getType());
    }

    @Test
    void decode_alias_notFound_tryLightningAddress() {
        when(lnurlPort.getInternalMetadata("test")).thenThrow(new ApplicationException("not found"));
        when(lnurlPort.getExternalMetadata("https://example.com/.well-known/lnurlp/test")).thenReturn(buildLnurlMetadata());

        DecodedResultDTO result = decoderAdapter.decode("test@example.com");
        assertEquals("lnurl_params", result.getType());
    }

    @Test
    void decode_unsupported_format() {
        when(lnurlPort.getInternalMetadata(any())).thenThrow(new ApplicationException("not found"));
        DecodedResultDTO result = decoderAdapter.decode("something_random");
        assertEquals("error", result.getType());
    }

    @Test
    void decode_trim_and_lowercase() {
        DecodedInvoicetDTO decoded = DecodedInvoicetDTO.builder().build();
        when(invoiceDecodingPort.decodeInvoice(any())).thenReturn(decoded);
        DecodedResultDTO result = decoderAdapter.decode("  LnBc1AbC  ");
        assertEquals("lightning_invoice", result.getType());
    }

    @Test
    void decode_nip05_success() {
        when(lnurlPort.getInternalMetadata("test.domain"))
                .thenThrow(new ApplicationException("not found"));
        CompletableFuture<String> future = CompletableFuture.completedFuture("user@domain.com");
        when(nostrPort.resolveNip05ToLud16("_@test.domain")).thenReturn(future);
        when(lnurlPort.getExternalMetadata("https://domain.com/.well-known/lnurlp/user"))
                .thenReturn(buildLnurlMetadata());

        DecodedResultDTO result = decoderAdapter.decode("test.domain");
        assertEquals("lnurl_params", result.getType());
    }

    @Test
    void decode_nip05_withAtSign_alreadyHasAt() {
        when(lnurlPort.getInternalMetadata("test")).thenThrow(new ApplicationException("not found"));
        when(lnurlPort.getExternalMetadata("https://domain.com/.well-known/lnurlp/test"))
                .thenThrow(new RuntimeException("bad"));

        CompletableFuture<String> future = CompletableFuture.completedFuture("user@nip05.com");
        when(nostrPort.resolveNip05ToLud16("test@domain.com")).thenReturn(future);
        when(lnurlPort.getExternalMetadata("https://nip05.com/.well-known/lnurlp/user"))
                .thenReturn(buildLnurlMetadata());

        DecodedResultDTO result = decoderAdapter.decode("test@domain.com");
        assertEquals("lnurl_params", result.getType());
    }

    @Test
    @SuppressWarnings("unchecked")
    void decode_nip05_interruptedException() throws Exception {
        when(lnurlPort.getInternalMetadata("test.domain"))
                .thenThrow(new ApplicationException("not found"));
        CompletableFuture<String> future = mock(CompletableFuture.class);
        when(future.get(3, TimeUnit.SECONDS)).thenThrow(new InterruptedException());
        when(nostrPort.resolveNip05ToLud16("_@test.domain")).thenReturn(future);

        DecodedResultDTO result = decoderAdapter.decode("test.domain");
        assertEquals("error", result.getType());
    }

    @Test
    void decode_nip05_executionException() {
        when(lnurlPort.getInternalMetadata("test.domain"))
                .thenThrow(new ApplicationException("not found"));
        CompletableFuture<String> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("fail"));
        when(nostrPort.resolveNip05ToLud16("_@test.domain")).thenReturn(future);

        DecodedResultDTO result = decoderAdapter.decode("test.domain");
        assertEquals("error", result.getType());
    }

    @Test
    void decode_nip05_timeoutException() {
        when(lnurlPort.getInternalMetadata("test.domain"))
                .thenThrow(new ApplicationException("not found"));
        CompletableFuture<String> future = new CompletableFuture<>();
        future.completeExceptionally(new TimeoutException("timeout"));
        when(nostrPort.resolveNip05ToLud16("_@test.domain")).thenReturn(future);

        DecodedResultDTO result = decoderAdapter.decode("test.domain");
        assertEquals("error", result.getType());
    }

    @Test
    void decode_nip05_invalidLightningAddressFormat() {
        when(lnurlPort.getInternalMetadata("test.domain"))
                .thenThrow(new ApplicationException("not found"));
        CompletableFuture<String> future = CompletableFuture.completedFuture("noatsign");
        when(nostrPort.resolveNip05ToLud16("_@test.domain")).thenReturn(future);

        assertThrows(ApplicationException.class,
                () -> decoderAdapter.decode("test.domain"));
    }

    @Test
    void decode_nip05_nullLightningAddress_returnsError() {
        when(lnurlPort.getInternalMetadata("test.domain"))
                .thenThrow(new ApplicationException("not found"));
        CompletableFuture<String> future = CompletableFuture.completedFuture(null);
        when(nostrPort.resolveNip05ToLud16("_@test.domain")).thenReturn(future);

        DecodedResultDTO result = decoderAdapter.decode("test.domain");
        assertEquals("error", result.getType());
    }

    @Test
    void decode_nip05_emptyLightningAddress_returnsError() {
        when(lnurlPort.getInternalMetadata("test.domain"))
                .thenThrow(new ApplicationException("not found"));
        CompletableFuture<String> future = CompletableFuture.completedFuture("");
        when(nostrPort.resolveNip05ToLud16("_@test.domain")).thenReturn(future);

        DecodedResultDTO result = decoderAdapter.decode("test.domain");
        assertEquals("error", result.getType());
    }
}
