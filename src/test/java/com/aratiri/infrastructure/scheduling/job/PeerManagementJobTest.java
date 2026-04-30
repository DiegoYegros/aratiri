package com.aratiri.infrastructure.scheduling.job;

import com.aratiri.admin.application.dto.*;
import com.aratiri.admin.application.port.in.AdminPort;
import com.aratiri.admin.application.port.out.NodeSettingsPort;
import com.aratiri.admin.domain.NodeSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PeerManagementJobTest {

    @Mock
    private AdminPort adminPort;

    @Mock
    private NodeSettingsPort nodeSettingsPort;

    @InjectMocks
    private PeerManagementJob job;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(job, "targetPeerCount", 20);
    }

    @Test
    void managePeers_skipsWhenDisabled() {
        when(nodeSettingsPort.loadSettings())
                .thenReturn(new NodeSettings(false, 60000L, Instant.now(), Instant.now()));

        job.managePeers();

        verify(adminPort, never()).listPeers();
    }

    @Test
    void managePeers_skipsWhenSettingsNull() {
        when(nodeSettingsPort.loadSettings()).thenReturn(null);

        job.managePeers();

        verify(adminPort, never()).listPeers();
    }

    @Test
    void managePeers_skipsWhenEnoughPeers() {
        // Return 20+ peers so target is met
        List<PeerDTO> manyPeers = java.util.stream.IntStream.range(0, 25)
                .mapToObj(i -> PeerDTO.builder().pubKey("key" + i).address("addr" + i).build())
                .toList();
        when(nodeSettingsPort.loadSettings())
                .thenReturn(new NodeSettings(true, 60000L, Instant.now(), Instant.now()));
        when(adminPort.listPeers()).thenReturn(manyPeers);

        job.managePeers();

        verify(adminPort, never()).listNodes();
    }

    @Test
    void managePeers_connectsToRecommendedNodes() {
        PeerDTO peer = PeerDTO.builder().pubKey("key1").address("addr1").build();
        NodeInfoDTO recommended = NodeInfoDTO.builder()
                .pubKey("key2").alias("node").addresses(List.of("127.0.0.1:9735")).build();
        RemotesResponseDTO remotes = new RemotesResponseDTO(List.of(recommended));

        when(nodeSettingsPort.loadSettings())
                .thenReturn(new NodeSettings(true, 60000L, Instant.now(), Instant.now()));
        when(adminPort.listPeers()).thenReturn(List.of(peer));
        when(adminPort.listNodes()).thenReturn(remotes);
        doNothing().when(adminPort).connectPeer(any());

        job.managePeers();

        verify(adminPort).connectPeer(any());
    }

    @Test
    void managePeers_handlesExceptionGracefully() {
        when(nodeSettingsPort.loadSettings())
                .thenReturn(new NodeSettings(true, 60000L, Instant.now(), Instant.now()));
        when(adminPort.listPeers()).thenThrow(new RuntimeException("network error"));

        assertDoesNotThrow(() -> job.managePeers());
    }
}
