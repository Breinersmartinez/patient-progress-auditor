package com.patientprogressauditor.agent.tools;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.patientprogressauditor.agent.AgentProperties.DescriptionVersion;
import com.patientprogressauditor.agent.AgentRunContext;
import com.patientprogressauditor.agent.DraftValidator;
import com.patientprogressauditor.agent.ReportContentBuilder;
import com.patientprogressauditor.llm.ToolSpec;

// La UNICA herramienta que escribe, y solo crea un borrador PENDIENTE_REVISION: el agente no tiene forma de aprobar (la
// confirmacion humana, AGENTS.md). Si el validador rechaza el borrador, el modelo recibe los problemas como observacion
// y puede corregirlo mientras le queden iteraciones.
@Component
public class SaveDraftTool implements AgentTool {

    private final DraftValidator validator;
    private final ReportContentBuilder builder;
    private final ObjectMapper mapper;

    public SaveDraftTool(DraftValidator validator, ReportContentBuilder builder, ObjectMapper mapper) {
        this.validator = validator;
        this.builder = builder;
        this.mapper = mapper;
    }

    @Override
    public String name() {
        return "save_draft";
    }

    @Override
    public boolean writes() {
        return true;
    }

    @Override
    public ToolSpec spec(DescriptionVersion version) {
        ObjectNode schema = mapper.createObjectNode().put("type", "object");
        ObjectNode props = schema.putObject("properties");
        props.putObject("summary").put("type", "string")
                .put("description", "Resumen en lenguaje claro de la evolución. Máximo 600 caracteres.");
        ObjectNode claim = props.putObject("claims").put("type", "array").putObject("items").put("type", "object");
        ObjectNode c = claim.putObject("properties");
        c.putObject("claim_key").put("type", "string").put("description", "Identificador corto único: c1, c2…");
        c.putObject("text").put("type", "string").put("description", "Una afirmación sobre lo documentado. Escriba los números con dígitos.");
        c.putObject("criterion").put("type", "string").put("description", "Con qué se comparó, p. ej. «semana anterior»");
        c.putObject("entry_ids").put("type", "array").put("description", "Alias (e1, e2…) de los registros que la sustentan, de 1 a 6")
                .putObject("items").put("type", "string");
        ObjectNode fig = c.putObject("figures").put("type", "array").put("description", "Una entrada por cada número del texto: key es la ruta en get_week_metrics (p. ej. symptoms.NAUSEAS.days) y value su valor")
                .putObject("items").put("type", "object");
        fig.putObject("properties").putObject("key").put("type", "string");
        fig.with("properties").putObject("value").put("type", "integer");
        claim.putArray("required").add("claim_key").add("text").add("criterion").add("entry_ids").add("figures");
        props.putObject("changes_vs_previous").put("type", "array").put("description", "claim_key de las afirmaciones que son cambios frente a la semana anterior")
                .putObject("items").put("type", "string");
        props.putObject("recurring_patterns").put("type", "array").put("description", "claim_key de las afirmaciones sobre síntomas que reaparecen o persisten")
                .putObject("items").put("type", "string");
        schema.putArray("required").add("summary").add("claims").add("changes_vs_previous").add("recurring_patterns");
        return new ToolSpec(name(), ToolDescriptions.of(name(), version), schema);
    }

    @Override
    public String execute(AgentRunContext context, JsonNode arguments) {
        DraftValidator.Checked checked = validator.check(arguments, context);
        if (!checked.valid()) {
            context.draftRejected();
            throw new ToolException("El borrador NO se guardó. Corrija esto y vuelva a llamar a save_draft:\n- "
                    + String.join("\n- ", checked.problems()));
        }
        ObjectNode content = builder.build(checked.model(), context);
        context.saveDraft(content, checked.citations());
        return "Borrador guardado: queda pendiente de revisión por enfermería. La tarea terminó.";
    }
}
