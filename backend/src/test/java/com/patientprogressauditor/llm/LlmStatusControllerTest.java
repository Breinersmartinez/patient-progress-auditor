package com.patientprogressauditor.llm;

import static com.patientprogressauditor.llm.LlmFixtures.MAPPER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import com.patientprogressauditor.llm.LlmStatusController.LlmStatus;

@SpringBootTest(properties = { "app.seed.demo=true", "llm.groq.api-key=gsk_secreta", "llm.gemini.api-key=AIza_secreta" })
@AutoConfigureMockMvc
class LlmStatusControllerTest {

    private static final String NURSE = "20000000-0000-0000-0000-000000000002";
    private static final String DOCTOR = "20000000-0000-0000-0000-000000000003";

    @Autowired private MockMvc mockMvc;

    @Test
    void listsPrimaryAndBackupAndNeverLeaksAKey() throws Exception {
        mockMvc.perform(get("/api/llm/status").header("X-Demo-User", NURSE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.warning").doesNotExist())
                .andExpect(jsonPath("$.primary.provider").value("groq"))
                .andExpect(jsonPath("$.primary.configured").value(true))
                .andExpect(jsonPath("$.primary.agentModel").value("openai/gpt-oss-120b"))
                .andExpect(jsonPath("$.primary.structuringModel").value("openai/gpt-oss-120b"))
                .andExpect(jsonPath("$.fallback.provider").value("gemini"))
                .andExpect(jsonPath("$.fallback.agentModel").value("gemini-3.5-flash-lite"))
                .andExpect(content().string(not(containsString("secreta"))));
    }

    @Test
    void anyChosenUserCanSeeItAndNoUserIsRejected() throws Exception {
        mockMvc.perform(get("/api/llm/status").header("X-Demo-User", DOCTOR)).andExpect(status().isOk());
        mockMvc.perform(get("/api/llm/status")).andExpect(status().isUnauthorized());
    }

    private static LlmStatus statusWith(String groqKey, String geminiKey, String primary, String fallback) {
        LlmProperties properties = LlmFixtures.properties("http://unused", groqKey, "http://unused", geminiKey);
        properties.setPrimary(primary);
        properties.setFallback(fallback);
        List<LlmClient> clients = List.of(new GroqClient(properties, MAPPER), new GeminiClient(properties, MAPPER));
        return new LlmStatusController(new LlmGateway(clients, properties, d -> { }, MAPPER)).status();
    }

    @Test
    void withoutAnyKeyItSaysWhatToDefineInsteadOfFailing() {
        LlmStatus status = statusWith("", "", "groq", "gemini");

        assertThat(status.available()).isFalse();
        assertThat(status.primary().configured()).isFalse();
        assertThat(status.warning()).isEqualTo("La IA no está configurada: falta la llave del proveedor. "
                + "Defina GROQ_API_KEY (o GEMINI_API_KEY) y reinicie la aplicación.");
    }

    @Test
    void withOnlyTheBackupKeyItWarnsThatTheBackupIsInUse() {
        LlmStatus status = statusWith("", "AIza", "groq", "gemini");

        assertThat(status.available()).isTrue();
        assertThat(status.warning()).isEqualTo("Falta GROQ_API_KEY: se usa Gemini, el proveedor de respaldo.");
    }

    @Test
    void withOnlyThePrimaryKeyThereIsNoWarning() {
        LlmStatus status = statusWith("gsk", "", "groq", "gemini");

        assertThat(status.available()).isTrue();
        assertThat(status.warning()).isNull();
        assertThat(status.fallback().configured()).isFalse();
    }

    @Test
    void theOrderFollowsTheConfiguration() {
        LlmStatus status = statusWith("gsk", "AIza", "gemini", "groq");

        assertThat(status.primary().provider()).isEqualTo("gemini");
        assertThat(status.fallback().provider()).isEqualTo("groq");
    }

    @Test
    void withoutABackupTheFallbackIsNull() {
        assertThat(statusWith("gsk", "AIza", "groq", "none").fallback()).isNull();
    }
}
