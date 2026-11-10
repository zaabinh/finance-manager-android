package com.fintrack.api.dto.response.transaction;

import java.util.List;

public record TransactionPageResponse(List<TransactionResponse> items, int page, int totalPages) {}
