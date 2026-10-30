package com.patientprogressauditor.agent;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

// Segunda barrera, en codigo, contra el diagnostico, la recomendacion y la dosis (RN-03, RF-15). El prompt ya lo pide; esto
// no depende de que el modelo obedezca. Es una lista deliberadamente corta y concreta: una palabra de mas obliga a
// reescribir y gasta una iteracion (hay 5), asi que solo entra lo que el informe nunca debe decir.
public final class DiagnosisGuard {

    private static final List<Pattern> FORBIDDEN = List.of(
            p("diagn[oó]stic\\w*"), p("padec\\w*"), p("pron[oó]stic\\w*"), p("recomi?end\\w*"), p("sugier\\w*|sugerir\\w*|se sugiere"),
            p("deber[ií]a\\w*"), p("suspend\\w*"), p("dosis|dosific\\w*"), p("\\d+\\s*(mg|ml|mcg)\\b|\\b(mg|ml|mcg)\\b"),
            p("antibi[oó]tic\\w*|analg[eé]sic\\w*|medicament\\w*|medicaci[oó]n|f[aá]rmac\\w*|prescri\\w*|receta\\w*"),
            p("tratamiento\\w*"));

    // La solicitud de la persona pide diagnostico, tratamiento o dosis: se declara fuera de alcance en el informe.
    private static final Pattern REQUEST = Pattern.compile(
            "diagn[oó]stic|qu[eé] tiene|qu[eé] enfermedad|tratamiento|medic|dosis|recet|recomienda|suspend", Pattern.CASE_INSENSITIVE);

    private DiagnosisGuard() {
    }

    private static Pattern p(String regex) {
        return Pattern.compile("(?<![\\p{L}])(?:" + regex + ")", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    // La primera palabra prohibida que aparece, o vacio.
    public static Optional<String> forbiddenIn(String text) {
        if (text == null) {
            return Optional.empty();
        }
        for (Pattern pattern : FORBIDDEN) {
            var matcher = pattern.matcher(text);
            if (matcher.find()) {
                return Optional.of(matcher.group().trim());
            }
        }
        return Optional.empty();
    }

    public static boolean asksForDiagnosisOrTreatment(String request) {
        return request != null && REQUEST.matcher(request).find();
    }

    public static final String OUT_OF_SCOPE = "Se pidió un diagnóstico o una recomendación de tratamiento: está fuera del alcance de este informe, "
            + "que solo describe la evolución documentada en los registros.";
}
