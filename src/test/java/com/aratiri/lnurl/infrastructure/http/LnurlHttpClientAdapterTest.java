package com.aratiri.lnurl.infrastructure.http;

import com.aratiri.infrastructure.http.destination.OutboundDestinationPolicy;
import com.aratiri.infrastructure.http.destination.OutboundDestinationRejectedException;
import com.aratiri.lnurl.application.dto.LnurlCallbackResponseDTO;
import com.aratiri.lnurl.application.dto.LnurlpResponseDTO;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LnurlHttpClientAdapterTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private OutboundDestinationPolicy outboundDestinationPolicy;

    private LnurlHttpClientAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new LnurlHttpClientAdapter(restTemplate, outboundDestinationPolicy);
    }

    @Test
    void fetchMetadata_returnsResponse() {
        LnurlpResponseDTO expected = new LnurlpResponseDTO();
        expected.setCallback("https://example.com/callback");
        when(restTemplate.getForObject("https://example.com/lnurl", LnurlpResponseDTO.class))
                .thenReturn(expected);

        LnurlpResponseDTO result = adapter.fetchMetadata("https://example.com/lnurl");

        assertEquals(expected, result);
        verify(outboundDestinationPolicy, times(1)).validate("https://example.com/lnurl");
        verify(restTemplate, times(1)).getForObject("https://example.com/lnurl", LnurlpResponseDTO.class);
    }

    @Test
    void fetchCallbackInvoice_returnsResponse() {
        LnurlCallbackResponseDTO expected = new LnurlCallbackResponseDTO();
        expected.setPaymentRequest("lnbc1...");
        when(restTemplate.getForObject("https://example.com/callback?amount=1000", LnurlCallbackResponseDTO.class))
                .thenReturn(expected);

        LnurlCallbackResponseDTO result = adapter.fetchCallbackInvoice("https://example.com/callback?amount=1000");

        assertEquals(expected, result);
        verify(outboundDestinationPolicy).validate("https://example.com/callback?amount=1000");
    }

    @Test
    void fetchMetadata_rejectsBeforeHttp() {
        doThrow(new OutboundDestinationRejectedException())
                .when(outboundDestinationPolicy).validate("https://127.0.0.1/lnurl");

        assertThrows(OutboundDestinationRejectedException.class,
                () -> adapter.fetchMetadata("https://127.0.0.1/lnurl"));
        verify(restTemplate, never()).getForObject(anyString(), eq(LnurlpResponseDTO.class));
    }

    @Test
    void fetchCallbackInvoice_rejectsPrivateBeforeHttp() {
        doThrow(new OutboundDestinationRejectedException())
                .when(outboundDestinationPolicy).validate("https://169.254.169.254/latest");

        assertThrows(OutboundDestinationRejectedException.class,
                () -> adapter.fetchCallbackInvoice("https://169.254.169.254/latest"));
        verify(restTemplate, never()).getForObject(anyString(), eq(LnurlCallbackResponseDTO.class));
    }

    @Test
    void fetchMetadata_rejectsRfc1918BeforeHttp() {
        doThrow(new OutboundDestinationRejectedException())
                .when(outboundDestinationPolicy).validate("https://192.168.1.1/lnurl");

        assertThrows(OutboundDestinationRejectedException.class,
                () -> adapter.fetchMetadata("https://192.168.1.1/lnurl"));
        verify(restTemplate, never()).getForObject(anyString(), eq(LnurlpResponseDTO.class));
    }

    @Test
    void outboundRestTemplate_doesNotFollowRedirectToPrivate() throws Exception {
        AtomicInteger privateHits = new AtomicInteger();
        HttpServer privateServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        privateServer.createContext("/", exchange -> {
            privateHits.incrementAndGet();
            byte[] body = "SECRET".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        privateServer.start();

        HttpServer publicServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int privatePort = privateServer.getAddress().getPort();
        publicServer.createContext("/meta", exchange -> {
            exchange.getResponseHeaders().add("Location", "http://127.0.0.1:" + privatePort + "/");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        publicServer.start();

        try {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
                @Override
                protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
                    super.prepareConnection(connection, httpMethod);
                    connection.setInstanceFollowRedirects(false);
                }
            };
            RestTemplate noRedirect = new RestTemplate(factory);
            OutboundDestinationPolicy permissive = org.mockito.Mockito.mock(OutboundDestinationPolicy.class);
            LnurlHttpClientAdapter redirectAdapter = new LnurlHttpClientAdapter(noRedirect, permissive);

            String url = "http://127.0.0.1:" + publicServer.getAddress().getPort() + "/meta";
            try {
                redirectAdapter.fetchMetadata(url);
            } catch (RestClientException _) {
                // non-success is fine; privateHits must stay zero
            }
            assertEquals(0, privateHits.get());
        } finally {
            publicServer.stop(0);
            privateServer.stop(0);
        }
    }
}
