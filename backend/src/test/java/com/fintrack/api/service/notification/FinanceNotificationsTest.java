package com.fintrack.api.service.notification;

import static org.assertj.core.api.Assertions.*;
import com.fintrack.api.dto.response.budget.BudgetResponse;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.http.HttpStatus;

class FinanceNotificationsTest {
    final String origin="https://finance.example.test";
    @Test void dailyAndBudgetTemplatesReuseLayoutEscapeEveryVariableAndHaveExactFields() {
        var renderer=new EmailTemplateRenderer(origin+"/email/logo.png");
        var daily=FinanceEmailContent.daily(" <script>x</script> ",LocalDate.of(2026,11,9),LocalTime.of(20,0),"Asia/Ho_Chi_Minh",origin);
        FinanceEmailContent.validate("daily-reminder",daily,origin);
        String html=renderer.render("daily-reminder",daily);
        assertThat(html).contains("&lt;script&gt;x&lt;/script&gt;","09/11/2026","20:00","Quản lý thông báo","/email/logo.png").doesNotContain("{{","<script>","ttlMinutes");
        for(boolean exceeded:new boolean[]{false,true}) {
            long spent=exceeded?100_000:80_000;
            var b=BudgetResponse.of(UUID.randomUUID(),UUID.randomUUID(),"2026-11",100_000,spent,80,null,Instant.EPOCH,Instant.EPOCH);
            var vars=FinanceEmailContent.budget(null,"<b>Ăn & uống</b>",b,exceeded,origin);
            String template=exceeded?"budget-exceeded":"budget-warning";
            FinanceEmailContent.validate(template,vars,origin);
            String rendered=renderer.render(template,vars);
            assertThat(rendered).contains("bạn","&lt;b&gt;Ăn &amp; uống&lt;/b&gt;","100.000 đ").doesNotContain("<b>Ăn","{{");
            if(exceeded) assertThat(vars.get("overAmount")).isEqualTo("0 đ");
            vars.remove("monthLabel"); assertThatThrownBy(()->FinanceEmailContent.validate(template,vars,origin)).isInstanceOf(IllegalArgumentException.class);
        }
    }
    @Test void unsafeOriginsSubjectsLinksAndUnknownTemplatesAreRejected() {
        for(String url:List.of("http://example.test","javascript:alert(1)","https://user:secret@example.test","https://example.test/path","https://example.test?token=x"))
            assertThatThrownBy(()->FinanceEmailContent.origin(url)).isInstanceOf(IllegalArgumentException.class);
        assertThat(FinanceEmailContent.subject("hello\r\nBcc:x")).isEqualTo("hello  Bcc:x");
        var vars=FinanceEmailContent.daily("",LocalDate.now(),LocalTime.NOON,"UTC",origin);
        vars.put("actionUrl","javascript:alert(1)");
        assertThatThrownBy(()->FinanceEmailContent.validate("daily-reminder",vars,origin)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->FinanceEmailContent.validate("forgot-password",vars,origin)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(()->FinanceEmailContent.money(-1)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void dailyDstGapSelectsFirstValidInstantAndOverlapUsesEarlierOffset() {
        ZoneId ny=ZoneId.of("America/New_York");
        assertThat(FinanceNotificationProducer.due(LocalDate.of(2026,3,8),LocalTime.of(2,30),ny)).isEqualTo(Instant.parse("2026-03-08T07:00:00Z"));
        assertThat(FinanceNotificationProducer.due(LocalDate.of(2026,11,1),LocalTime.of(1,30),ny)).isEqualTo(Instant.parse("2026-11-01T05:30:00Z"));
        assertThat(FinanceNotificationProducer.due(LocalDate.of(2028,2,29),LocalTime.MIDNIGHT,ZoneId.of("UTC"))).isEqualTo(Instant.parse("2028-02-29T00:00:00Z"));
    }
    @Test void providerFailuresAreClassifiedWithoutPersistingPayloadAndRetryAfterIsBoundedByWorker() {
        Instant now=Instant.parse("2026-11-09T21:00:00Z"); HttpHeaders headers=new HttpHeaders();headers.set("Retry-After","120");
        var rate=HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS,"secret",headers,new byte[0],StandardCharsets.UTF_8);
        var failure=NotificationOutboxWorker.classify(rate,now);
        assertThat(failure.transientFailure()).isTrue();assertThat(failure.retryAfter()).isEqualTo(now.plusSeconds(120));
        assertThat(NotificationOutboxWorker.classify(new ResourceAccessException("raw address secret"),now).reason()).isEqualTo("NETWORK");
        assertThat(NotificationOutboxWorker.classify(HttpServerErrorException.create(HttpStatus.BAD_GATEWAY,"",null,null,null),now).transientFailure()).isTrue();
        for(String body:List.of("invalid_idempotent_request","concurrent_idempotent_requests")) {
            var conflict=HttpClientErrorException.create(HttpStatus.CONFLICT,"",null,body.getBytes(StandardCharsets.UTF_8),StandardCharsets.UTF_8);
            assertThat(NotificationOutboxWorker.classify(conflict,now).transientFailure()).isEqualTo(body.startsWith("concurrent"));
        }
        assertThat(NotificationOutboxWorker.classify(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED,"raw secret",null,null,null),now).reason()).isEqualTo("PROVIDER_PERMANENT");
        assertThat(NotificationOutboxWorker.classify(new IllegalArgumentException("missing template secret"),now).transientFailure()).isFalse();
    }
}
