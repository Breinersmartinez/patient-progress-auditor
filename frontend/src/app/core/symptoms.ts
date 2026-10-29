// Catalogo cerrado de nueve sintomas (D18) y sus intensidades, como los muestra el mockup.
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

// El código del catálogo para lo que se escribió en el campo del síntoma, o null si no es uno de los nueve.
export const symptomCode = (typed: string): string | null =>
  SYMPTOM_CODES.find((code) => SYMPTOM_LABELS[code].toLowerCase() === typed.trim().toLowerCase()) ?? null;

const INTENSITY_LABELS: Record<string, string> = { LEVE: 'leve', MODERADO: 'moderado', INTENSO: 'intenso', SIN_INDICAR: '' };

export interface EntrySymptom {
  symptom: string;
  intensity: string;
  occurrences?: number;
}

export const symptomName = (code: string): string => SYMPTOM_LABELS[code] ?? code;

// sintTexto() del mockup: "Diarrea, intenso, 2 veces".
export const symptomText = (s: EntrySymptom): string =>
  [
    symptomName(s.symptom),
    INTENSITY_LABELS[s.intensity] ?? '',
    s.occurrences ? (s.occurrences === 1 ? '1 vez' : `${s.occurrences} veces`) : '',
  ]
    .filter(Boolean)
    .join(', ');
