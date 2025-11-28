package com.aratiri.infrastructure.scheduling.job;

import com.aratiri.admin.application.dto.ConnectPeerRequestDTO;
import com.aratiri.admin.application.dto.NodeInfoDTO;
import com.aratiri.admin.application.dto.NodeSettingsDTO;
import com.aratiri.admin.application.port.in.AdminPort;
import com.aratiri.shared.exception.AratiriException;
import lnrpc.Peer;
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

    @Value("${aratiri.peer.management.target.count:20}")
    private int targetPeerCount;

    public PeerManagementJob(AdminPort adminPort) {
        this.adminPort = adminPort;
    }

    // once a day
    @Scheduled(fixedDelayString = "${aratiri.peer.management.interval:86400000}")
    public void managePeers() {
        NodeSettingsDTO settings = adminPort.getNodeSettings();
        if (settings == null || !settings.isAutoManagePeers()) {
            logger.debug("Automatic peer management is disabled in node settings. Skipping job.");
            return;
        }

        logger.info("Starting automatic peer management job. Target peer count: {}", targetPeerCount);

        try {
            List<Peer> currentPeers = adminPort.listPeers();
            Set<String> currentPeerPubkeys = currentPeers.stream()
                    .map(Peer::getPubKey)
                    .collect(Collectors.toSet());
            int peersToConnect = targetPeerCount - currentPeers.size();

            if (peersToConnect <= 0) {
                logger.info("Already connected to {} peers (target is {}). No new connections needed.", currentPeers.size(), targetPeerCount);
                return;
            }

            logger.info("Need to connect to {} more peers.", peersToConnect);

            List<NodeInfoDTO> recommendedNodes = adminPort.listNodes().getNodes();
            int connectedCount = 0;
            for (NodeInfoDTO node : recommendedNodes) {
                if (connectedCount >= peersToConnect) {
                    break;
                }

                if (!currentPeerPubkeys.contains(node.getPubKey())) {
                    if (node.getAddresses() == null || node.getAddresses().isEmpty()) {
                        logger.warn("Skipping node {} ({}).no addresses available.", node.getAlias(), node.getPubKey());
                        continue;
                    }
                    String host = node.getAddresses().get(0);
                    try {
                        logger.info("Attempting to connect to peer: {} ({}) at {}", node.getAlias(), node.getPubKey(), host);
                        ConnectPeerRequestDTO connectRequest = new ConnectPeerRequestDTO();
                        connectRequest.setPubkey(node.getPubKey());
                        connectRequest.setHost(host);
                        adminPort.connectPeer(connectRequest);
                        logger.info("Successfully initiated connection to {}", node.getAlias());
                        connectedCount++;
                        currentPeerPubkeys.add(node.getPubKey());
                    } catch (AratiriException e) {
                        logger.warn("Failed to connect to peer {} ({}): {}", node.getAlias(), node.getPubKey(), e.getMessage());
                    } catch (Exception e) {
                        logger.error("Unexpected error connecting to peer {} ({}): {}", node.getAlias(), node.getPubKey(), e.getMessage(), e);
                    }
                }
            }
            logger.info("Finished automatic peer management job. Initiated {} new connections.", connectedCount);
        } catch (Exception e) {
            logger.error("Error during automatic peer management job: {}", e.getMessage(), e);
        }
    }
}