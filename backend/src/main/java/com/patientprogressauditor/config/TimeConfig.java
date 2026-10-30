package com.patientprogressauditor.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

    // La fecha de hoy la decide el servidor, en America/Bogota (D10, RN-06); el navegador no.
    public static final ZoneId BOGOTA = ZoneId.of("America/Bogota");

    @Bean
    Clock clock() {
        return Clock.system(BOGOTA);
    }
}
