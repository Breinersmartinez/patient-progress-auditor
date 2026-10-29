package com.patientprogressauditor.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.patientprogressauditor.agent.AgentProperties.DescriptionVersion;
import com.patientprogressauditor.agent.AgentRunContext;
import com.patientprogressauditor.llm.ToolSpec;

// Una herramienta del agente: atomica, con minimo privilegio. Las de lectura devuelven JSON compacto como texto; un uso
// invalido lanza ToolException y vuelve al modelo como observacion.
public interface AgentTool {

    String name();

    // La descripcion es un prompt: cuando usarla, cuando NO y que prerrequisitos tiene. Hay dos versiones medidas.
    ToolSpec spec(DescriptionVersion version);

    // true solo para la unica herramienta que escribe: el borrador en PENDIENTE_REVISION.
    default boolean writes() {
        return false;
    }

    String execute(AgentRunContext context, JsonNode arguments) throws ToolException;
}
