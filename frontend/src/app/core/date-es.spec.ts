import { dCorto, dFull, dLargo, fch, isoDe, periodoTxt, rango, sumar, unir } from './date-es';

describe('ayudantes de fecha en español', () => {
  it('suma días cruzando el mes sin desplazarse un día', () => {
    expect(sumar('2026-10-25', 1)).toBe('2026-10-26');
    expect(sumar('2026-09-28', 7)).toBe('2026-10-05');
    expect(sumar('2026-10-19', -28)).toBe('2026-09-21');
  });

  it('da los nombres cortos, largos y completos', () => {
    expect(dCorto('2026-10-25')).toBe('dom 25');
    expect(dLargo('2026-10-25')).toBe('domingo 25 de octubre');
    expect(dFull('2026-10-25')).toBe('25 de octubre de 2026');
  });

  it('escribe la semana de lunes a domingo', () => {
    expect(rango('2026-10-19')).toBe('19 al 25 de octubre de 2026');
    expect(fch('2026-10-19').getDay()).toBe(1);
  });

  it('escribe un periodo dentro de un mes, entre dos meses y de un solo día', () => {
    expect(periodoTxt('2026-10-19', '2026-10-25')).toBe('19 al 25 de octubre de 2026');
    expect(periodoTxt('2026-09-28', '2026-10-25')).toBe('28 de septiembre al 25 de octubre de 2026');
    expect(periodoTxt('2026-10-25', '2026-10-25')).toBe('25 de octubre de 2026');
  });

  it('une con comas y una "y" final', () => {
    expect(unir([])).toBe('');
    expect(unir(['lunes'])).toBe('lunes');
    expect(unir(['lunes', 'martes', 'jueves'])).toBe('lunes, martes y jueves');
  });

  it('isoDe invierte a fch', () => {
    expect(isoDe(fch('2026-02-01'))).toBe('2026-02-01');
  });
});
