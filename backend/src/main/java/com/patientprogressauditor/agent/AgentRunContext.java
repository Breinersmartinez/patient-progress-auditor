package com.patientprogressauditor.agent;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.patientprogressauditor.agent.tools.ToolException;
import com.patientprogressauditor.metrics.WeeklyMetrics;

// Lo que una ejecucion sabe y las herramientas comparten: de quien es la tarea, de que semana, los alias de las entradas
// que el agente ha leido (son las unicas que puede citar) y las metricas ya calculadas.
public class AgentRunContext {

    private final UUID patientId;
    private final LocalDate weekStart;
    private final Map<String, UUID> entriesByAlias = new LinkedHashMap<>();
    private final Map<UUID, String> aliasByEntry = new HashMap<>();
    private final Map<UUID, LocalDate> dateByEntry = new HashMap<>();
    private final Map<LocalDate, WeeklyMetrics> metrics = new HashMap<>();
    private DraftSink sink;
    private UUID savedReport;
    private String request = "";
    private int rejectedDrafts;

    // Quien guarda de verdad el borrador. La herramienta save_draft es la unica que lo invoca: es la unica escritura del agente.
    @FunctionalInterface
    public interface DraftSink {
        UUID save(ObjectNode content, Map<String, List<UUID>> citations);
    }

    public AgentRunContext(UUID patientId, LocalDate weekStart) {
        if (weekStart.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("Una semana empieza el lunes: " + weekStart);
        }
        this.patientId = patientId;
        this.weekStart = weekStart;
    }

    public void setSink(DraftSink sink) {
        this.sink = sink;
    }

    // Lo que la persona pidio ademas del informe (p. ej. un diagnostico, que se declara fuera de alcance); puede ir vacio.
    public void setRequest(String request) {
        this.request = request == null ? "" : request;
    }

    public String request() {
        return request;
    }

    public synchronized UUID saveDraft(ObjectNode content, Map<String, List<UUID>> citations) {
        if (savedReport != null) {
            throw new ToolException("El borrador ya se guardó; la tarea terminó.");
        }
        if (sink == null) {
            throw new IllegalStateException("Esta ejecución no puede guardar borradores");
        }
        savedReport = sink.save(content, citations);
        return savedReport;
    }

    public synchronized UUID savedReport() {
        return savedReport;
    }

    public synchronized int rejectedDrafts() {
        return rejectedDrafts;
    }

    public synchronized void draftRejected() {
        rejectedDrafts++;
    }

    public UUID patientId() {
        return patientId;
    }

    public LocalDate weekStart() {
        return weekStart;
    }

    public LocalDate weekEnd() {
        return weekStart.plusDays(6);
    }

    // El alias corto de una entrada («e1», «e2»…): el mismo siempre dentro de la ejecucion. Ahorra tokens y hace que citar
    // una entrada que nunca se leyo sea imposible de disimular: un alias no emitido no existe.
    public synchronized String aliasFor(UUID entryId, LocalDate date) {
        String alias = aliasByEntry.get(entryId);
        if (alias == null) {
            alias = "e" + (entriesByAlias.size() + 1);
            entriesByAlias.put(alias, entryId);
            aliasByEntry.put(entryId, alias);
            dateByEntry.put(entryId, date);
        }
        return alias;
    }

    public synchronized UUID entryOf(String alias) {
        return entriesByAlias.get(alias);
    }

    public synchronized LocalDate dateOf(UUID entryId) {
        return dateByEntry.get(entryId);
    }

    public synchronized int knownEntries() {
        return entriesByAlias.size();
    }

    // Una semana pedida por el agente: lunes y no posterior a la del informe (no se lee el futuro de la semana evaluada).
    public LocalDate requireWeek(String text) {
        LocalDate week;
        try {
            week = LocalDate.parse(text == null ? "" : text.trim());
        } catch (RuntimeException e) {
            throw new ToolException("week_start debe ser una fecha AAAA-MM-DD; recibí «" + text + "».");
        }
        if (week.getDayOfWeek() != DayOfWeek.MONDAY) {
            throw new ToolException("week_start debe ser un lunes; " + week + " no lo es. El lunes de esa semana es "
                    + week.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) + ".");
        }
        if (week.isAfter(weekStart)) {
            throw new ToolException("Solo puede consultar hasta la semana del informe (" + weekStart + "); " + week + " es posterior.");
        }
        return week;
    }

    public synchronized WeeklyMetrics metrics(LocalDate week, Function<LocalDate, WeeklyMetrics> loader) {
        return metrics.computeIfAbsent(week, loader);
    }
}
