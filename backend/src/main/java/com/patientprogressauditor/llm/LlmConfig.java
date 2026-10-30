package com.patientprogressauditor.llm;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class LlmConfig {

    @Bean
    Sleeper sleeper() {
        return duration -> Thread.sleep(duration.toMillis());
    }
}
