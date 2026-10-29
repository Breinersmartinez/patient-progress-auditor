import { HttpClient } from '@angular/common/http';
import { Injectable, InjectionToken, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { SessionService } from './session.service';

export type RunStatus = 'EN_COLA' | 'EN_CURSO' | 'COMPLETADA' | 'INCOMPLETA' | 'FALLIDA';

export interface Run {
  id: string;
  runType: 'INFORME' | 'MENSUAL' | 'ESTRUCTURACION';
  patientId: string;
  periodStart?: string;
  status: RunStatus;
  stopReason?: string;
  // El informe que dejo la ejecucion (un borrador o uno INCOMPLETO); ausente si fallo antes de escribir.
  reportId?: string;
  // El motivo si no termino bien, en lenguaje claro.
  message?: string;
  provider?: string;
  model?: string;
}

// Un paso de la traza tal como llega por SSE: es una fila de agent_steps (D24), no una animacion.
export interface RunStep {
  id: number;
  seq: number;
  type: string;
  message?: string;
  tool?: string;
  error: boolean;
  durationMs?: number;
}

export interface RequestedRun {
  run: Run;
  // Falso si ya habia una ejecucion viva del mismo paciente y semana y se devuelve esa.
  created: boolean;
}

export interface RunFollower {
  onStep: (step: RunStep) => void;
  onEnd: (run: Run) => void;
}

// Lo minimo de EventSource que usa la app: asi las pruebas ponen uno falso.
export interface EventSourceLike {
  readyState: number;
  onerror: ((event: Event) => void) | null;
  addEventListener(type: string, listener: (event: MessageEvent<string>) => void): void;
  close(): void;
}

export const EVENT_SOURCE_FACTORY = new InjectionToken<(url: string) => EventSourceLike>('EVENT_SOURCE_FACTORY', {
  providedIn: 'root',
  factory: () => (url: string) => new EventSource(url) as unknown as EventSourceLike,
});

const FINAL: RunStatus[] = ['COMPLETADA', 'INCOMPLETA', 'FALLIDA'];

@Injectable({ providedIn: 'root' })
export class RunsService {
  private readonly http = inject(HttpClient);
  private readonly session = inject(SessionService);
  private readonly newEventSource = inject(EVENT_SOURCE_FACTORY);

  // `weekStart` es el lunes de la semana o, si `type` es MENSUAL, el lunes de la primera de las cuatro semanas.
  async request(patientId: string, weekStart: string, type?: 'SEMANAL' | 'MENSUAL'): Promise<RequestedRun> {
    const body = type ? { patientId, weekStart, type } : { patientId, weekStart };
    const response = await firstValueFrom(this.http.post<Run>('/api/runs', body, { observe: 'response' }));
    return { run: response.body as Run, created: response.status === 202 };
  }

  get(id: string): Promise<Run> {
    return firstValueFrom(this.http.get<Run>(`/api/runs/${encodeURIComponent(id)}`));
  }

  // Sigue la ejecucion por SSE. EventSource no permite cabeceras: el usuario viaja en ?demoUser= (solo en esta ruta, E4).
  // Si la conexion se corta, el navegador reconecta solo con Last-Event-ID y el servidor reanuda; ademas se consulta el
  // estado, por si la ejecucion termino mientras no habia conexion. Devuelve la funcion que cierra el seguimiento.
  follow(runId: string, follower: RunFollower): () => void {
    const url = `/api/runs/${encodeURIComponent(runId)}/events?demoUser=${encodeURIComponent(this.session.currentId() ?? '')}`;
    const source = this.newEventSource(url);
    let closed = false;
    const finish = (run: Run): void => {
      if (!closed) {
        closed = true;
        source.close();
        follower.onEnd(run);
      }
    };
    source.addEventListener('step', (event) => {
      if (!closed) {
        follower.onStep(JSON.parse(event.data) as RunStep);
      }
    });
    source.addEventListener('end', (event) => finish(JSON.parse(event.data) as Run));
    source.onerror = () => {
      this.get(runId)
        .then((run) => {
          if (FINAL.includes(run.status)) {
            finish(run);
          }
        })
        .catch(() => undefined);
    };
    return () => {
      closed = true;
      source.close();
    };
  }
}
