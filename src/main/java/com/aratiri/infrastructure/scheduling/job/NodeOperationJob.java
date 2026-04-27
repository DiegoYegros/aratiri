package com.aratiri.infrastructure.scheduling.job;

import com.aratiri.infrastructure.nodeoperations.NodeOperationService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NodeOperationJob {

    private final NodeOperationService nodeOperationService;

    @Scheduled(fixedDelayString = "${aratiri.node-operations.fixed-delay-ms:1000}")
    public void processOperations() {
        nodeOperationService.processOperations();
    }
}
