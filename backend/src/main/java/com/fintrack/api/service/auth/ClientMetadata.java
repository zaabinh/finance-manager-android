package com.fintrack.api.service.auth;

import jakarta.servlet.http.HttpServletRequest;

public record ClientMetadata(String ipAddress, String userAgent) {
    public static ClientMetadata from(HttpServletRequest request) {
        // Forwarded headers are deliberately ignored; configure trusted proxies at the server boundary.
        String agent = request.getHeader("User-Agent");
        return new ClientMetadata(request.getRemoteAddr(), agent == null ? null : agent.substring(0, Math.min(300, agent.length())));
    }
}
