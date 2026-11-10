package com.fintrack.api.dto.response.user;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String username,
        String displayName,
        String email
) {
}
