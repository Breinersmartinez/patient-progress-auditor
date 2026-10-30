package com.patientprogressauditor.seed;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest
class DemoDataSeederDisabledTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void withoutTheFlagThereIsNoSeeder() {
        assertThat(context.getBeanNamesForType(DemoDataSeeder.class)).isEmpty();
    }
}
