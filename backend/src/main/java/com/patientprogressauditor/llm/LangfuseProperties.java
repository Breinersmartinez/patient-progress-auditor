package com.patientprogressauditor.llm;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

// Trazas y costo por consulta en Langfuse Cloud (D28, RF-43), opcional: sin las dos llaves no se envia nada. Las llaves
// vienen del entorno y nunca tienen valor por defecto.
@ConfigurationProperties(prefix = "langfuse")
@Getter
@Setter
public class LangfuseProperties {

    public static final String DEFAULT_BASE_URL = "https://us.cloud.langfuse.com";
    public static final String DEFAULT_ENVIRONMENT = "demo";

    private String publicKey = "";
    private String secretKey = "";
    private String baseUrl = DEFAULT_BASE_URL;
    // Agrupa las trazas en el panel (demo, eval, pruebas) para que la evaluacion no se mezcle con la demo.
    private String environment = DEFAULT_ENVIRONMENT;
    // Tope de cada envio. Corre en segundo plano: nunca retrasa la peticion de enfermeria.
    private Duration timeout = Duration.ofSeconds(5);

    public boolean enabled() {
        return publicKey != null && !publicKey.isBlank() && secretKey != null && !secretKey.isBlank();
    }
}
