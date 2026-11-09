package com.fintrack.api;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fintrack.api.controller.TransactionController;
import com.fintrack.api.dto.request.transaction.TransactionRequest;
import com.fintrack.api.dto.response.transaction.TransactionPageResponse;
import com.fintrack.api.dto.response.transaction.TransactionResponse;
import com.fintrack.api.exception.ErrorCode;
import com.fintrack.api.exception.GlobalExceptionHandler;
import com.fintrack.api.security.AuthenticatedUser;
import com.fintrack.api.service.finance.FinanceSupport;
import com.fintrack.api.service.transaction.TransactionService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.bind.support.WebDataBinderFactory;

/** Exercises real routing, JSON and error envelopes without a datasource-free Spring profile. */
class TransactionControllerTest {
    final TransactionService service = mock(TransactionService.class);
    final UUID user = UUID.randomUUID(), id = UUID.randomUUID();
    MockMvc mvc;
    TransactionResponse response;

    @BeforeEach void setup() {
        AuthenticatedUser principal = mock(AuthenticatedUser.class);
        when(principal.id()).thenReturn(user);
        mvc = MockMvcBuilders.standaloneSetup(new TransactionController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    public boolean supportsParameter(MethodParameter parameter) { return parameter.getParameterType() == AuthenticatedUser.class; }
                    public Object resolveArgument(MethodParameter p, ModelAndViewContainer m, NativeWebRequest r, WebDataBinderFactory f) { return principal; }
                }).build();
        Instant at = Instant.parse("2026-11-07T08:45:00Z");
        response = new TransactionResponse(id, "INCOME", 10_000, UUID.randomUUID(), UUID.randomUUID(), null,
                at, "", "", UUID.randomUUID(), "POSTED", null, at, at);
    }

    @Test void postReturnsCreatedAndReplayReturnsOk() throws Exception {
        when(service.create(eq(user), any())).thenReturn(new TransactionService.Result(response, true), new TransactionService.Result(response, false));
        mvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated()).andExpect(header().string("Location", "/api/v1/transactions/" + id))
                .andExpect(jsonPath("amountMinor").value(10_000)).andExpect(jsonPath("status").value("POSTED"));
        mvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
    }

    @Test void listBindsFiltersAndDetailIsNotLimitedToHistoryPage() throws Exception {
        when(service.list(user, 0, 50, null, null, null, null, null, null, "occurredAt,desc"))
                .thenReturn(new TransactionPageResponse(List.of(), 0, 0));
        when(service.detail(user, id)).thenReturn(response);
        mvc.perform(get("/api/v1/transactions")).andExpect(status().isOk()).andExpect(jsonPath("totalPages").value(0));
        mvc.perform(get("/api/v1/transactions/" + id)).andExpect(status().isOk()).andExpect(jsonPath("transactionId").value(id.toString()));
        when(service.list(user, 2, 10, "2026-11-01", "2026-11-30", null, null, "INCOME", "50%", "amountMinor,asc"))
                .thenReturn(new TransactionPageResponse(List.of(response), 2, 3));
        mvc.perform(get("/api/v1/transactions").param("page", "2").param("size", "10").param("dateFrom", "2026-11-01")
                        .param("dateTo", "2026-11-30").param("type", "INCOME").param("q", "50%").param("sort", "amountMinor,asc"))
                .andExpect(status().isOk()).andExpect(jsonPath("page").value(2));
    }

    @Test void malformedFinanceJsonAndUnownedIdsHaveTheirContractStatus() throws Exception {
        for (String json : List.of("{\"amountMinor\":1.5}", "{\"amountMinor\":\"100\"}", "{\"status\":\"VOIDED\"}", "{\"walletId\":\"bad-id\"}"))
            mvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isBadRequest());
        verify(service, never()).create(any(), any(TransactionRequest.class));
        when(service.detail(user, id)).thenThrow(FinanceSupport.notFound());
        mvc.perform(get("/api/v1/transactions/" + id)).andExpect(status().isNotFound()).andExpect(jsonPath("errorCode").value("NOT_FOUND"));
    }

    @Test void updateAndDeleteMapInsufficientFundsWithoutReportingSuccess() throws Exception {
        when(service.update(eq(user), eq(id), any())).thenReturn(response);
        mvc.perform(put("/api/v1/transactions/" + id).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("transactionId").value(id.toString()));
        doThrow(FinanceSupport.conflict(ErrorCode.WALLET_INSUFFICIENT_FUNDS, "insufficient"))
                .doNothing().when(service).delete(user, id);
        mvc.perform(delete("/api/v1/transactions/" + id)).andExpect(status().isConflict())
                .andExpect(jsonPath("errorCode").value("WALLET_INSUFFICIENT_FUNDS"));
        mvc.perform(delete("/api/v1/transactions/" + id)).andExpect(status().isNoContent()).andExpect(content().string(""));
    }
}
