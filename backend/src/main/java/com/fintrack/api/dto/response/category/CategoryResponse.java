package com.fintrack.api.dto.response.category;

import java.time.Instant;
import java.util.UUID;
public record CategoryResponse(UUID categoryId, String name, String categoryType, String iconKey,
        String colorHex, int sortOrder, Instant archivedAt, Instant createdAt, Instant updatedAt) {}
