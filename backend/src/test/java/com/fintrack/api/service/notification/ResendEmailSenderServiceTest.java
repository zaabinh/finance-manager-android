package com.fintrack.api.service.notification;

import com.fintrack.api.config.AuthProperties;
import com.fintrack.api.repository.VerificationChallengeRepository;
import com.fintrack.api.repository.VerificationChallengeRepository.Purpose;
import com.fintrack.api.service.auth.EmailVerificationService;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ResendEmailSenderServiceTest {
    @Test void sendsHtmlPayloadWithIdempotencyKeyAndHandlesProviderFailure() {
        var builder = RestClient.builder().baseUrl("https://api.resend.com").defaultHeader("Authorization", "Bearer test-key");
        var server = MockRestServiceServer.bindTo(builder).build();
        var sender = new ResendEmailSenderService(builder.build(), "FinTrack <auth@example.test>");
        UUID delivery = UUID.randomUUID();
        server.expect(requestTo("https://api.resend.com/emails")).andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-key")).andExpect(header("Idempotency-Key", "email/" + delivery))
                .andExpect(jsonPath("$.to[0]").value("user@example.test"))
                .andExpect(jsonPath("$.subject").value("Subject"))
                .andExpect(jsonPath("$.html").value("<p>Body</p>"))
                .andRespond(withSuccess("{\"id\":\"test\"}", MediaType.APPLICATION_JSON));
        sender.send("user@example.test", "Subject", "<p>Body</p>", delivery);
        server.verify(); server.reset();
        server.expect(requestTo("https://api.resend.com/emails")).andRespond(withServerError());
        assertThatThrownBy(() -> sender.send("user@example.test", "Subject", "<p>Body</p>", UUID.randomUUID()))
                .isInstanceOf(org.springframework.web.client.RestClientException.class);
        server.verify();
    }
    @Test void verificationDeliveryRendersCodeAndSwallowsProviderFailure() {
        var sender = mock(EmailSenderService.class);
        var properties = new AuthProperties("test", Duration.ofMinutes(15), Duration.ofDays(30), Duration.ofMinutes(10), null, null, Duration.ofSeconds(20));
        var service = new EmailVerificationService(mock(VerificationChallengeRepository.class), new BCryptPasswordEncoder(4),
                mock(ApplicationEventPublisher.class), properties, Clock.systemUTC(), sender,
                new EmailTemplateRenderer("https://cdn.example.test/logo.png"));
        var email = new EmailVerificationService.CodeEmail("user@example.test", "123456", Purpose.PASSWORD_RESET, UUID.randomUUID());
        service.deliver(email);
        verify(sender).send(eq("user@example.test"), contains("mật khẩu"),
                argThat(html -> html.contains(">123456<") && html.contains("10 phút")), eq(email.deliveryId()));
        doThrow(new IllegalStateException("sensitive provider error")).when(sender).send(any(), any(), any(), any());
        assertThatCode(() -> service.deliver(email)).doesNotThrowAnyException();
    }
}
