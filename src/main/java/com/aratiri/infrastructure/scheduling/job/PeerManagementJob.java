package com.aratiri.infrastructure.scheduling.job;

import com.aratiri.admin.application.dto.ConnectPeerRequestDTO;
import com.aratiri.admin.application.dto.NodeInfoDTO;
import com.aratiri.admin.application.dto.PeerDTO;
import com.aratiri.admin.application.port.in.AdminPort;
import com.aratiri.admin.application.port.out.NodeSettingsPort;
import com.aratiri.admin.domain.NodeSettings;
import com.aratiri.shared.exception.AratiriException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class PeerManagementJob {

    private static final Logger logger = LoggerFactory.getLogger(PeerManagementJob.class);

    private final AdminPort adminPort;
    private final NodeSettingsPort nodeSettingsPort;

    @Value("${aratiri.peer.management.target.count:20}")
    private int targetPeerCount;

    public PeerManagementJob(AdminPort adminPort, NodeSettingsPort nodeSettingsPort) {
        this.adminPort = adminPort;
        this.nodeSettingsPort = nodeSettingsPort;
    }

    // once a day
    @Scheduled(fixedDelayString = "${aratiri.peer.management.interval:86400000}")
    public void managePeers() {
        NodeSettings settings = nodeSettingsPort.loadSettings();
        if (settings == null || !settings.autoManagePeers()) {
            logger.debug("Automatic peer management is disabled in node settings. Skipping job.");
            return;
        }

        logger.info("Starting automatic peer management job. Target peer count: {}", targetPeerCount);

        try {
            List<PeerDTO> currentPeers = adminPort.listPeers();
            Set<String> currentPeerPubkeys = currentPeers.stream()
                    .map(PeerDTO::getPubKey)
                    .collect(Collectors.toSet());
            int peersToConnect = targetPeerCount - currentPeers.size();

            if (peersToConnect <= 0) {
                logger.info("Already connected to {} peers (target is {}). No new connections needed.", currentPeers.size(), targetPeerCount);
                return;
            }

            logger.info("Need to connect to {} more peers.", peersToConnect);

            List<NodeInfoDTO> recommendedNodes = adminPort.listNodes().getNodes();
            int connectedCount = connectRecommendedPeers(recommendedNodes, currentPeerPubkeys, peersToConnect);
            logger.info("Finished automatic peer management job. Initiated {} new connections.", connectedCount);
        } catch (Exception e) {
            logger.error("Error during automatic peer management job: {}", e.getMessage(), e);
        }
    }

    private int connectRecommendedPeers(List<NodeInfoDTO> recommendedNodes, Set<String> currentPeerPubkeys, int peersToConnect) {
        int connectedCount = 0;
        for (int i = 0; i < recommendedNodes.size() && connectedCount < peersToConnect; i++) {
            NodeInfoDTO node = recommendedNodes.get(i);
            if (connectIfEligible(node, currentPeerPubkeys)) {
                connectedCount++;
            }
        }
        return connectedCount;
    }

    private boolean connectIfEligible(NodeInfoDTO node, Set<String> currentPeerPubkeys) {
        if (currentPeerPubkeys.contains(node.getPubKey())) {
            return false;
        }
        if (node.getAddresses() == null || node.getAddresses().isEmpty()) {
            logger.warn("Skipping node {} ({}). No addresses available.", node.getAlias(), node.getPubKey());
            return false;
        }
        return connectNode(node, currentPeerPubkeys);
    }

    private boolean connectNode(NodeInfoDTO node, Set<String> currentPeerPubkeys) {
        String host = node.getAddresses().getFirst();
        try {
            logger.info("Attempting to connect to peer: {} ({}) at {}", node.getAlias(), node.getPubKey(), host);
            ConnectPeerRequestDTO connectRequest = new ConnectPeerRequestDTO();
            connectRequest.setPubkey(node.getPubKey());
            connectRequest.setHost(host);
            adminPort.connectPeer(connectRequest);
            logger.info("Successfully initiated connection to {}", node.getAlias());
            currentPeerPubkeys.add(node.getPubKey());
            return true;
        } catch (AratiriException e) {
            logger.warn("Failed to connect to peer {} ({}): {}", node.getAlias(), node.getPubKey(), e.getMessage());
        } catch (Exception e) {
            logger.error("Unexpected error connecting to peer {} ({}): {}", node.getAlias(), node.getPubKey(), e.getMessage(), e);
        }
        return false;
    }
}
