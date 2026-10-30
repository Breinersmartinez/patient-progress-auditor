export const ROLE_LABELS: Record<string, string> = {
  ENFERMERIA: 'Enfermería',
  MEDICO: 'Médico',
};

export const roleLabel = (code: string): string => ROLE_LABELS[code] ?? code;
