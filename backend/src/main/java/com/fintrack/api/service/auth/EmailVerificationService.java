package com.fintrack.api.service.auth;

import com.fintrack.api.config.AuthProperties;
import com.fintrack.api.exception.ChallengeRejectedException;
import com.fintrack.api.exception.ErrorCode;
import com.fintrack.api.repository.VerificationChallengeRepository;
import com.fintrack.api.repository.VerificationChallengeRepository.Purpose;
import com.fintrack.api.service.notification.EmailSenderService;
import com.fintrack.api.service.notification.EmailTemplateRenderer;
import java.security.SecureRandom;
import java.time.Clock;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/** Issues, emails and checks one-time codes for email verification and password reset. */
@Service
@Profile("!test")
@RequiredArgsConstructor
public class EmailVerificationService {
    static final int MAX_ATTEMPTS = 5;
    private final VerificationChallengeRepository challenges;
    private final PasswordEncoder encoder;
    private final ApplicationEventPublisher events;
    private final AuthProperties properties;
    private final Clock clock;
    private final EmailSenderService sender;
    private final EmailTemplateRenderer templates;
    private final SecureRandom random = new SecureRandom();

    /** Replaces any open code for this purpose; the email is sent only after the caller's transaction commits. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void issue(UUID userId, String email, Purpose purpose) {
        var now = clock.instant();
        String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        challenges.invalidate(userId, purpose, now);
        challenges.create(userId, email, purpose, encoder.encode(code), now, now.plus(properties.otpTtl()));
        events.publishEvent(new CodeEmail(email, code, purpose, UUID.randomUUID()));
    }

    /** Consumes the latest open code or throws; a wrong code still commits the failed-attempt count. */
    @Transactional(propagation = Propagation.MANDATORY, noRollbackFor = ChallengeRejectedException.class)
    public void verify(UUID userId, Purpose purpose, String code) {
        var challenge = challenges.lockLatest(userId, purpose)
                .orElseThrow(() -> rejected(HttpStatus.BAD_REQUEST, ErrorCode.AUTH_OTP_INVALID, "Invalid code"));
        if (!challenge.expiresAt().isAfter(clock.instant()))
            throw rejected(HttpStatus.GONE, ErrorCode.AUTH_OTP_EXPIRED, "Code has expired");
        if (challenge.attempts() >= MAX_ATTEMPTS)
            throw rejected(HttpStatus.TOO_MANY_REQUESTS, ErrorCode.AUTH_OTP_ATTEMPTS_EXCEEDED, "Too many code attempts");
        if (!encoder.matches(code, challenge.codeHash())) {
            challenges.fail(challenge.id());
            boolean last = challenge.attempts() == MAX_ATTEMPTS - 1;
            throw rejected(last ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.BAD_REQUEST,
                    last ? ErrorCode.AUTH_OTP_ATTEMPTS_EXCEEDED : ErrorCode.AUTH_OTP_INVALID, "Invalid code");
        }
        challenges.consume(challenge.id(), clock.instant());
    }

    @TransactionalEventListener
    public void deliver(CodeEmail email) {
        boolean reset = email.purpose() == Purpose.PASSWORD_RESET;
        String subject = reset ? "Mã đặt lại mật khẩu Đồng" : "Mã xác minh email Đồng";
        String minutes = String.valueOf(properties.otpTtl().toMinutes());
        try {
            String html = templates.render(reset ? "forgot-password" : "otp-verification", Map.of(
                    "title", subject,
                    "preheader", "Mã của bạn có hiệu lực trong " + minutes + " phút.",
                    "code", email.code(),
                    "ttlMinutes", minutes));
            sender.send(email.recipient(), subject, html, email.deliveryId());
        } catch (RuntimeException exception) {
            // Provider exceptions may contain request bodies, addresses or credentials.
            LoggerFactory.getLogger(EmailVerificationService.class)
                    .warn("Verification email delivery failed; deliveryId={}", email.deliveryId());
        }
    }

    private static ChallengeRejectedException rejected(HttpStatus status, ErrorCode code, String message) {
        return new ChallengeRejectedException(status, code, message);
    }

    public record CodeEmail(String recipient, String code, Purpose purpose, UUID deliveryId) {
        @Override public String toString() { return "CodeEmail[deliveryId=" + deliveryId + "]"; }
    }
}
