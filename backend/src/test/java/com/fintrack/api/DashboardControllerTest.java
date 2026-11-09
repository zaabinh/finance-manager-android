package com.fintrack.api;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.fintrack.api.controller.DashboardController;
import com.fintrack.api.exception.GlobalExceptionHandler;
import com.fintrack.api.security.AuthenticatedUser;
import com.fintrack.api.service.dashboard.DashboardService;
import com.fintrack.api.service.finance.FinanceSupport;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.bind.support.WebDataBinderFactory;

class DashboardControllerTest {
    DashboardService service = mock(DashboardService.class);
    UUID user = UUID.randomUUID();
    MockMvc mvc;
    @BeforeEach void setup() {
        var principal = mock(AuthenticatedUser.class); when(principal.id()).thenReturn(user);
        mvc = MockMvcBuilders.standaloneSetup(new DashboardController(service)).setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    public boolean supportsParameter(MethodParameter p) { return p.getParameterType() == AuthenticatedUser.class; }
                    public Object resolveArgument(MethodParameter p, ModelAndViewContainer m, NativeWebRequest r, WebDataBinderFactory f) { return principal; }
                }).build();
    }
    @Test void passesOnlyPrincipalOwnerAndOptionalMonth() throws Exception {
        mvc.perform(get("/api/v1/dashboard")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/dashboard").param("month", "2026-11")).andExpect(status().isOk());
        verify(service).get(user, null); verify(service).get(user, "2026-11");
    }
    @Test void rejectsUnknownAndDuplicateQueriesBeforeCallingService() throws Exception {
        for (String field : java.util.List.of("userId", "walletId", "limit", "status"))
            mvc.perform(get("/api/v1/dashboard").param(field, "anything")).andExpect(status().isBadRequest()).andExpect(jsonPath("errorCode").value("BAD_REQUEST"));
        mvc.perform(get("/api/v1/dashboard").param("month", "2026-11", "2026-12")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void keepsStandardOverflowErrorEnvelope() throws Exception {
        when(service.get(user, null)).thenThrow(new com.fintrack.api.exception.AppException(
                org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                com.fintrack.api.exception.ErrorCode.FINANCE_AMOUNT_OVERFLOW, "Amount overflow"));
        mvc.perform(get("/api/v1/dashboard")).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("errorCode").value("FINANCE_AMOUNT_OVERFLOW"));
    }
}
