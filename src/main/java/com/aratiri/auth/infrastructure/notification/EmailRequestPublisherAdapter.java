package com.aratiri.auth.infrastructure.notification;

import com.aratiri.auth.application.event.EmailRequestedEvent;
import com.aratiri.auth.application.event.EmailRequestedEvent.EmailType;
import com.aratiri.auth.application.port.out.EmailRequestPublisher;
import com.aratiri.infrastructure.messaging.EventPublisher;
import com.aratiri.infrastructure.messaging.KafkaTopicNames;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Publishes {@code email.requested} events off the HTTP thread. Fails fast with the same 503 the
 * synchronous sender used to throw when SMTP is unconfigured, so user-visible behavior of
 * register / forgot-password is unchanged.
 */
@Component
public class EmailRequestPublisherAdapter implements EmailRequestPublisher {

    private final EventPublisher eventPublisher;
    private final String username;
    private final String password;

    public EmailRequestPublisherAdapter(
            EventPublisher eventPublisher,
            @Value("${spring.mail.username:}") String username,
            @Value("${spring.mail.password:}") String password
    ) {
        this.eventPublisher = eventPublisher;
        this.username = username;
        this.password = password;
    }

    @Override
    public void publishVerificationEmail(String to, String code) {
        MailConfigurationSupport.requireConfigured(username, password);
        eventPublisher.publish(
                KafkaTopicNames.EMAIL_REQUESTED,
                EmailType.VERIFICATION + ":" + to,
                new EmailRequestedEvent(EmailType.VERIFICATION, to, code)
        );
    }

    @Override
    public void publishPasswordResetEmail(String to, String code) {
        MailConfigurationSupport.requireConfigured(username, password);
        eventPublisher.publish(
                KafkaTopicNames.EMAIL_REQUESTED,
                EmailType.PASSWORD_RESET + ":" + to,
                new EmailRequestedEvent(EmailType.PASSWORD_RESET, to, code)
        );
    }
}
