package com.patientprogressauditor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.patientprogressauditor.llm.LangfuseExporter;

@SpringBootTest
class PatientProgressAuditorApplicationTests {

    @Autowired
    private LangfuseExporter langfuse;

    @Test
    void contextLoads() {
    }

    // Las pruebas no mandan trazas a la cuenta compartida de Langfuse aunque las llaves esten en el entorno de quien las corre
    // (surefire las vacia en pom.xml). Si esto falla, el arnes dejo de aislarlas.
    @Test
    void langfuseStaysOffInTests() {
        assertThat(langfuse.enabled()).isFalse();
    }
}
