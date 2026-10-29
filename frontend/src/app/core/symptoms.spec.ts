import { looksLikeSymptom, normalizeCode, symptomCode, symptomName, symptomText } from './symptoms';

describe('textos de síntomas (sintTexto del mockup)', () => {
  it('nombra los nueve del catálogo y deja pasar un código desconocido', () => {
    expect(symptomName('DOLOR_CABEZA')).toBe('Dolor de cabeza');
    expect(symptomName('ANIMO_DECAIDO')).toBe('Ánimo decaído');
    expect(symptomName('TOS_SECA')).toBe('Tos seca');
  });

  it('un síntoma fuera del catálogo usa su etiqueta, con tildes, y la reserva se arma del código', () => {
    expect(symptomName('PICAZON_EN_LOS_BRAZOS', 'Picazón en los brazos')).toBe('Picazón en los brazos');
    expect(symptomText({ symptom: 'DOLOR_DE_GARGANTA', label: 'Dolor de garganta', intensity: 'LEVE', occurrences: 2 }))
      .toBe('Dolor de garganta, leve, 2 veces');
  });

  it('calcula el mismo código que el servidor y reconoce los nueve por nombre o por código', () => {
    expect(normalizeCode('  Picazón   en los brazos ')).toBe('PICAZON_EN_LOS_BRAZOS');
    expect(symptomCode('dolor de cabeza')).toBe('DOLOR_CABEZA');
    expect(symptomCode('Animo decaido')).toBe('ANIMO_DECAIDO');
    expect(symptomCode('NAUSEAS')).toBe('NAUSEAS');
    expect(symptomCode('tos seca')).toBeNull();
  });

  it('acepta un nombre con forma de síntoma y rechaza números, símbolos y textos largos', () => {
    expect(looksLikeSymptom('dolor de garganta')).toBeTrue();
    expect(looksLikeSymptom('picazón (brazos)')).toBeTrue();
    expect(looksLikeSymptom('12345')).toBeFalse();
    expect(looksLikeSymptom('<script>')).toBeFalse();
    expect(looksLikeSymptom('x')).toBeFalse();
    expect(looksLikeSymptom('a'.repeat(61))).toBeFalse();
  });

  it('une nombre, intensidad y veces, y omite lo que no se indicó', () => {
    expect(symptomText({ symptom: 'DIARREA', intensity: 'INTENSO', occurrences: 2 })).toBe('Diarrea, intenso, 2 veces');
    expect(symptomText({ symptom: 'VOMITO', intensity: 'LEVE', occurrences: 1 })).toBe('Vómito, leve, 1 vez');
    expect(symptomText({ symptom: 'APETITO_BAJO', intensity: 'SIN_INDICAR' })).toBe('Apetito bajo');
  });
});
