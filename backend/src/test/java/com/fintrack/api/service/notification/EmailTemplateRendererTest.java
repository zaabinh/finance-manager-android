package com.fintrack.api.service.notification;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class EmailTemplateRendererTest {
    private final EmailTemplateRenderer renderer = new EmailTemplateRenderer("https://cdn.example.test/logo.png?v=1&s=2");

    @Test void wrapsContentInSharedLayoutAndEscapesValues() {
        for (String template : new String[] {"otp-verification", "forgot-password"}) {
            String html = renderer.render(template, Map.of("title", "<Tiêu đề>", "preheader", "Xem trước",
                    "code", "<b>1234</b>", "ttlMinutes", "10"));
            assertThat(html).startsWith("<!DOCTYPE html>").contains("<title>&lt;Tiêu đề&gt;</title>")
                    .contains("&lt;b&gt;1234&lt;/b&gt;").doesNotContain("<b>1234</b>")
                    .contains("src=\"https://cdn.example.test/logo.png?v=1&amp;s=2\"").doesNotContain("{{");
        }
    }

    @Test void missingValueFailsInsteadOfSendingRawPlaceholder() {
        assertThatThrownBy(() -> renderer.render("otp-verification", Map.of("title", "t", "preheader", "p")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("code");
    }

    @Test void verificationGreetsTheNamedRecipientAndEscapesHtml() {
        String html = renderer.render("otp-verification", Map.of("title", "Xác minh", "preheader", "",
                "code", "1234", "ttlMinutes", "10", "username", "  Nguyễn <A>  "));
        assertThat(html).contains("Chào <strong>Nguyễn &lt;A&gt;</strong>,")
                .contains("Cảm ơn bạn đã đăng ký tài khoản <strong>Đồng</strong>.")
                .doesNotContain("Nguyễn <A>", "{{username}}");
    }

    @Test void verificationFallsBackForMissingNullOrBlankUsername() {
        var values = new java.util.HashMap<>(Map.of("title", "Xác minh", "preheader", "",
                "code", "1234", "ttlMinutes", "10"));
        assertThat(renderer.render("otp-verification", values)).contains("Chào <strong>bạn</strong>,");
        values.put("username", null);
        assertThat(renderer.render("otp-verification", values)).contains("Chào <strong>bạn</strong>,");
        values.put("username", "   ");
        assertThat(renderer.render("otp-verification", values)).contains("Chào <strong>bạn</strong>,");
    }

    @Test void passwordResetGreetsTheRecipientWithTheSameEscapingAndFallback() {
        var values = new java.util.HashMap<>(Map.of("title", "Đặt lại mật khẩu", "preheader", "",
                "code", "1234", "ttlMinutes", "10"));
        values.put("username", "  Nguyễn <A>  ");
        assertThat(renderer.render("forgot-password", values))
                .contains("Chào <strong>Nguyễn &lt;A&gt;</strong>,")
                .contains("Vui lòng nhập mã bên dưới vào ứng dụng để thiết lập mật khẩu mới.")
                .doesNotContain("Nguyễn <A>", "{{username}}");
        values.remove("username");
        assertThat(renderer.render("forgot-password", values)).contains("Chào <strong>bạn</strong>,");
        values.put("username", null);
        assertThat(renderer.render("forgot-password", values)).contains("Chào <strong>bạn</strong>,");
        values.put("username", "   ");
        assertThat(renderer.render("forgot-password", values)).contains("Chào <strong>bạn</strong>,");
    }
}
