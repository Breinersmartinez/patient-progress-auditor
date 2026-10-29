package com.patientprogressauditor.agent;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

// Los limites del agente (AGENTS.md, «El agente»; valores y razon en docs/decisiones.md y docs/decisiones/semana06.md).
// Se configuran por variable de entorno: asi cada limite se puede forzar en la demo SIN simular nada (por ejemplo
// AGENT_MAX_ITERATIONS=1) y la ruta normal sigue llamando al proveedor real.
@ConfigurationProperties(prefix = "agent")
@Getter
@Setter
public class AgentProperties {

    public enum DescriptionVersion { V1, V2 }

    // Cuantas llamadas al modelo puede hacer una ejecucion (una iteracion = una llamada).
    private int maxIterations = 5;
    // Tokens de entrada mas salida, sumados, por solicitud.
    private int maxTokens = 30_000;
    // Tiempo maximo de UNA herramienta. Son lecturas locales: si tardan mas de esto, algo anda mal.
    private Duration toolTimeout = Duration.ofSeconds(5);
    // Cuantas veces puede agotar una herramienta su tiempo antes de detener la ejecucion.
    private int maxToolTimeouts = 2;
    // Version de las descripciones de herramientas (se mide cada una; ver docs/decisiones/semana06.md).
    private DescriptionVersion toolDescriptions = DescriptionVersion.V2;
    // Un texto de entrada mas largo se corta al mostrarselo al modelo: ahorra tokens (la cuota gratuita es de 8 000 por minuto).
    private int entryTextChars = 400;
}
