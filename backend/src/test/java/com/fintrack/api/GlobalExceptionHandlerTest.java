package com.fintrack.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fintrack.api.exception.GlobalExceptionHandler;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ErrorProbeController())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void validationReturnsFieldErrors() throws Exception {
        mockMvc.perform(post("/probe").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/probe"));
    }

    @Test
    void malformedJsonDoesNotLeakParserDetails() throws Exception {
        mockMvc.perform(post("/probe").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Request could not be processed"))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test
    void commonRequestErrorsKeepTheirHttpStatus() throws Exception {
        mockMvc.perform(get("/probe"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.errorCode").value("METHOD_NOT_ALLOWED"));
        mockMvc.perform(post("/probe").contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType());
        mockMvc.perform(get("/parameter"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/parameter").param("count", "invalid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unexpectedExceptionDoesNotLeakInternalMessage() throws Exception {
        mockMvc.perform(get("/failure"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @RestController
    @TestComponent
    static class ErrorProbeController {

        @PostMapping("/probe")
        void validate(@Valid @RequestBody ProbeRequest request) {
        }

        @GetMapping("/parameter")
        void parameter(@RequestParam int count) {
        }

        @GetMapping("/failure")
        void failure() {
            throw new IllegalStateException("Internal diagnostic detail");
        }
    }

    record ProbeRequest(@NotBlank String name) {
    }
}
