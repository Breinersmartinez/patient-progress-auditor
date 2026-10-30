import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';

import { FakeEventSource } from '../core/runs.service.spec';
import { EVENT_SOURCE_FACTORY } from '../core/runs.service';
import { SessionService } from '../core/session.service';
import { ReportGenerateComponent } from './report-generate.component';

const PATIENTS = [{ id: 'p1', alias: 'P-014', bed: '204-A' }, { id: 'p2', alias: 'P-021' }];
const WEEK4 = (n: number, start: string, extra: object = {}) => ({ weekNo: n, weekStart: start, weekEnd: start, approved: true, reportId: 'w' + n, status: 'APROBADO', ...extra });
// Dos ventanas: la de septiembre tiene sus cuatro semanas aprobadas; la mas reciente tiene una semana sin informe.
const MONTHS = [
  { start: '2026-09-28', end: '2026-10-25', canGenerate: false, weeks: [],
    reason: 'No se puede generar el informe mensual: falta el informe semanal aprobado de la semana del 19 de octubre al 25 de octubre (no tiene informe semanal). Apruebe esos informes y vuelva a pedirlo.' },
  { start: '2026-09-21', end: '2026-10-18', canGenerate: true, weeks: [WEEK4(1, '2026-09-21'), WEEK4(2, '2026-09-28'), WEEK4(3, '2026-10-05'), WEEK4(4, '2026-10-12')] },
];
const WEEKS = [
  { weekStart: '2026-10-19', weekEnd: '2026-10-25', entries: 7, canGenerate: true },
  { weekStart: '2026-10-12', weekEnd: '2026-10-18', entries: 7, reportId: 'rep9', reportStatus: 'APROBADO', canGenerate: false, reason: 'Ya hay un informe de esta semana.' },
];

describe('ReportGenerateComponent (vInfGenerar del mockup)', () => {
  let http: HttpTestingController;
  let root: HTMLElement;
  let settle: () => Promise<void>;
  let sources: FakeEventSource[];
  let navigate: jasmine.Spy;

  async function open(routeId: string | null = null, llm: object = { available: true, primary: { configured: true } }) {
    sources = [];
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap(routeId ? { id: routeId } : {}) } } },
        { provide: EVENT_SOURCE_FACTORY, useValue: (url: string) => sources[sources.push(new FakeEventSource(url)) - 1] },
      ],
    });
    TestBed.inject(SessionService).currentId.set('u1');
    navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(ReportGenerateComponent);
    fixture.detectChanges();
    http.expectOne('/api/llm/status').flush(llm);
    http.expectOne('/api/patients').flush(PATIENTS);
    root = fixture.nativeElement;
    settle = async () => { await new Promise((resolve) => setTimeout(resolve)); fixture.detectChanges(); };
    await settle();
  }

  async function weeks(patient: string, answer: object[] = WEEKS, months: object[] = []) {
    http.expectOne(`/api/patients/${patient}/report-weeks`).flush(answer);
    await settle();
    http.expectOne(`/api/patients/${patient}/report-months`).flush(months);
    await settle();
  }

  afterEach(() => http.verify());

  const select = (id: string) => root.querySelector<HTMLSelectElement>(id)!;
  const button = () => root.querySelector<HTMLButtonElement>('#ig-btn')!;
  const stepTexts = () => Array.from(root.querySelectorAll('.pasos .est')).map((e) => e.textContent!.trim());

  it('muestra el paciente, el periodo de una semana y las semanas completas del servidor, con la linea de comparacion', async () => {
    await open();
    await weeks('p1');

    expect(Array.from(select('#ig-pac').options).map((o) => o.textContent!.trim())).toEqual(['P-014 (cama 204-A)', 'P-021']);
    expect(Array.from(select('#ig-tipo').options).map((o) => o.textContent!.trim())).toEqual(['Una semana', 'Un mes (cuatro semanas)']);
    expect(Array.from(select('#ig-sem').options).map((o) => o.textContent!.trim())).toEqual([
      'Del 19 al 25 de octubre de 2026', 'Del 12 al 18 de octubre de 2026 (ya tiene informe)']);
    expect(root.textContent).toContain('Se compara con la semana anterior; el agente decide cuántas semanas más atrás revisar.');
    expect(root.textContent).not.toContain('Rango de fechas');
    expect(button().textContent).toContain('Generar con IA');
  });

  it('preselecciona el paciente de la ruta y recarga las semanas al cambiar de paciente', async () => {
    await open('p2');
    expect(select('#ig-pac').value).toBe('p2');
    await weeks('p2', [WEEKS[0]]);

    select('#ig-pac').value = 'p1';
    select('#ig-pac').dispatchEvent(new Event('change'));
    await settle();
    await weeks('p1');
    expect(select('#ig-sem').options.length).toBe(2);
  });

  it('pide la ejecucion, muestra los pasos reales a medida que llegan y al terminar abre el informe', async () => {
    await open();
    await weeks('p1');

    button().click();
    await settle();
    const request = http.expectOne('/api/runs');
    expect(request.request.body).toEqual({ patientId: 'p1', weekStart: '2026-10-19' });
    request.flush({ id: 'r1', status: 'EN_COLA' }, { status: 202, statusText: 'Accepted' });
    await settle();

    expect(sources[0].url).toBe('/api/runs/r1/events?demoUser=u1');
    expect(button().getAttribute('aria-busy')).toBe('true');
    sources[0].emit('step', { id: 1, seq: 1, type: 'ESTADO', message: 'Consultando al modelo (llamada 1 de 5)…', error: false });
    await settle();
    expect(stepTexts()).toEqual(['Consultando al modelo (llamada 1 de 5)…']);
    sources[0].emit('step', { id: 2, seq: 2, type: 'HERRAMIENTA', message: 'Leyendo los registros de la semana del 2026-10-19', tool: 'get_week_entries', error: false });
    await settle();
    expect(stepTexts()).toEqual(['Consultando al modelo (llamada 1 de 5)…', 'Leyendo los registros de la semana del 2026-10-19']);
    expect(navigate).not.toHaveBeenCalled();

    sources[0].emit('end', { id: 'r1', status: 'COMPLETADA', reportId: 'rep1' });
    await settle();
    expect(navigate).toHaveBeenCalledWith(['/enf/informes/ver', 'rep1']);
    expect(button().getAttribute('aria-busy')).toBeNull();
  });

  it('una semana que ya tiene informe lo abre en lugar de pedir otro', async () => {
    await open();
    await weeks('p1');
    select('#ig-sem').value = '2026-10-12';
    select('#ig-sem').dispatchEvent(new Event('change'));
    await settle();

    button().click();
    await settle();
    expect(navigate).toHaveBeenCalledWith(['/enf/informes/ver', 'rep9']);
  });

  it('una semana que ya se esta generando se engancha a esa ejecucion', async () => {
    await open();
    await weeks('p1', [{ weekStart: '2026-10-19', weekEnd: '2026-10-25', entries: 7, runId: 'r7', canGenerate: true }]);

    button().click();
    await settle();
    http.expectOne('/api/runs/r7').flush({ id: 'r7', status: 'EN_CURSO' });
    await settle();
    expect(sources[0].url).toContain('/api/runs/r7/events');
  });

  it('una ejecucion fallida se explica con un aviso claro y no abre ningun informe', async () => {
    await open();
    await weeks('p1');
    button().click();
    await settle();
    http.expectOne('/api/runs').flush({ id: 'r1', status: 'EN_COLA' }, { status: 202, statusText: 'Accepted' });
    await settle();

    sources[0].emit('end', { id: 'r1', status: 'FALLIDA', message: 'El servicio de IA no responde en este momento. Intente de nuevo en unos minutos.' });
    await settle();
    expect(root.querySelector('.aviso')!.textContent).toContain('El servicio de IA no responde en este momento');
    expect(navigate).not.toHaveBeenCalled();
    expect(button().getAttribute('aria-busy')).toBeNull();
  });

  it('un informe incompleto tambien se abre, porque ahi esta el motivo', async () => {
    await open();
    await weeks('p1');
    button().click();
    await settle();
    http.expectOne('/api/runs').flush({ id: 'r1', status: 'EN_COLA' }, { status: 202, statusText: 'Accepted' });
    await settle();

    sources[0].emit('end', { id: 'r1', status: 'INCOMPLETA', reportId: 'rep2', message: 'Se agotó el límite' });
    await settle();
    expect(navigate).toHaveBeenCalledWith(['/enf/informes/ver', 'rep2']);
  });

  it('si el servidor rechaza el pedido (ejecutor lleno, 503), el mensaje va junto al boton, sin codigo crudo', async () => {
    await open();
    await weeks('p1');
    button().click();
    await settle();
    http.expectOne('/api/runs').flush({ message: 'El sistema está generando otros informes en este momento. Intente de nuevo en un minuto.' },
      { status: 503, statusText: 'Service Unavailable' });
    await settle();

    expect(root.querySelector('#ig-err')!.textContent).toBe('El sistema está generando otros informes en este momento. Intente de nuevo en un minuto.');
    expect(sources.length).toBe(0);
    expect(button().getAttribute('aria-busy')).toBeNull();
  });

  it('un paciente sin semanas completas con registros dice por que no hay con que generar', async () => {
    await open();
    await weeks('p1', []);
    button().click();
    await settle();
    expect(root.querySelector('#ig-err')!.textContent).toContain('no tiene registros en una semana completa');
  });

  it('avisa cuando falta la llave del proveedor', async () => {
    await open(null, { available: false, primary: { configured: false }, warning: 'Falta la llave del proveedor de IA.' });
    await weeks('p1');
    expect(root.querySelector('.aviso')!.textContent).toContain('Falta la llave del proveedor de IA.');
  });

  // --- informe mensual (E13) ---

  async function chooseMonth() {
    select('#ig-tipo').value = 'mes';
    select('#ig-tipo').dispatchEvent(new Event('change'));
    await settle();
  }

  it('el periodo «Un mes» ofrece las ventanas de cuatro semanas del servidor y marca la que no se puede generar', async () => {
    await open();
    await weeks('p1', WEEKS, MONTHS);

    await chooseMonth();

    expect(root.querySelector('#ig-semana')).toBeNull();
    expect(Array.from(select('#ig-m').options).map((o) => o.textContent!.trim())).toEqual([
      'Del 28 de septiembre al 25 de octubre de 2026 (faltan informes semanales aprobados)',
      'Del 21 de septiembre al 18 de octubre de 2026']);
    expect(root.textContent).toContain('El informe mensual resume la evolución de las cuatro semanas');
    // Se prefiere la primera ventana que se puede generar, y no se muestra ningun error
    expect(select('#ig-m').value).toBe('2026-09-21');
    expect(root.querySelector('#ig-err')!.textContent).toBe('');
  });

  it('una ventana con una semana sin aprobar dice cual falta, sin pedir nada al servidor', async () => {
    await open();
    await weeks('p1', WEEKS, MONTHS);
    await chooseMonth();

    select('#ig-m').value = '2026-09-28';
    select('#ig-m').dispatchEvent(new Event('change'));
    await settle();
    expect(root.querySelector('#ig-err')!.textContent).toContain('falta el informe semanal aprobado de la semana del 19 de octubre al 25 de octubre (no tiene informe semanal)');

    button().click();
    await settle();
    http.expectNone('/api/runs');
    expect(sources.length).toBe(0);
  });

  it('pide el mensual con el tipo y la primera semana, sigue los pasos y abre el informe', async () => {
    await open();
    await weeks('p1', WEEKS, MONTHS);
    await chooseMonth();

    button().click();
    await settle();
    const request = http.expectOne('/api/runs');
    expect(request.request.body).toEqual({ patientId: 'p1', weekStart: '2026-09-21', type: 'MENSUAL' });
    request.flush({ id: 'r5', runType: 'MENSUAL', status: 'EN_COLA' }, { status: 202, statusText: 'Accepted' });
    await settle();
    sources[0].emit('step', { id: 1, seq: 1, type: 'HERRAMIENTA', message: 'Leyendo los cuatro informes semanales aprobados del mes', error: false });
    await settle();
    expect(stepTexts()).toEqual(['Leyendo los cuatro informes semanales aprobados del mes']);

    sources[0].emit('end', { id: 'r5', status: 'COMPLETADA', reportId: 'mensual1' });
    await settle();
    expect(navigate).toHaveBeenCalledWith(['/enf/informes/ver', 'mensual1']);
  });

  it('si el servidor responde 409 con la semana que falta, el mensaje queda junto al boton', async () => {
    await open();
    await weeks('p1', WEEKS, MONTHS);
    await chooseMonth();
    button().click();
    await settle();

    http.expectOne('/api/runs').flush({ message: 'No se puede generar el informe mensual: falta el informe semanal aprobado de la semana del 5 de octubre al 11 de octubre (su informe está pendiente de revisión).' },
      { status: 409, statusText: 'Conflict' });
    await settle();

    expect(root.querySelector('#ig-err')!.textContent).toContain('su informe está pendiente de revisión');
    expect(button().getAttribute('aria-busy')).toBeNull();
  });

  it('un paciente sin cuatro semanas completas lo dice al pedir el mensual', async () => {
    await open();
    await weeks('p1', WEEKS, []);
    await chooseMonth();

    button().click();
    await settle();
    expect(root.querySelector('#ig-err')!.textContent).toContain('aún no tiene cuatro semanas completas seguidas');
  });

  it('un mes que ya tiene informe lo abre en lugar de pedir otro', async () => {
    await open();
    await weeks('p1', WEEKS, [{ ...MONTHS[1], reportId: 'mes7', reportStatus: 'PENDIENTE_REVISION', canGenerate: false, reason: 'Ya hay un informe mensual de ese período.' }]);
    await chooseMonth();

    expect(select('#ig-m').options[0].textContent).toContain('(ya tiene informe)');
    button().click();
    await settle();
    expect(navigate).toHaveBeenCalledWith(['/enf/informes/ver', 'mes7']);
  });
});
