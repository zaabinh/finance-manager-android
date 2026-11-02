package com.fintrack.api.service.notification;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class EmailTemplateRendererTest {
    private final EmailTemplateRenderer renderer = new EmailTemplateRenderer("https://cdn.example.test/logo.png?v=1&s=2");

    @Test void wrapsContentInSharedLayoutAndEscapesValues() {
        for (String template : new String[] {"otp-verification", "forgot-password"}) {
            String html = renderer.render(template, Map.of("title", "<Tiêu đề>", "preheader", "Xem trước",
                    "code", "<b>123456</b>", "ttlMinutes", "10"));
            assertThat(html).startsWith("<!DOCTYPE html>").contains("<title>&lt;Tiêu đề&gt;</title>")
                    .contains("&lt;b&gt;123456&lt;/b&gt;").doesNotContain("<b>123456</b>")
                    .contains("src=\"https://cdn.example.test/logo.png?v=1&amp;s=2\"").doesNotContain("{{");
        }
    }

    @Test void missingValueFailsInsteadOfSendingRawPlaceholder() {
        assertThatThrownBy(() -> renderer.render("otp-verification", Map.of("title", "t", "preheader", "p")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("code");
    }
}
