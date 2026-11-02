package com.fintrack.api.service.notification;

import java.util.UUID;

public interface EmailSenderService {
    /**
     * Sends one email. {@code htmlBody} is a complete HTML document, usually from {@link EmailTemplateRenderer}.
     * {@code deliveryId} makes provider retries idempotent.
     */
    void send(String recipient, String subject, String htmlBody, UUID deliveryId);
}
