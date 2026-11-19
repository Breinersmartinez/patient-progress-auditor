import { AUDIO_TYPES, VOICE_LEVEL, hasVoice, pickAudioType } from './voice-recorder.service';

describe('Dictado: formato y detección de voz', () => {
  it('elige el formato de cada navegador, en orden de preferencia', () => {
    // Chrome, Edge y Brave: webm con opus.
    expect(pickAudioType((t) => t.startsWith('audio/webm'))).toBe('audio/webm;codecs=opus');
    // Un navegador que solo graba ogg (Firefox puede hacerlo).
    expect(pickAudioType((t) => t.startsWith('audio/ogg'))).toBe('audio/ogg;codecs=opus');
    // Safari: mp4.
    expect(pickAudioType((t) => t === 'audio/mp4')).toBe('audio/mp4');
    // Ninguno de los que transcribe el servidor.
    expect(pickAudioType(() => false)).toBeNull();
  });

  it('solo pide formatos que el servidor acepta', () => {
    expect(AUDIO_TYPES.map((t) => t.split(';')[0])).toEqual(['audio/webm', 'audio/webm', 'audio/ogg', 'audio/mp4']);
  });

  it('el silencio y el ruido bajo no cuentan como voz; tres muestras con voz sí', () => {
    expect(hasVoice([])).toBeFalse();
    expect(hasVoice(Array(60).fill(0.004))).toBeFalse();
    expect(hasVoice([0.003, VOICE_LEVEL, 0.001, VOICE_LEVEL * 3])).toBeFalse();
    expect(hasVoice([0.003, VOICE_LEVEL, 0.08, 0.001, 0.12])).toBeTrue();
  });
});
