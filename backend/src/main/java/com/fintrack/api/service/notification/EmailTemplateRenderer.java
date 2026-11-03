package com.fintrack.api.service.notification;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

/**
 * Renders {@code templates/email/<name>.html} inside the shared {@code templates/email/layout.html}.
 * Placeholders are {@code {{key}}}; every value is HTML-escaped. Layout keys: {@code title}, {@code preheader}.
 */
@Component
public class EmailTemplateRenderer {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");
    private final String layout;
    private final String logoUrl;

    public EmailTemplateRenderer(@Value("${app.mail.logo-url:}") String logoUrl) {
        this.layout = load("layout");
        this.logoUrl = logoUrl;
    }

    public String render(String template, Map<String, String> values) {
        var vars = new HashMap<String, String>();
        values.forEach((key, value) -> {
            if (!"username".equals(key)) vars.put(key, escape(value));
        });
        String username = values.get("username");
        vars.put("username", escape(username == null || username.isBlank() ? "bạn" : username.strip()));
        vars.put("logoUrl", escape(logoUrl));
        vars.put("content", fill(load(template), vars));
        return fill(layout, vars);
    }

    private static String fill(String template, Map<String, String> vars) {
        return PLACEHOLDER.matcher(template).replaceAll(match -> {
            String value = vars.get(match.group(1));
            if (value == null) throw new IllegalArgumentException("Missing email template value: " + match.group(1));
            return Matcher.quoteReplacement(value);
        });
    }

    private static String escape(String value) { return HtmlUtils.htmlEscape(value, StandardCharsets.UTF_8.name()); }

    private static String load(String name) {
        try {
            return new ClassPathResource("templates/email/" + name + ".html").getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Email template not found: " + name, exception);
        }
    }
}
