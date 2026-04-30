package com.aratiri.infrastructure.configuration;

import io.grpc.CallCredentials.MetadataApplier;
import io.grpc.CallCredentials.RequestInfo;
import io.grpc.Metadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MacaroonCallCredentialsTest {

    @Mock
    private RequestInfo requestInfo;

    @Mock
    private Executor appExecutor;

    @Mock
    private MetadataApplier applier;

    @Test
    void applyRequestMetadata_appliesMacaroonHeader() throws Exception {
        Path macaroonFile = Files.createTempFile("test-macaroon", ".macaroon");
        Files.writeString(macaroonFile, "ab01ef");
        MacaroonCallCredentials credentials = new MacaroonCallCredentials(macaroonFile.toString());

        credentials.applyRequestMetadata(requestInfo, appExecutor, applier);

        ArgumentCaptor<Metadata> captor = ArgumentCaptor.forClass(Metadata.class);
        verify(applier).apply(captor.capture());
        Metadata metadata = captor.getValue();
        assertEquals("ab01ef", metadata.get(Metadata.Key.of("macaroon", Metadata.ASCII_STRING_MARSHALLER)));
        Files.deleteIfExists(macaroonFile);
    }

    @Test
    void loadMacaroonHex_trimsWhitespace() throws Exception {
        Path macaroonFile = Files.createTempFile("test-macaroon", ".macaroon");
        Files.writeString(macaroonFile, "  ab01ef  \n");
        MacaroonCallCredentials credentials = new MacaroonCallCredentials(macaroonFile.toString());

        credentials.applyRequestMetadata(requestInfo, appExecutor, applier);

        ArgumentCaptor<Metadata> captor = ArgumentCaptor.forClass(Metadata.class);
        verify(applier).apply(captor.capture());
        assertEquals("ab01ef", captor.getValue().get(Metadata.Key.of("macaroon", Metadata.ASCII_STRING_MARSHALLER)));
        Files.deleteIfExists(macaroonFile);
    }

    @Test
    void loadMacaroonHex_throwsWhenFileNotFound() {
        assertThrows(IllegalStateException.class,
                () -> new MacaroonCallCredentials("/nonexistent/path/to/macaroon"));
    }
}
