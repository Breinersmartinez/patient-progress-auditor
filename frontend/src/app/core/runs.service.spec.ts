import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { EVENT_SOURCE_FACTORY, EventSourceLike, Run, RunStep, RunsService } from './runs.service';
import { SessionService } from './session.service';

export class FakeEventSource implements EventSourceLike {
  readyState = 1;
  onerror: ((event: Event) => void) | null = null;
  closed = false;
  private readonly listeners: Record<string, (event: MessageEvent<string>) => void> = {};

  constructor(readonly url: string) {}

  addEventListener(type: string, listener: (event: MessageEvent<string>) => void): void {
    this.listeners[type] = listener;
  }

  close(): void {
    this.closed = true;
  }

  emit(type: string, data: unknown): void {
    this.listeners[type]({ data: JSON.stringify(data) } as MessageEvent<string>);
  }
}

describe('RunsService', () => {
  let http: HttpTestingController;
  let service: RunsService;
  let sources: FakeEventSource[];

  beforeEach(() => {
    sources = [];
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(), provideHttpClientTesting(),
        { provide: EVENT_SOURCE_FACTORY, useValue: (url: string) => sources[sources.push(new FakeEventSource(url)) - 1] },
      ],
    });
    TestBed.inject(SessionService).currentId.set('u 1');
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(RunsService);
  });

  afterEach(() => http.verify());

  it('pide la ejecucion y dice si es nueva (202) o la que ya corria (200)', async () => {
    const created = service.request('p1', '2026-10-19');
    const first = http.expectOne('/api/runs');
    expect(first.request.body).toEqual({ patientId: 'p1', weekStart: '2026-10-19' });
    first.flush({ id: 'r1', status: 'EN_COLA' }, { status: 202, statusText: 'Accepted' });
    expect((await created).created).toBeTrue();

    const reused = service.request('p1', '2026-10-19');
    http.expectOne('/api/runs').flush({ id: 'r1', status: 'EN_CURSO' });
    const answer = await reused;
    expect(answer.created).toBeFalse();
    expect(answer.run.id).toBe('r1');
  });

  it('sigue la ejecucion por SSE con el usuario elegido en la ruta, entrega cada paso y cierra al terminar', () => {
    const steps: RunStep[] = [];
    let end: Run | undefined;
    service.follow('r 1', { onStep: (s) => steps.push(s), onEnd: (r) => (end = r) });

    expect(sources[0].url).toBe('/api/runs/r%201/events?demoUser=u%201');
    sources[0].emit('step', { id: 5, seq: 1, type: 'ESTADO', message: 'Consultando', error: false });
    sources[0].emit('step', { id: 6, seq: 2, type: 'HERRAMIENTA', message: 'Leyendo', error: false });
    expect(steps.map((s) => s.message)).toEqual(['Consultando', 'Leyendo']);
    expect(end).toBeUndefined();

    sources[0].emit('end', { id: 'r1', status: 'COMPLETADA', reportId: 'rep1' });
    expect(end?.reportId).toBe('rep1');
    expect(sources[0].closed).toBeTrue();
  });

  it('si la conexion se corta, consulta el estado: una ejecucion que ya termino cierra el seguimiento', async () => {
    let end: Run | undefined;
    service.follow('r1', { onStep: () => undefined, onEnd: (r) => (end = r) });

    sources[0].onerror?.(new Event('error'));
    http.expectOne('/api/runs/r1').flush({ id: 'r1', status: 'EN_CURSO' });
    await Promise.resolve();
    expect(end).toBeUndefined();

    sources[0].onerror?.(new Event('error'));
    http.expectOne('/api/runs/r1').flush({ id: 'r1', status: 'FALLIDA', message: 'Interrumpida por un reinicio del servidor.' });
    await new Promise((resolve) => setTimeout(resolve));
    expect(end?.status).toBe('FALLIDA');
    expect(sources[0].closed).toBeTrue();
  });

  it('el cierre manual detiene el flujo y no entrega mas pasos', () => {
    const steps: RunStep[] = [];
    const stop = service.follow('r1', { onStep: (s) => steps.push(s), onEnd: () => undefined });
    stop();
    sources[0].emit('step', { id: 1, seq: 1, type: 'ESTADO', error: false });
    expect(sources[0].closed).toBeTrue();
    expect(steps).toEqual([]);
  });
});
