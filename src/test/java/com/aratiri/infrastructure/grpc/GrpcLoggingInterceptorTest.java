package com.aratiri.infrastructure.grpc;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.Status;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GrpcLoggingInterceptorTest {

    private static final String METHOD_NAME = "lnrpc.Lightning/SendPaymentSync";
    private static final String MACAROON_SENTINEL = "SENTINEL-GRPC-MACAROON-HEX-deadbeef";
    private static final String REQUEST_SENTINEL = "SENTINEL-GRPC-PAYMENT-REQUEST-lnbc1";
    private static final String RESPONSE_SENTINEL = "SENTINEL-GRPC-PREIMAGE-aabbccdd";
    private static final String ADDRESS_SENTINEL = "SENTINEL-GRPC-ADDRESS-bc1qxy";

    private final GrpcLoggingInterceptor interceptor = new GrpcLoggingInterceptor();

    @Mock
    private Channel channel;

    @Mock
    private ClientCall<String, String> clientCall;

    private Logger logger;
    private Level previousLevel;
    private ListAppender<ILoggingEvent> listAppender;
    private MethodDescriptor<String, String> methodDescriptor;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(GrpcLoggingInterceptor.class);
        previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);

        methodDescriptor = MethodDescriptor.<String, String>newBuilder()
                .setType(MethodDescriptor.MethodType.UNARY)
                .setFullMethodName(METHOD_NAME)
                .setRequestMarshaller(stringMarshaller())
                .setResponseMarshaller(stringMarshaller())
                .build();

        @SuppressWarnings("unchecked")
        ClientCall<Object, Object> uncheckedCall = (ClientCall<Object, Object>) (ClientCall<?, ?>) clientCall;
        when(channel.newCall(any(), any())).thenReturn(uncheckedCall);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(listAppender);
        listAppender.stop();
        logger.setLevel(previousLevel);
    }

    @Test
    void doesNotLogMetadataOrProtobufPayloadsAtInfoOrDebug() {
        ClientCall<String, String> intercepted = interceptor.interceptCall(
                methodDescriptor, CallOptions.DEFAULT, channel);

        Metadata headers = new Metadata();
        headers.put(Metadata.Key.of("macaroon", Metadata.ASCII_STRING_MARSHALLER), MACAROON_SENTINEL);
        headers.put(Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER), "Bearer " + MACAROON_SENTINEL);
        headers.put(Metadata.Key.of("custom-token-bin", Metadata.BINARY_BYTE_MARSHALLER),
                MACAROON_SENTINEL.getBytes(StandardCharsets.UTF_8));

        ArgumentCaptor<ClientCall.Listener<String>> listenerCaptor =
                ArgumentCaptor.forClass(ClientCall.Listener.class);

        @SuppressWarnings("unchecked")
        ClientCall.Listener<String> downstream = mock(ClientCall.Listener.class);
        intercepted.start(downstream, headers);
        verify(clientCall).start(listenerCaptor.capture(), eq(headers));

        String requestPayload = "payment_request=" + REQUEST_SENTINEL + " address=" + ADDRESS_SENTINEL;
        intercepted.sendMessage(requestPayload);
        verify(clientCall).sendMessage(requestPayload);

        String responseMessage = "payment_preimage=" + RESPONSE_SENTINEL;
        Metadata trailers = new Metadata();
        trailers.put(Metadata.Key.of("grpc-status-details", Metadata.ASCII_STRING_MARSHALLER), "ok");

        ClientCall.Listener<String> forwardingListener = listenerCaptor.getValue();
        forwardingListener.onMessage(responseMessage);
        forwardingListener.onClose(Status.OK, trailers);

        verify(downstream).onMessage(responseMessage);
        ArgumentCaptor<Status> statusCaptor = ArgumentCaptor.forClass(Status.class);
        ArgumentCaptor<Metadata> trailersCaptor = ArgumentCaptor.forClass(Metadata.class);
        verify(downstream).onClose(statusCaptor.capture(), trailersCaptor.capture());
        assertEquals(Status.Code.OK, statusCaptor.getValue().getCode());
        assertEquals("ok", trailersCaptor.getValue()
                .get(Metadata.Key.of("grpc-status-details", Metadata.ASCII_STRING_MARSHALLER)));

        String captured = capturedLogText();
        assertFalse(captured.isBlank(), "expected operational log output");
        assertTrue(captured.contains(METHOD_NAME), "safe method metadata missing");
        assertTrue(captured.contains("OK") || captured.contains("gRPC CALL STATUS"),
                "safe status metadata missing");

        assertSentinelAbsent(captured, MACAROON_SENTINEL);
        assertSentinelAbsent(captured, REQUEST_SENTINEL);
        assertSentinelAbsent(captured, RESPONSE_SENTINEL);
        assertSentinelAbsent(captured, ADDRESS_SENTINEL);
        assertFalse(captured.contains("payment_request="), "request protobuf must not be logged");
        assertFalse(captured.contains("payment_preimage="), "response protobuf must not be logged");
    }

    @Test
    void logsStatusCodeWithoutErrorDescriptionPayload() {
        ClientCall<String, String> intercepted = interceptor.interceptCall(
                methodDescriptor, CallOptions.DEFAULT, channel);

        ArgumentCaptor<ClientCall.Listener<String>> listenerCaptor =
                ArgumentCaptor.forClass(ClientCall.Listener.class);
        @SuppressWarnings("unchecked")
        ClientCall.Listener<String> downstream = mock(ClientCall.Listener.class);
        intercepted.start(downstream, new Metadata());
        verify(clientCall).start(listenerCaptor.capture(), any(Metadata.class));

        String errorDescription = "invoice=" + REQUEST_SENTINEL + " preimage=" + RESPONSE_SENTINEL;
        Status errorStatus = Status.INTERNAL.withDescription(errorDescription);
        Metadata trailers = new Metadata();
        trailers.put(Metadata.Key.of("error-trailer", Metadata.ASCII_STRING_MARSHALLER), "internal");

        listenerCaptor.getValue().onClose(errorStatus, trailers);

        ArgumentCaptor<Status> statusCaptor = ArgumentCaptor.forClass(Status.class);
        ArgumentCaptor<Metadata> trailersCaptor = ArgumentCaptor.forClass(Metadata.class);
        verify(downstream).onClose(statusCaptor.capture(), trailersCaptor.capture());
        assertEquals(Status.Code.INTERNAL, statusCaptor.getValue().getCode());
        assertEquals(errorDescription, statusCaptor.getValue().getDescription());
        assertEquals("internal", trailersCaptor.getValue()
                .get(Metadata.Key.of("error-trailer", Metadata.ASCII_STRING_MARSHALLER)));

        String captured = capturedLogText();
        assertTrue(captured.contains(METHOD_NAME));
        assertTrue(captured.contains("INTERNAL"));
        assertSentinelAbsent(captured, REQUEST_SENTINEL);
        assertSentinelAbsent(captured, RESPONSE_SENTINEL);
        assertSentinelAbsent(captured, errorDescription);
    }

    private String capturedLogText() {
        return listAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));
    }

    private static void assertSentinelAbsent(String captured, String sentinel) {
        assertFalse(captured.contains(sentinel),
                () -> "captured logs must not contain sentinel: " + sentinel + "\nlogs:\n" + captured);
    }

    private static MethodDescriptor.Marshaller<String> stringMarshaller() {
        return new MethodDescriptor.Marshaller<>() {
            @Override
            public InputStream stream(String value) {
                return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
            }

            @Override
            public String parse(InputStream stream) {
                try {
                    return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }
}
