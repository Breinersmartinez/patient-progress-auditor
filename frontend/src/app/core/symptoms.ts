// Los nueve sintomas del catalogo (D18) y sus intensidades, como los muestra el mockup. Cualquier otro sintoma se acepta (E18).
export const SYMPTOM_LABELS: Record<string, string> = {
  VOMITO: 'Vómito',
  DIARREA: 'Diarrea',
  NAUSEAS: 'Náuseas',
  FIEBRE: 'Fiebre',
  MAREO: 'Mareo',
  DOLOR_CABEZA: 'Dolor de cabeza',
  DOLOR_HUESO: 'Dolor de hueso',
  APETITO_BAJO: 'Apetito bajo',
  ANIMO_DECAIDO: 'Ánimo decaído',
};

export const SYMPTOM_CODES = Object.keys(SYMPTOM_LABELS);

// Las cuatro intensidades del mockup, en su orden (D27, confirmadas por el equipo el 28-oct).
export const INTENSITY_OPTIONS = [
  { code: 'SIN_INDICAR', label: 'Sin indicar' },
  { code: 'LEVE', label: 'leve' },
  { code: 'MODERADO', label: 'moderado' },
  { code: 'INTENSO', label: 'intenso' },
];

// Sin acentos, en mayúsculas y con guiones bajos: el mismo código que calcula el servidor («Picazón en los brazos» -> PICAZON_EN_LOS_BRAZOS).
export const normalizeCode = (typed: string): string =>
  typed.normalize('NFD').replace(/\p{M}/gu, '').toUpperCase().replace(/[^A-Z0-9]+/g, '_').replace(/^_+|_+$/g, '');

// El código del catálogo para lo que se escribió en el campo del síntoma (por su nombre o su código), o null si es otro síntoma.
export const symptomCode = (typed: string): string | null => {
  const code = normalizeCode(typed);
  return SYMPTOM_CODES.find((c) => c === code || normalizeCode(SYMPTOM_LABELS[c]) === code) ?? null;
};

// La misma forma que acepta el servidor para un síntoma escrito a mano: letras primero, hasta 60 caracteres.
export const looksLikeSymptom = (typed: string): boolean =>
  /^\p{L}[\p{L}\p{N} ,'()/-]{1,59}$/u.test(typed.trim().replace(/\s+/g, ' ')) && normalizeCode(typed).length >= 2;

const INTENSITY_LABELS: Record<string, string> = { LEVE: 'leve', MODERADO: 'moderado', INTENSO: 'intenso', SIN_INDICAR: '' };

export interface EntrySymptom {
  symptom: string;
  intensity: string;
  occurrences?: number;
  // El nombre de un síntoma fuera del catálogo (los nueve se nombran por su código).
  label?: string;
}

// DOLOR_DE_GARGANTA -> «Dolor de garganta» cuando no llega la etiqueta.
const prettify = (code: string): string => {
  const spaced = code.toLowerCase().replace(/_/g, ' ');
  return spaced.charAt(0).toUpperCase() + spaced.slice(1);
};

export const symptomName = (code: string, label?: string | null): string => SYMPTOM_LABELS[code] ?? label ?? prettify(code);

// sintTexto() del mockup: "Diarrea, intenso, 2 veces".
export const symptomText = (s: EntrySymptom): string =>
  [
    symptomName(s.symptom, s.label),
    INTENSITY_LABELS[s.intensity] ?? '',
    s.occurrences ? (s.occurrences === 1 ? '1 vez' : `${s.occurrences} veces`) : '',
  ]
    .filter(Boolean)
    .join(', ');
