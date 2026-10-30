package com.patientprogressauditor.entry;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

// Un sintoma: un codigo estable (`DOLOR_GARGANTA`) y su etiqueta legible. Nueve vienen en el catalogo (D18) y conservan su
// codigo de siempre; cualquier otro que la nota diga o la persona escriba se acepta si tiene forma de sintoma (E18). Dos
// sintomas son el mismo si tienen el mismo codigo: la etiqueta solo se muestra. El orden es el del catalogo y luego el del codigo.
public final class Symptom implements Comparable<Symptom> {

    public static final int MAX_CODE = 40;
    public static final int MAX_LABEL = 60;
    private static final Pattern CODE = Pattern.compile("^[A-Z][A-Z0-9_]{1,39}$");
    private static final Pattern LABEL = Pattern.compile("^[\\p{L}][\\p{L}\\p{N} ,'()/-]{1,59}$");

    public static final Symptom VOMITO = catalog("VOMITO", "Vómito");
    public static final Symptom DIARREA = catalog("DIARREA", "Diarrea");
    public static final Symptom NAUSEAS = catalog("NAUSEAS", "Náuseas");
    public static final Symptom FIEBRE = catalog("FIEBRE", "Fiebre");
    public static final Symptom MAREO = catalog("MAREO", "Mareo");
    public static final Symptom DOLOR_CABEZA = catalog("DOLOR_CABEZA", "Dolor de cabeza");
    public static final Symptom DOLOR_HUESO = catalog("DOLOR_HUESO", "Dolor de hueso");
    public static final Symptom APETITO_BAJO = catalog("APETITO_BAJO", "Apetito bajo");
    public static final Symptom ANIMO_DECAIDO = catalog("ANIMO_DECAIDO", "Ánimo decaído");

    private static final List<Symptom> CATALOG = List.of(VOMITO, DIARREA, NAUSEAS, FIEBRE, MAREO, DOLOR_CABEZA, DOLOR_HUESO,
            APETITO_BAJO, ANIMO_DECAIDO);

    private final String code;
    private final String label;

    private Symptom(String code, String label) {
        this.code = code;
        this.label = label;
    }

    private static Symptom catalog(String code, String label) {
        return new Symptom(code, label);
    }

    // Los nueve del catalogo, en su orden (lo que antes devolvia el enum).
    public static List<Symptom> values() {
        return CATALOG;
    }

    public static boolean isCatalog(String code) {
        return CATALOG.stream().anyMatch(s -> s.code.equals(code));
    }

    // El codigo de un nombre escrito a mano: sin acentos, en mayusculas y con guiones bajos («Dolor de garganta» -> DOLOR_DE_GARGANTA).
    public static String codeOf(String typed) {
        String plain = Normalizer.normalize(typed.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return plain.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
    }

    // El sintoma que nombra un texto escrito a mano. Si coincide con uno del catalogo (por su etiqueta o su codigo, sin acentos)
    // es ese; si no, uno nuevo con la etiqueta como se escribio (la primera letra en mayuscula). Vacio si no tiene forma de sintoma.
    public static Optional<Symptom> fromTyped(String typed) {
        if (typed == null) {
            return Optional.empty();
        }
        String text = typed.trim().replaceAll("\\s+", " ");
        String code = codeOf(text);
        for (Symptom s : CATALOG) {
            if (s.code.equals(code) || codeOf(s.label).equals(code)) {
                return Optional.of(s);
            }
        }
        if (!LABEL.matcher(text).matches() || !CODE.matcher(code).matches()) {
            return Optional.empty();
        }
        return Optional.of(new Symptom(code, text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1)));
    }

    // La semilla trae el sintoma como texto con su codigo («NAUSEAS»).
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static Symptom fromCode(String code) {
        return of(code, null);
    }

    // Lo que se guarda y se vuelve a leer: el codigo y la etiqueta. Para uno del catalogo la etiqueta es la del catalogo.
    public static Symptom of(String code, String label) {
        for (Symptom s : CATALOG) {
            if (s.code.equals(code)) {
                return s;
            }
        }
        if (code == null || !CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("Código de síntoma no válido: " + code);
        }
        return new Symptom(code, label == null || label.isBlank() ? prettify(code) : label);
    }

    // Etiqueta de reserva para un codigo sin etiqueta: DOLOR_GARGANTA -> Dolor garganta.
    public static String prettify(String code) {
        String spaced = code.toLowerCase(Locale.ROOT).replace('_', ' ');
        return spaced.substring(0, 1).toUpperCase(Locale.ROOT) + spaced.substring(1);
    }

    @JsonValue
    public String code() {
        return code;
    }

    // Igual que el codigo; se conserva el nombre que tenia el enum para que el resto del codigo no cambie.
    public String name() {
        return code;
    }

    public String label() {
        return label;
    }

    public boolean inCatalog() {
        return isCatalog(code);
    }

    @Override
    public int compareTo(Symptom other) {
        int mine = CATALOG.indexOf(this);
        int theirs = CATALOG.indexOf(other);
        if (mine >= 0 && theirs >= 0) {
            return Integer.compare(mine, theirs);
        }
        if (mine >= 0) {
            return -1;
        }
        if (theirs >= 0) {
            return 1;
        }
        return code.compareTo(other.code);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Symptom s && s.code.equals(code);
    }

    @Override
    public int hashCode() {
        return code.hashCode();
    }

    @Override
    public String toString() {
        return code;
    }
}
