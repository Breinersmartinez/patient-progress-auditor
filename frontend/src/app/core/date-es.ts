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

// "21 sep": para los encabezados de una tabla angosta.
export const fechaCorta = (s: string): string => `${fch(s).getDate()} ${MESES[fch(s).getMonth()].slice(0, 3)}`;

export const unir = (a: string[]): string =>
  a.length < 2 ? a.join('') : a.slice(0, -1).join(', ') + ' y ' + a[a.length - 1];

// Periodo en una frase: "19 al 25 de octubre de 2026", o con los dos meses si cruza de mes (periodoTxt del mockup).
export const periodoTxt = (desde: string, hasta: string): string => {
  const a = fch(desde);
  const b = fch(hasta);
  if (desde === hasta) {
    return dFull(desde);
  }
  if (a.getMonth() === b.getMonth() && a.getFullYear() === b.getFullYear()) {
    return `${a.getDate()} al ${b.getDate()} de ${MESES[b.getMonth()]} de ${b.getFullYear()}`;
  }
  return `${a.getDate()} de ${MESES[a.getMonth()]} al ${b.getDate()} de ${MESES[b.getMonth()]} de ${b.getFullYear()}`;
};

// El dia (YYYY-MM-DD) de un instante del servidor, en la zona de la institucion (F7): el navegador no decide que dia es.
export const bogotaDay = (instant: string): string =>
  new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Bogota', year: 'numeric', month: '2-digit', day: '2-digit' })
    .format(new Date(instant));
