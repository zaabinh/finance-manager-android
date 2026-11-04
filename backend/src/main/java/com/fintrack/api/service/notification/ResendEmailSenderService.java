package com.fintrack.api.service.notification;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@Profile("!test")
public class ResendEmailSenderService implements EmailSenderService {
    private final RestClient client;
    private final String from;
    private final String apiKey;
    @org.springframework.beans.factory.annotation.Autowired
    public ResendEmailSenderService(
        @Value("${app.mail.resend-api-key:}") String apiKey,
        @Value("${app.mail.from:}") String from, Environment environment
    ) {
        if ((apiKey.isBlank() || from.isBlank()) && !environment.acceptsProfiles(Profiles.of("dev & !prod & !staging"))) {
            throw new IllegalArgumentException("Configure RESEND_API_KEY and MAIL_FROM");
        }
        this.apiKey = apiKey; this.from = from;
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.client = RestClient.builder().baseUrl("https://api.resend.com").requestFactory(factory)
                .defaultHeader("Authorization", "Bearer " + apiKey).build();
    }
    // Separate constructor for HTTP contract tests; production always uses the fixed HTTPS endpoint.
    ResendEmailSenderService(RestClient client, String from) { this.client = client; this.from = from; this.apiKey = "test"; }

    @Override public void send(String recipient, String subject, String htmlBody, UUID deliveryId) {
        if (apiKey.isBlank() || from.isBlank())
            throw new IllegalStateException("Email sender is not configured");
        client.post().uri("/emails").header("Idempotency-Key", "email/" + deliveryId)
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("from", from, "to", List.of(recipient),
                        "subject", subject, "html", htmlBody))
                .retrieve().toBodilessEntity();
    }
}
