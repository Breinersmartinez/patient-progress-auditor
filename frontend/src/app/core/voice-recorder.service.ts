import { Injectable } from '@angular/core';

// Dictado por voz (RF-38, D14): graba con el micrófono del navegador y mide si hubo voz. El audio vive solo en la memoria
// del navegador hasta que se envía a transcribir; no se guarda en ningún lado (condición del equipo, 2026-11-05).

// Formatos en orden de preferencia. Chrome, Edge y Brave graban webm/opus; Firefox, webm u ogg con opus; Safari, mp4/aac.
// Los tres los transcribe Whisper en Groq (probado el 2026-11-05) y el backend acepta solo estos.
export const AUDIO_TYPES = ['audio/webm;codecs=opus', 'audio/webm', 'audio/ogg;codecs=opus', 'audio/mp4'];

// La voz se graba a baja tasa: 120 s quedan por debajo de 1 MB (el servidor acepta hasta 5 MB).
const BITS_PER_SECOND = 32_000;
// Nivel (RMS, de 0 a 1) a partir del cual una muestra cuenta como voz, y cuántas muestras de 100 ms hacen falta.
// Whisper inventa texto con silencio o ruido («Gracias.», medido el 2026-11-05): sin voz no se envía. [Propuesta]
export const VOICE_LEVEL = 0.02;
export const VOICE_FRAMES = 3;

export type RecorderProblem = 'insecure' | 'unsupported' | 'denied' | 'no-mic' | 'error';

export class RecorderError extends Error {
  constructor(readonly problem: RecorderProblem) {
    super(problem);
  }
}

export interface RecordedAudio {
  blob: Blob;
  mimeType: string;
  seconds: number;
  voiceDetected: boolean;
}

export interface Recording {
  // Detiene la grabación, suelta el micrófono y entrega el audio.
  stop(): Promise<RecordedAudio>;
  // Descarta la grabación y suelta el micrófono.
  cancel(): void;
}

// El primer formato que el navegador sabe grabar, o null.
export function pickAudioType(isSupported: (type: string) => boolean): string | null {
  return AUDIO_TYPES.find((type) => isSupported(type)) ?? null;
}

// Si en las muestras de nivel hubo voz: al menos VOICE_FRAMES por encima de VOICE_LEVEL.
export function hasVoice(levels: number[], level = VOICE_LEVEL, frames = VOICE_FRAMES): boolean {
  return levels.filter((value) => value >= level).length >= frames;
}

@Injectable({ providedIn: 'root' })
export class VoiceRecorderService {
  // Por qué este navegador no puede dictar, o null si puede. No pide permiso: eso pasa al pulsar el micrófono.
  problem(): RecorderProblem | null {
    if (typeof window === 'undefined' || !window.isSecureContext) {
      return 'insecure';
    }
    if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === 'undefined'
        || !pickAudioType((type) => MediaRecorder.isTypeSupported(type))) {
      return 'unsupported';
    }
    return null;
  }

  // Pide el micrófono y empieza a grabar. `maxSeconds` corta solo; `onTick` recibe los segundos transcurridos.
  async start(maxSeconds: number, onTick: (seconds: number) => void, onLimit: () => void): Promise<Recording> {
    const problem = this.problem();
    if (problem) {
      throw new RecorderError(problem);
    }
    let stream: MediaStream;
    try {
      stream = await navigator.mediaDevices.getUserMedia({ audio: { channelCount: 1, echoCancellation: true, noiseSuppression: true } });
    } catch (e) {
      const name = (e as DOMException)?.name;
      throw new RecorderError(name === 'NotAllowedError' || name === 'SecurityError' ? 'denied'
        : name === 'NotFoundError' || name === 'OverconstrainedError' ? 'no-mic' : 'error');
    }
    const mimeType = pickAudioType((type) => MediaRecorder.isTypeSupported(type))!;
    const recorder = new MediaRecorder(stream, { mimeType, audioBitsPerSecond: BITS_PER_SECOND });
    const chunks: Blob[] = [];
    recorder.ondataavailable = (event) => {
      if (event.data.size > 0) {
        chunks.push(event.data);
      }
    };

    // El nivel de la voz, cada 100 ms, con un analizador que no graba nada: solo lee el volumen.
    const levels: number[] = [];
    const context = new AudioContext();
    const analyser = context.createAnalyser();
    analyser.fftSize = 2048;
    context.createMediaStreamSource(stream).connect(analyser);
    const samples = new Float32Array(analyser.fftSize);
    const started = Date.now();
    let finished = false;
    const timer = setInterval(() => {
      analyser.getFloatTimeDomainData(samples);
      let sum = 0;
      for (const value of samples) {
        sum += value * value;
      }
      levels.push(Math.sqrt(sum / samples.length));
      const seconds = Math.floor((Date.now() - started) / 1000);
      onTick(seconds);
      if (seconds >= maxSeconds && !finished) {
        onLimit();
      }
    }, 100);

    const release = () => {
      finished = true;
      clearInterval(timer);
      stream.getTracks().forEach((track) => track.stop());
      context.close().catch(() => undefined);
    };
    recorder.start(250);

    return {
      stop: () => new Promise<RecordedAudio>((resolve) => {
        recorder.onstop = () => {
          release();
          const blob = new Blob(chunks, { type: mimeType });
          chunks.length = 0;
          resolve({ blob, mimeType, seconds: (Date.now() - started) / 1000, voiceDetected: hasVoice(levels) });
        };
        if (recorder.state === 'inactive') {
          recorder.onstop?.(new Event('stop'));
        } else {
          recorder.stop();
        }
      }),
      cancel: () => {
        recorder.onstop = () => {
          chunks.length = 0;
        };
        if (recorder.state !== 'inactive') {
          recorder.stop();
        }
        release();
      },
    };
  }
}
