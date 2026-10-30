package com.patientprogressauditor.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.patientprogressauditor.agent.AgentProperties.DescriptionVersion;

// Los limites vienen de la configuracion: los valores por defecto son los que el equipo decidio.
@SpringBootTest
class AgentPropertiesTest {

    @Autowired private AgentProperties properties;

    @Test
    void theDefaultLimitsAreTheOnesTheTeamDecided() {
        assertThat(properties.getMaxIterations()).isEqualTo(5);
        assertThat(properties.getMaxTokens()).isEqualTo(30_000);
        assertThat(properties.getToolTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.getToolDescriptions()).isEqualTo(DescriptionVersion.V2);
    }
}
