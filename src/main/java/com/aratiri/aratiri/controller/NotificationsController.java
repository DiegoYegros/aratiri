package com.aratiri.aratiri.controller;

import com.aratiri.aratiri.context.AratiriContext;
import com.aratiri.aratiri.context.AratiriCtx;
import com.aratiri.aratiri.service.NotificationsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/v1/notifications")
@Tag(name = "Notifications", description = "Real time notifications")
public class NotificationsController {

    private final NotificationsService notificationService;

    public NotificationsController(NotificationsService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping(value = "/subscribe", produces = "text/event-stream")
    @Operation(summary = "Subscribe to real time notifications",
            description = "Establishes a Server-Sent Events connection to receive notifications in real time.")
    public SseEmitter subscribe(@AratiriCtx AratiriContext ctx) {
        String userId = ctx.getUser().getId();
        return notificationService.subscribe(userId);
    }
}