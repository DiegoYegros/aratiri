package com.aratiri.webhooks.application;

import com.aratiri.infrastructure.persistence.jpa.entity.WebhookDeliveryEntity;
import com.aratiri.infrastructure.persistence.jpa.entity.WebhookEndpointEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

@Component
public class JavaHttpWebhookDeliverySender implements WebhookDeliverySender {

    private static final String USER_AGENT = "Aratiri-Webhooks/1.0";
    private static final String SIGNATURE_VERSION = "v1";
    private static final long CONNECT_TIMEOUT_SECONDS = 10;
    private static final long READ_TIMEOUT_SECONDS = 10;

    private final HttpClient httpClient;

    @Autowired
    public JavaHttpWebhookDeliverySender() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    JavaHttpWebhookDeliverySender(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public WebhookSendResult send(WebhookDeliveryEntity delivery, WebhookEndpointEntity endpoint)
            throws IOException, InterruptedException {
        String payload = delivery.getPayload() != null ? delivery.getPayload() : "";
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String signature = generateSignature(endpoint.getSigningSecret(), timestamp, delivery.getEventId().toString(), payload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint.getUrl()))
                .header("Content-Type", "application/json")
                .header("User-Agent", USER_AGENT)
                .header("X-Aratiri-Event-Id", delivery.getEventId().toString())
                .header("X-Aratiri-Event-Type", delivery.getEventType())
                .header("X-Aratiri-Delivery-Id", delivery.getId().toString())
                .header("X-Aratiri-Timestamp", timestamp)
                .header("X-Aratiri-Signature", signature)
                .timeout(Duration.ofSeconds(READ_TIMEOUT_SECONDS))
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return new WebhookSendResult(response.statusCode(), response.body());
    }

    static String generateSignature(String secret, String timestamp, String eventId, String payload) {
        try {
            String signedPayload = timestamp + "." + eventId + "." + payload;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));
            return SIGNATURE_VERSION + "=" + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to generate webhook signature", e);
        }
    }
}
