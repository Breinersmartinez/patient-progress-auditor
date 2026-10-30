package com.patientprogressauditor.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.patientprogressauditor.agent.AgentProperties.DescriptionVersion;

// Asi se fuerza cada limite en la demo: con las mismas variables de entorno que usa el compose (AGENT_MAX_ITERATIONS, etc.),
// que Spring traduce a estas propiedades. Aqui se pasan como propiedades, el mismo camino.
@SpringBootTest(properties = { "agent.max-iterations=1", "agent.max-tokens=1000", "agent.tool-timeout=0s", "agent.tool-descriptions=V1" })
class AgentPropertiesForcedTest {

    @Autowired private AgentProperties properties;

    @Test
    void everyLimitCanBeForcedByConfiguration() {
        assertThat(properties.getMaxIterations()).isEqualTo(1);
        assertThat(properties.getMaxTokens()).isEqualTo(1000);
        assertThat(properties.getToolTimeout()).isEqualTo(Duration.ZERO);
        assertThat(properties.getToolDescriptions()).isEqualTo(DescriptionVersion.V1);
    }
}
