package com.patientprogressauditor.llm;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Estado del proveedor para el aviso de las pantallas que usan IA (D22, RNF-13): que proveedor es el principal, cual el
// respaldo y si tienen llave. Nunca devuelve una llave. Cualquier usuario elegido puede verlo.
@RestController
@RequestMapping("/api/llm")
public class LlmStatusController {

    public record ProviderStatus(String provider, String displayName, boolean configured, String structuringModel,
            String agentModel) {
    }

    public record LlmStatus(boolean available, ProviderStatus primary, ProviderStatus fallback, String warning) {
    }

    private final LlmGateway gateway;

    public LlmStatusController(LlmGateway gateway) {
        this.gateway = gateway;
    }

    @GetMapping("/status")
    public LlmStatus status() {
        List<LlmClient> providers = gateway.providers();
        ProviderStatus primary = view(providers.get(0));
        ProviderStatus fallback = providers.size() > 1 ? view(providers.get(1)) : null;
        boolean available = primary.configured() || (fallback != null && fallback.configured());
        String warning = null;
        if (!available) {
            warning = "La IA no está configurada: falta la llave del proveedor. Defina "
                    + providers.get(0).keyVariable() + (providers.size() > 1 ? " (o " + providers.get(1).keyVariable() + ")" : "")
                    + " y reinicie la aplicación.";
        } else if (!primary.configured()) {
            warning = "Falta " + providers.get(0).keyVariable() + ": se usa " + fallback.displayName() + ", el proveedor de respaldo.";
        }
        return new LlmStatus(available, primary, fallback, warning);
    }

    private static ProviderStatus view(LlmClient client) {
        return new ProviderStatus(client.id(), client.displayName(), client.configured(),
                client.model(LlmPurpose.STRUCTURING), client.model(LlmPurpose.AGENT));
    }
}
