import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';

import { ReportViewComponent } from './report-view.component';

const CONTENT = {
  period: { start: '2026-10-19', end: '2026-10-25', compared_with: '2026-10-12' },
  summary: 'La semana tiene registros casi todos los días.',
  claims: [
    { claim_key: 'c1', text: 'Las náuseas se registraron en 4 días, frente a 0 la semana anterior.', criterion: 'Días con el síntoma frente a la semana anterior',
      entry_ids: ['e1', 'e2'], figures: [{ key: 'symptoms.NAUSEAS.days', value: 4 }] },
    { claim_key: 'c2', text: 'El ánimo se describe estable.', criterion: 'Texto de los registros', entry_ids: ['e3'], figures: [] },
  ],
  changes_vs_previous: ['c1'],
  recurring_patterns: ['c1'],
  metrics: {
    week: '2026-10-19', entries: 6, days_with_entry: 6, days_without_entry: 1, missing_dates: ['2026-10-25'], unstructured_entries: 0, total_events: 4,
    symptoms: { NAUSEAS: { days: 4, dates: [], events: 4, occurrences: 6 } },
    vs_previous: { week: '2026-10-12', comparable: true, previous_entries: 7, days: { NAUSEAS: [0, 4], FIEBRE: [2, 0] } },
  },
  insufficient_data: [{ kind: 'DIAS_SIN_REGISTRO', dates: ['2026-10-25'], text: 'No hay registros el domingo 25 de octubre.' }],
  out_of_scope: null,
};

const REPORT = {
  id: 'rep1', patientId: 'p1', patientAlias: 'P-014', reportType: 'SEMANAL', periodStart: '2026-10-19', periodEnd: '2026-10-25',
  status: 'PENDIENTE_REVISION', generatedAt: '2026-10-29T15:00:00Z', provider: 'groq', model: 'openai/gpt-oss-120b',
};

const CITATIONS = {
  c1: [{ id: 'e1', date: '2026-10-20', body: 'Náuseas leves tras el desayuno.', author: 'Marcela' }, { id: 'e2', date: '2026-10-21', body: 'Náuseas otra vez.' }],
  c2: [{ id: 'e3', date: '2026-10-22', body: 'Ánimo estable, conversa con la familia.' }],
};

const detail = (over: object = {}, report: object = {}) => ({
  report: { ...REPORT, ...report }, requestedBy: 'Camilo Torres', runId: 'run1',
  version: { versionNo: 1, origin: 'MODELO', createdAt: '2026-10-29T15:00:00Z', content: CONTENT },
  versions: [{ versionNo: 1, origin: 'MODELO', createdAt: '2026-10-29T15:00:00Z' }], citations: CITATIONS, ...over,
});

// Un informe mensual (E13): `metrics` trae las cuatro semanas; el contenido enlaza los cuatro informes semanales de origen.
const WEEK = (week: string, entries: number, withEntry: number, nausea: number) => ({
  week, entries, days_with_entry: withEntry, days_without_entry: 7 - withEntry, missing_dates: [], unstructured_entries: 0, total_events: nausea,
  symptoms: nausea ? { NAUSEAS: { days: nausea, dates: [], events: nausea } } : {},
  vs_previous: { week: '', comparable: true, previous_entries: 0, days: {} },
});
const MONTHLY_CONTENT = {
  period: { start: '2026-09-21', end: '2026-10-18', weeks: ['2026-09-21', '2026-09-28', '2026-10-05', '2026-10-12'] },
  summary: 'Las náuseas reaparecen en la tercera semana.',
  claims: [{ claim_key: 'c1', text: 'Las náuseas pasan de 3 días en la primera semana a 0 en la segunda.', criterion: 'Primera semana frente a segunda semana',
    entry_ids: ['e1'], figures: [{ key: 'symptoms.NAUSEAS.days@2026-09-21', value: 3 }] }],
  changes_vs_previous: ['c1'],
  recurring_patterns: ['c1'],
  metrics: { weeks: [WEEK('2026-09-21', 7, 7, 3), WEEK('2026-09-28', 7, 7, 0), WEEK('2026-10-05', 6, 6, 2), WEEK('2026-10-12', 7, 7, 0)] },
  weekly_reports: [
    { week_no: 1, week_start: '2026-09-21', report_id: 'w1', summary: 'Náuseas en tres días.' },
    { week_no: 2, week_start: '2026-09-28', report_id: 'w2', summary: 'Sin síntomas.' },
    { week_no: 3, week_start: '2026-10-05', report_id: 'w3', summary: 'Náuseas vuelven.' },
    { week_no: 4, week_start: '2026-10-12', report_id: 'w4', summary: 'Sin síntomas.' },
  ],
  insufficient_data: [{ kind: 'DIAS_SIN_REGISTRO', week: '2026-10-05', dates: ['2026-10-11'], text: 'Semana del 5 de octubre al 11 de octubre: No hay registros el domingo 11 de octubre.' }],
  out_of_scope: null,
};

describe('ReportViewComponent (vInfVer y hojaHTML del mockup)', () => {
  let http: HttpTestingController;
  let root: HTMLElement;
  let settle: () => Promise<void>;

  async function open(answer: object | 404 = detail()) {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: 'rep1' }) } } }],
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(ReportViewComponent);
    fixture.detectChanges();
    const request = http.expectOne('/api/reports/rep1');
    if (answer === 404) {
      request.flush({ message: 'No encontramos ese informe.' }, { status: 404, statusText: 'Not Found' });
    } else {
      request.flush(answer);
    }
    root = fixture.nativeElement;
    settle = async () => { await new Promise((resolve) => setTimeout(resolve)); fixture.detectChanges(); };
    await settle();
  }

  afterEach(() => http.verify());

  const click = async (selector: string) => { root.querySelector<HTMLElement>(selector)!.click(); await settle(); };
  const text = (selector: string) => root.querySelector(selector)!.textContent!.replace(/\s+/g, ' ').trim();
  const type = (selector: string, value: string) => {
    const el = root.querySelector<HTMLInputElement | HTMLTextAreaElement>(selector)!;
    el.value = value;
    el.dispatchEvent(new Event('input'));
  };

  it('dibuja la hoja: encabezado, resumen con [n], cifras del codigo, patron, fuentes con el texto original y el pie', async () => {
    await open();

    expect(text('.acciones .izq b')).toBe('Informe de P-014');
    expect(text('.acciones .est')).toBe('En revisión');
    expect(text('.hoja h1')).toBe('Informe de seguimiento semanal');
    expect(text('.hoja .sub')).toBe('Paciente P-014, semana del 19 al 25 de octubre de 2026');
    const meta = text('.hoja .meta');
    expect(meta).toContain('Comparado con: 12 al 18 de octubre de 2026');
    expect(meta).toContain('Registros revisados: 6 de 7 días');
    expect(meta).toContain('Generado: 29 de octubre de 2026');
    expect(meta).toContain('Aprobado por: pendiente de aprobación');

    const summary = text('.hoja .resumen');
    expect(summary).toContain('La semana tiene registros casi todos los días.');
    expect(summary).toContain('Las náuseas se registraron en 4 días, frente a 0 la semana anterior.[1]');
    expect(summary).toContain('No hay registros el domingo 25 de octubre.[3]');

    const table = Array.from(root.querySelectorAll('.hoja table tr')).map((tr) => Array.from(tr.children).map((c) => c.textContent!.trim()).join(' '));
    expect(table).toEqual(['Síntoma Período de comparación Esta semana', 'Registros 7 6', 'Náuseas No registrado 4 días, 6 veces', 'Fiebre 2 días No registrado']);
    expect(root.textContent).toContain('Cifras calculadas por el sistema a partir de los registros, no por la IA.');
    expect(text('.hoja .resumen:nth-of-type(2)')).toBeTruthy();
    expect(root.textContent).toContain('Patrón a destacar');

    const sources = Array.from(root.querySelectorAll('.hoja ol li')).map((li) => li.textContent!.replace(/\s+/g, ' ').trim());
    expect(sources).toEqual([
      '[1] mar 20: "Náuseas leves tras el desayuno." / mié 21: "Náuseas otra vez."',
      '[2] jue 22: "Ánimo estable, conversa con la familia."',
      '[3] dom 25: sin registro',
    ]);
    // Cada fuente enlaza a la ficha del paciente.
    expect(root.querySelector('.hoja ol li a')!.getAttribute('href')).toBe('/enf/pacientes/ficha/p1');
    expect(text('.hoja .pie')).toContain('No contiene diagnóstico ni recomendación de tratamiento.');
  });

  it('cada [n] del resumen es un enlace a su fila de fuentes', async () => {
    await open();
    const target = root.querySelector<HTMLElement>('#fuente-2')!;
    const scroll = spyOn(target, 'scrollIntoView');
    root.querySelector<HTMLElement>('.hoja sup a[aria-label="Ir a la fuente 2"]')!.click();
    expect(scroll).toHaveBeenCalled();
  });

  it('en revision ofrece rechazar, editar y aprobar, y avisa que es la vista previa del medico', async () => {
    await open();
    expect(Array.from(root.querySelectorAll('.acciones [data-a]')).map((b) => b.getAttribute('data-a'))).toEqual(['traza', 'rechazar', 'editar', 'aprobar']);
    expect(text('.acciones .nota')).toContain('Revise el texto y las fuentes.');
    expect(root.querySelector('.aviso')!.textContent).toContain('Esta es la vista previa tal como la verá el médico.');
  });

  it('aprobar llama al servidor, muestra el informe aprobado con quien lo aprobo y quita los botones de decision', async () => {
    await open();
    await click('[data-a="aprobar"]');
    const request = http.expectOne('/api/reports/rep1/approve');
    expect(request.request.method).toBe('POST');
    request.flush(detail({ approvedVersionNo: 1 }, { status: 'APROBADO', reviewedBy: 'Camilo Torres', reviewedAt: '2026-10-29T16:00:00Z' }));
    await settle();

    expect(text('.acciones .est')).toBe('Aprobado');
    expect(text('.acciones .nota')).toBe('Informe aprobado y guardado. Ya puede consultarlo el médico y descargarlo en PDF.');
    expect(text('.hoja .meta')).toContain('Aprobado por: Camilo Torres');
    expect(root.querySelector('[data-a="aprobar"]')).toBeNull();
    expect(root.querySelector('[data-a="editar"]')).toBeNull();
    expect(root.querySelector('[data-a="descargar"]')).toBeTruthy();
    expect(root.textContent).not.toContain('vista previa tal como la verá el médico');
  });

  const APPROVED = () => detail({ approvedVersionNo: 1 }, { status: 'APROBADO', reviewedBy: 'Camilo Torres', reviewedAt: '2026-10-29T16:00:00Z' });

  it('«Descargar PDF» solo existe en un informe aprobado', async () => {
    await open();
    expect(root.querySelector('[data-a="descargar"]')).toBeNull();
  });

  it('«Descargar PDF» pide el PDF al servidor y lo descarga con el nombre que el servidor propone (RF-37)', async () => {
    await open(APPROVED());
    const anchors: HTMLAnchorElement[] = [];
    spyOn(URL, 'createObjectURL').and.returnValue('blob:informe');
    const revoke = spyOn(URL, 'revokeObjectURL');
    spyOn(HTMLAnchorElement.prototype, 'click').and.callFake(function (this: HTMLAnchorElement) { anchors.push(this); });

    await click('[data-a="descargar"]');
    const request = http.expectOne('/api/reports/rep1/pdf');
    expect(request.request.method).toBe('GET');
    expect(request.request.responseType).toBe('blob');
    request.flush(new Blob(['%PDF-1.7'], { type: 'application/pdf' }),
      { headers: { 'Content-Disposition': 'attachment; filename="informe-semanal-P-014-2026-10-19.pdf"' } });
    await settle();

    expect(anchors.length).toBe(1);
    expect(anchors[0].download).toBe('informe-semanal-P-014-2026-10-19.pdf');
    expect(anchors[0].href).toBe('blob:informe');
    expect(revoke).toHaveBeenCalledWith('blob:informe');
    expect(root.querySelector('.aviso[role="alert"]')).toBeNull();
  });

  it('si el servidor niega el PDF, muestra su motivo en un aviso y no descarga nada', async () => {
    await open(APPROVED());
    const create = spyOn(URL, 'createObjectURL');

    await click('[data-a="descargar"]');
    http.expectOne('/api/reports/rep1/pdf').flush(new Blob([JSON.stringify({ message: 'Solo se puede imprimir un informe aprobado. Este está pendiente revision.' })], { type: 'application/json' }),
      { status: 409, statusText: 'Conflict' });
    // Leer el cuerpo del error (un Blob) es asincrono: se espera a que aparezca el aviso.
    for (let i = 0; i < 20 && !root.querySelector('.aviso[role="alert"]'); i++) { await settle(); }

    expect(text('.aviso[role="alert"]')).toBe('Solo se puede imprimir un informe aprobado. Este está pendiente revision.');
    expect(create).not.toHaveBeenCalled();
  });

  it('si el servidor no deja aprobar (409), lo explica en un aviso y el informe sigue igual', async () => {
    await open();
    await click('[data-a="aprobar"]');
    http.expectOne('/api/reports/rep1/approve').flush({ message: 'No se puede aprobar este informe: ya fue aprobado y no se modifica.' },
      { status: 409, statusText: 'Conflict' });
    await settle();
    expect(root.querySelector('[role="alert"]')!.textContent).toContain('No se puede aprobar este informe');
    expect(text('.acciones .est')).toBe('En revisión');
  });

  it('rechazar pide el motivo, no manda nada sin el y muestra el rechazo con su motivo', async () => {
    await open();
    await click('[data-a="rechazar"]');
    await click('[data-a="confirmar-rechazo"]');
    expect(text('[data-e="motivo"]')).toBe('Escriba el motivo del rechazo.');
    http.expectNone('/api/reports/rep1/reject');

    type('#ir-motivo', 'No concuerda con el registro del martes.');
    await click('[data-a="confirmar-rechazo"]');
    const request = http.expectOne('/api/reports/rep1/reject');
    expect(request.request.body).toEqual({ reason: 'No concuerda con el registro del martes.' });
    request.flush(detail({}, { status: 'RECHAZADO', statusReason: 'No concuerda con el registro del martes.', reviewedBy: 'Camilo Torres' }));
    await settle();

    expect(text('.acciones .est')).toBe('Rechazado');
    expect(root.querySelector('.aviso')!.textContent).toContain('Informe rechazado por Camilo Torres: No concuerda con el registro del martes.');
    expect(root.querySelector('[data-a="aprobar"]')).toBeNull();
    expect(root.textContent).toContain('Generar de nuevo');
  });

  it('un informe incompleto muestra su motivo, no se puede aprobar y ofrece generar de nuevo', async () => {
    await open(detail({}, { status: 'INCOMPLETO', statusReason: 'Se agotó el límite de 5 llamadas al modelo.' }));
    expect(text('.acciones .est')).toBe('Incompleto');
    expect(root.querySelector('.aviso')!.textContent).toContain('Este informe quedó incompleto: Se agotó el límite de 5 llamadas al modelo.');
    expect(root.querySelector('[data-a="aprobar"]')).toBeNull();
    expect(root.querySelector('[data-a="editar"]')).toBeNull();
    expect(root.querySelector('.acciones a.btn')!.getAttribute('href')).toBe('/enf/informes/generar/p1');
  });

  it('un informe incompleto por salida invalida muestra aparte el texto del modelo sin validar, marcado como tal', async () => {
    await open(detail({ version: { versionNo: 1, origin: 'MODELO', createdAt: 'x', content: { ...CONTENT, claims: [], summary: 'El agente no terminó el borrador.' },
      plainText: '(Sin validar) El vómito reapareció.' } }, { status: 'INCOMPLETO', statusReason: 'El borrador no cumplió el formato.' }));
    expect(text('[data-t="sin-validar"]')).toContain('El vómito reapareció.');
    expect(root.textContent).toContain('Texto del modelo, sin validar');
    expect(root.textContent).toContain('no es parte del informe');
  });

  it('«Ver cómo se generó» abre la traza a un clic con herramienta, parametros, resultado, duracion y tokens', async () => {
    await open();
    await click('[data-a="traza"]');
    http.expectOne('/api/reports/rep1/trace').flush([
      { id: 1, seq: 1, type: 'HERRAMIENTA', message: 'Calculando las cifras de la semana del 2026-10-19', tool: 'get_week_metrics', params: { week_start: '2026-10-19' },
        result: { chars: 900, text: '{"week":"2026-10-19"}' }, error: false, durationMs: 12, at: '2026-10-29T15:00:01Z' },
      { id: 2, seq: 2, type: 'LLAMADA_LLM', message: 'groq openai/gpt-oss-120b, intento 1', error: false, durationMs: 4876, tokensIn: 1509, tokensOut: 17, at: '2026-10-29T15:00:02Z' },
    ]);
    await settle();

    const rows = Array.from(root.querySelectorAll('[data-t="paso"]')).map((tr) => Array.from(tr.querySelectorAll('td')).map((td) => td.textContent!.trim()));
    expect(rows[0]).toEqual(['Calculando las cifras de la semana del 2026-10-19', 'get_week_metrics', '{"week_start":"2026-10-19"}', '{"week":"2026-10-19"}', '12 ms', '—']);
    expect(rows[1]).toEqual(['groq openai/gpt-oss-120b, intento 1', '—', '—', '—', '4876 ms', '1509 + 17']);
    await click('[data-a="traza"]');
    expect(root.querySelector('[data-t="paso"]')).toBeNull();
  });

  describe('edicion', () => {
    async function edit() {
      await open();
      await click('[data-a="editar"]');
      const request = http.expectOne((r) => r.url === '/api/patients/p1/entries');
      expect(request.request.params.get('from')).toBe('2026-10-12');
      expect(request.request.params.get('to')).toBe('2026-10-25');
      request.flush([
        { id: 'e1', patientId: 'p1', entryDate: '2026-10-20', body: 'Náuseas leves tras el desayuno.', authorName: 'M', symptoms: [] },
        { id: 'e4', patientId: 'p1', entryDate: '2026-10-23', body: 'Comió poco en el almuerzo.', authorName: 'M', symptoms: [] },
      ]);
      await settle();
    }

    it('convierte la hoja en un formulario con resumen, texto, criterio y fuentes de cada afirmacion', async () => {
      await edit();
      expect((root.querySelector('#ie-resumen') as HTMLTextAreaElement).value).toBe(CONTENT.summary);
      expect((root.querySelector('#ie-texto-0') as HTMLTextAreaElement).value).toBe(CONTENT.claims[0].text);
      expect((root.querySelector('#ie-criterio-1') as HTMLInputElement).value).toBe('Texto de los registros');
      expect(root.querySelectorAll('[data-t="edicion"]')[0].querySelectorAll('[data-a="quitar-fuente"]').length).toBe(2);
      // Lo que se puede agregar: los registros de la semana y la anterior (y los que otra afirmacion cita) que esta no cita todavia.
      const options = Array.from(root.querySelectorAll<HTMLSelectElement>('[data-a="agregar-fuente"]')[0].options).map((o) => o.textContent!.trim());
      expect(options).toEqual(['Agregar una fuente…', 'jue 22: "Ánimo estable, conversa con la familia."', 'vie 23: "Comió poco en el almuerzo."']);
      expect(root.querySelector('[data-a="aprobar"]')).toBeNull();
      expect(root.querySelector('[data-a="guardar"]')).not.toBeNull();
    });

    it('guarda lo editado: texto nuevo, una fuente quitada y otra agregada', async () => {
      await edit();
      type('#ie-texto-0', 'Las náuseas se registraron en 4 días.');
      type('#ie-criterio-0', 'Días con el síntoma esta semana');
      await click('[data-t="edicion"] [data-a="quitar-fuente"]');
      const select = root.querySelector<HTMLSelectElement>('[data-a="agregar-fuente"]')!;
      select.value = 'e4';
      select.dispatchEvent(new Event('change'));
      await settle();

      await click('[data-a="guardar"]');
      const request = http.expectOne('/api/reports/rep1/content');
      expect(request.request.method).toBe('PUT');
      expect(request.request.body.summary).toBe(CONTENT.summary);
      expect(request.request.body.claims[0]).toEqual({ claimKey: 'c1', text: 'Las náuseas se registraron en 4 días.',
        criterion: 'Días con el síntoma esta semana', entryIds: ['e2', 'e4'] });
      expect(request.request.body.claims[1].entryIds).toEqual(['e3']);

      const saved = detail({
        version: { versionNo: 2, origin: 'ENFERMERIA', editedBy: 'Camilo Torres', createdAt: '2026-10-29T16:00:00Z',
          content: { ...CONTENT, claims: [{ ...CONTENT.claims[0], text: 'Las náuseas se registraron en 4 días.' }, CONTENT.claims[1]] } },
        versions: [{ versionNo: 1, origin: 'MODELO', createdAt: 'x' }, { versionNo: 2, origin: 'ENFERMERIA', editedBy: 'Camilo Torres', createdAt: 'y' }],
      });
      request.flush(saved);
      await settle();
      expect(root.querySelector('#ie-resumen')).toBeNull();
      expect(text('.hoja .resumen')).toContain('Las náuseas se registraron en 4 días.[1]');
      expect(root.querySelector('[data-a="ia"]')).not.toBeNull();
    });

    it('un rechazo del servidor (422) se muestra junto al campo que lo causo y conserva lo escrito', async () => {
      await edit();
      type('#ie-texto-0', 'Parece una infección.');
      await click('[data-a="guardar"]');
      http.expectOne('/api/reports/rep1/content').flush(
        { message: 'Revise lo que cambió: el informe no se guardó.', fields: { 'claims[0].text': 'Contiene «infección»: el informe no diagnostica ni recomienda tratamiento ni dosis.' } },
        { status: 422, statusText: 'Unprocessable Entity' });
      await settle();

      expect(text('[data-e="texto-0"]')).toContain('Contiene «infección»');
      expect((root.querySelector('#ie-texto-0') as HTMLTextAreaElement).value).toBe('Parece una infección.');
      expect(root.querySelector('[data-a="guardar"]')).not.toBeNull();
    });

    it('cancelar vuelve a la hoja sin llamar al servidor', async () => {
      await edit();
      type('#ie-texto-0', 'Otro texto');
      await click('[data-a="cancelar"]');
      http.expectNone('/api/reports/rep1/content');
      expect(root.querySelector('#ie-resumen')).toBeNull();
      expect(text('.hoja .resumen')).toContain('Las náuseas se registraron en 4 días, frente a 0 la semana anterior.');
    });
  });

  it('con una version editada ofrece ver lo que escribio la IA (la version 1, que no se toca)', async () => {
    await open(detail({
      version: { versionNo: 2, origin: 'ENFERMERIA', editedBy: 'Camilo Torres', createdAt: 'y', content: { ...CONTENT, summary: 'Resumen corregido.' } },
      versions: [{ versionNo: 1, origin: 'MODELO', createdAt: 'x' }, { versionNo: 2, origin: 'ENFERMERIA', editedBy: 'Camilo Torres', createdAt: 'y' }],
    }));
    expect(text('.hoja .resumen')).toContain('Resumen corregido.');
    await click('[data-a="ia"]');
    http.expectOne('/api/reports/rep1/versions/1').flush({ versionNo: 1, origin: 'MODELO', createdAt: 'x', content: CONTENT });
    await settle();
    expect(text('[data-t="version-ia"]')).toContain('Lo que escribió la IA (versión 1)');
    expect(text('[data-t="version-ia"]')).toContain(CONTENT.summary);
    expect(text('[data-t="version-ia"]')).toContain(CONTENT.claims[1].text);
  });

  it('un informe que no existe (o que el rol no ve) dice «No encontramos lo que busca»', async () => {
    await open(404);
    expect(root.textContent).toContain('No encontramos lo que busca');
    expect(root.querySelector('.hoja')).toBeNull();
  });

  it('dibuja el informe mensual: titulo, evolucion semana a semana, los cuatro informes de origen enlazados y las mismas fuentes', async () => {
    await open(detail({ version: { versionNo: 1, origin: 'MODELO', createdAt: '2026-10-29T15:00:00Z', content: MONTHLY_CONTENT }, citations: { c1: CITATIONS['c1'] } },
      { id: 'rep1', reportType: 'MENSUAL', periodStart: '2026-09-21', periodEnd: '2026-10-18' }));

    expect(text('.hoja h1')).toBe('Informe de seguimiento mensual');
    expect(text('.hoja .sub')).toBe('Paciente P-014, del 21 de septiembre al 18 de octubre de 2026 (cuatro semanas)');
    const meta = text('.hoja .meta');
    expect(meta).toContain('Semanas incluidas: 4 informes semanales aprobados');
    expect(meta).toContain('Registros revisados: 27 de 28 días');
    expect(meta).not.toContain('Comparado con');

    const table = Array.from(root.querySelectorAll('.hoja table[data-t="evolucion"] tr')).map((tr) => Array.from(tr.children).map((c) => c.textContent!.trim()).join(' | '));
    expect(table).toEqual([
      'Síntoma | Semana 1 (21 sep) | Semana 2 (28 sep) | Semana 3 (5 oct) | Semana 4 (12 oct)',
      'Registros | 7 | 7 | 6 | 7',
      'Náuseas | 3 días | No registrado | 2 días | No registrado',
    ]);
    expect(root.textContent).toContain('Evolución semana a semana');
    expect(root.textContent).not.toContain('Cifras de la semana');

    const origin = Array.from(root.querySelectorAll('.hoja ol[data-t="origen"] li')).map((li) => li.textContent!.replace(/\s+/g, ' ').trim());
    expect(origin[0]).toContain('Semana 1 (21 al 27 de septiembre de 2026): Náuseas en tres días.');
    expect(origin[3]).toContain('Semana 4 (12 al 18 de octubre de 2026): Sin síntomas.');
    expect(root.querySelector('.hoja ol[data-t="origen"] li a')!.getAttribute('href')).toBe('/enf/informes/ver/w1');

    // El resumen, las afirmaciones con su [n], lo que falta y las fuentes funcionan igual que en el semanal
    expect(text('.hoja .resumen')).toContain('Las náuseas pasan de 3 días en la primera semana a 0 en la segunda.[1]');
    expect(text('.hoja .resumen')).toContain('Semana del 5 de octubre al 11 de octubre: No hay registros el domingo 11 de octubre.[2]');
    expect(Array.from(root.querySelectorAll('.hoja ol:not([data-t]) li')).map((li) => li.textContent!.replace(/\s+/g, ' ').trim())).toEqual([
      '[1] mar 20: "Náuseas leves tras el desayuno." / mié 21: "Náuseas otra vez."',
      '[2] dom 11: sin registro',
    ]);
    // Se aprueba como cualquier informe pendiente
    expect(root.querySelector('[data-a="aprobar"]')).toBeTruthy();
  });
});
