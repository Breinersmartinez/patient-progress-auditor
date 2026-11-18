import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';

import { SessionService } from '../core/session.service';
import { ConsultationViewComponent } from './consultation-view.component';

const CONTENT = {
  period: { start: '2026-10-19', end: '2026-10-25', compared_with: '2026-10-12' },
  summary: 'Las náuseas aparecieron en el período consultado.',
  claims: [
    { claim_key: 'c1', text: 'Las náuseas aparecieron 4 días en el período.', criterion: 'Días del período frente al período de comparación',
      entry_ids: ['e1'], figures: [{ key: 'symptoms.NAUSEAS.days', value: 4 }] },
  ],
  changes_vs_previous: ['c1'],
  recurring_patterns: ['c1'],
  metrics: {
    week: '2026-10-19', entries: 7, days_with_entry: 7, days_without_entry: 0, missing_dates: [], unstructured_entries: 0, total_events: 4,
    symptoms: { NAUSEAS: { days: 4, dates: [], events: 4, occurrences: 4 } },
    vs_previous: { week: '2026-10-12', comparable: true, previous_entries: 7, days: { NAUSEAS: [0, 4] } },
  },
  insufficient_data: [],
  out_of_scope: null,
};

const DETAIL = {
  id: 'cons1', patientId: 'p1', patientAlias: 'P-014', periodStart: '2026-10-19', periodEnd: '2026-10-25',
  comparisonStart: '2026-10-12', comparisonEnd: '2026-10-18', content: CONTENT, runId: 'run1', createdAt: '2026-10-29T15:00:00Z',
  citations: { c1: [{ id: 'e1', date: '2026-10-20', body: 'Náuseas leves tras el desayuno.' }] },
};

describe('ConsultationViewComponent («Informe de consulta» del mockup, D31, RF-46)', () => {
  let http: HttpTestingController;
  let root: HTMLElement;
  let settle: () => Promise<void>;

  async function open(answer: object | 404 = DETAIL) {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: 'cons1' }) }, paramMap: of(convertToParamMap({ id: 'cons1' })) } }],
    });
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(SessionService).session.set({ id: 'm1', displayName: 'Dr. Andrés Salazar', role: 'MEDICO', permissions: ['CONSULTATIONS_REQUEST'] });
    const fixture = TestBed.createComponent(ConsultationViewComponent);
    fixture.detectChanges();
    const request = http.expectOne('/api/consultations/cons1');
    if (answer === 404) {
      request.flush({ message: 'No encontramos esa consulta.' }, { status: 404, statusText: 'Not Found' });
    } else {
      request.flush(answer);
    }
    root = fixture.nativeElement;
    settle = async () => { await new Promise((resolve) => setTimeout(resolve)); fixture.detectChanges(); };
    await settle();
    return root;
  }

  afterEach(() => http.verify());

  const text = (selector: string) => root.querySelector(selector)?.textContent?.replace(/\s+/g, ' ').trim();
  const click = async (selector: string) => { root.querySelector<HTMLElement>(selector)!.click(); await settle(); };

  it('muestra la marca «Consulta del médico», el resumen con sus citas y las cifras de la semana', async () => {
    await open();

    expect(text('.acciones .izq b')).toBe('Informe de P-014');
    expect(root.querySelectorAll('.marca').length).toBe(2);
    expect(root.querySelector('.marca')!.textContent).toBe('CONSULTA DEL MÉDICO');
    expect(root.textContent).toContain('Consulta personal: se arma con los registros del período');
    expect(text('h1')).toBe('Informe de consulta');
    // Un período de exactamente 7 días se lee como semana, como hojaHTML del mockup (largo() === 7).
    expect(text('.sub')).toBe('Paciente P-014, semana del 19 al 25 de octubre de 2026');
    expect(root.textContent).toContain('Las náuseas aparecieron 4 días en el período.');
    expect(root.textContent).toContain('Comparado con:');
    expect(root.textContent).toContain('12 al 18 de octubre de 2026');
    expect(root.textContent).toContain('Registros revisados: 7 de 7 días');
    expect(root.textContent).toContain('Aprobado por: no aplica, es una consulta del médico');
    expect(text('h2')).toBe('Resumen de la evolución');
    expect(Array.from(root.querySelectorAll('h2')).map((h) => h.textContent)).toContain('Cifras de la semana');
    expect(text('table')).toContain('Registros');
    expect(root.textContent).toContain('4 días');
    expect(root.textContent).toContain('Patrón a destacar');
    expect(root.textContent).toContain('mar 20');
    expect(root.textContent).toContain('Náuseas leves tras el desayuno');
    expect(root.textContent).toContain('No pasó por la revisión de enfermería');
    expect(root.textContent).toContain('Revisado por: el médico que lo solicita');
  });

  it('un período que no es una semana completa se lee como período, no como semana', async () => {
    await open({
      ...DETAIL, periodStart: '2026-10-01', periodEnd: '2026-10-15',
      content: { ...CONTENT, period: { ...CONTENT.period, start: '2026-10-01', end: '2026-10-15' } },
    });

    expect(text('.sub')).toBe('Paciente P-014, período del 1 al 15 de octubre de 2026');
    expect(Array.from(root.querySelectorAll('h2')).map((h) => h.textContent)).toContain('Cifras del período');
  });

  it('sin comparación lo dice en vez de inventar un período', async () => {
    const noComparison = {
      ...DETAIL, comparisonStart: undefined, comparisonEnd: undefined,
      content: { ...CONTENT, period: { ...CONTENT.period, compared_with: null } },
    };
    await open(noComparison);

    expect(root.textContent).toContain('Comparado con: sin comparación');
  });

  it('una consulta de otro médico (404) dice que no se encontró', async () => {
    await open(404);

    expect(text('.vacio h3')).toBe('No encontramos lo que busca');
    expect(root.querySelector('.acciones')).toBeNull();
  });

  it('«Descargar PDF» pide el PDF al servidor y lo descarga con el nombre que el servidor propone', async () => {
    await open();
    const anchors: HTMLAnchorElement[] = [];
    spyOn(URL, 'createObjectURL').and.returnValue('blob:consulta');
    const revoke = spyOn(URL, 'revokeObjectURL');
    spyOn(HTMLAnchorElement.prototype, 'click').and.callFake(function (this: HTMLAnchorElement) { anchors.push(this); });

    await click('[data-a="descargar"]');
    const request = http.expectOne('/api/consultations/cons1/pdf');
    expect(request.request.responseType).toBe('blob');
    request.flush(new Blob(['%PDF-1.7'], { type: 'application/pdf' }),
      { headers: { 'Content-Disposition': 'attachment; filename="consulta-P-014-2026-10-19.pdf"' } });
    await settle();

    expect(anchors.length).toBe(1);
    expect(anchors[0].download).toBe('consulta-P-014-2026-10-19.pdf');
    expect(anchors[0].href).toBe('blob:consulta');
    expect(revoke).toHaveBeenCalledWith('blob:consulta');
  });

  it('si el servidor niega el PDF (contenido sin validar), lo explica en un aviso', async () => {
    await open();
    const create = spyOn(URL, 'createObjectURL');

    await click('[data-a="descargar"]');
    http.expectOne('/api/consultations/cons1/pdf').flush(
      new Blob([JSON.stringify({ message: 'Esta consulta no tiene un contenido estructurado que se pueda imprimir.' })], { type: 'application/json' }),
      { status: 409, statusText: 'Conflict' });
    for (let i = 0; i < 20 && !root.querySelector('.aviso[role="alert"]'); i++) { await settle(); }

    expect(text('.aviso[role="alert"]')).toBe('Esta consulta no tiene un contenido estructurado que se pueda imprimir.');
    expect(create).not.toHaveBeenCalled();
  });
});
