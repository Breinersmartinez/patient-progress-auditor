// Ayudantes de fecha en espanol (del mockup). Las fechas son 'YYYY-MM-DD'; se parsean a mediodia para no
// cruzar de dia por la zona horaria del navegador.
const DIAS = ['dom', 'lun', 'mar', 'mié', 'jue', 'vie', 'sáb'];
const DIAS_L = ['domingo', 'lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado'];
const MESES = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre',
  'noviembre', 'diciembre'];

export const fch = (s: string): Date => new Date(s + 'T12:00:00');

export const isoDe = (d: Date): string =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

export const sumar = (s: string, n: number): string => {
  const d = fch(s);
  d.setDate(d.getDate() + n);
  return isoDe(d);
};

export const dCorto = (s: string): string => `${DIAS[fch(s).getDay()]} ${fch(s).getDate()}`;

export const dLargo = (s: string): string =>
  `${DIAS_L[fch(s).getDay()]} ${fch(s).getDate()} de ${MESES[fch(s).getMonth()]}`;

export const dFull = (s: string): string =>
  `${fch(s).getDate()} de ${MESES[fch(s).getMonth()]} de ${fch(s).getFullYear()}`;

// Semana de lunes a domingo que empieza en `ini` (RN-06).
export const rango = (ini: string): string => {
  const a = fch(ini);
  const b = fch(sumar(ini, 6));
  return `${a.getDate()} al ${b.getDate()} de ${MESES[b.getMonth()]} de ${b.getFullYear()}`;
};

export const unir = (a: string[]): string =>
  a.length < 2 ? a.join('') : a.slice(0, -1).join(', ') + ' y ' + a[a.length - 1];
