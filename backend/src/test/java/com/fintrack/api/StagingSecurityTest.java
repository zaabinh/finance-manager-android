package com.fintrack.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "staging"})
class StagingSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private Environment environment;

    @Test
    void stagingUsesDeploymentSecurityDefaults() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/wallets")).andExpect(status().isUnauthorized());
        assertThat(environment.getProperty("springdoc.api-docs.enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("springdoc.swagger-ui.enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("spring.jpa.show-sql", Boolean.class)).isFalse();
        assertThat(environment.getProperty("spring.config.import")).isNull();
    }
}
