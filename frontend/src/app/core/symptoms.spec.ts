import { symptomName, symptomText } from './symptoms';

describe('textos de síntomas (sintTexto del mockup)', () => {
  it('nombra los nueve del catálogo y deja pasar un código desconocido', () => {
    expect(symptomName('DOLOR_CABEZA')).toBe('Dolor de cabeza');
    expect(symptomName('ANIMO_DECAIDO')).toBe('Ánimo decaído');
    expect(symptomName('OTRO')).toBe('OTRO');
  });

  it('une nombre, intensidad y veces, y omite lo que no se indicó', () => {
    expect(symptomText({ symptom: 'DIARREA', intensity: 'INTENSO', occurrences: 2 })).toBe('Diarrea, intenso, 2 veces');
    expect(symptomText({ symptom: 'VOMITO', intensity: 'LEVE', occurrences: 1 })).toBe('Vómito, leve, 1 vez');
    expect(symptomText({ symptom: 'APETITO_BAJO', intensity: 'SIN_INDICAR' })).toBe('Apetito bajo');
  });
});
