export const ROLE_LABELS: Record<string, string> = {
  ENFERMERIA: 'Enfermería',
  MEDICO: 'Médico',
};

export const roleLabel = (code: string): string => ROLE_LABELS[code] ?? code;

// El prefijo de ruta de cada rol (D26): en el mockup sale de la URL, aquí del usuario de demostración elegido.
export const rolePrefix = (role: string | undefined): string => (role === 'MEDICO' ? 'med' : 'enf');

// A dónde vuelve cada rol al cambiar de usuario o al no encontrar una ruta (como el mockup).
export const roleHome = (role: string | undefined): string => `/${rolePrefix(role)}/pacientes`;
