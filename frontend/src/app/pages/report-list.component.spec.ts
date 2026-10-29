import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';

import { ReportListComponent } from './report-list.component';

const base = { patientId: 'p1', reportType: 'SEMANAL', generatedAt: '2026-10-29T03:30:00Z' };
const REPORTS = [
  { ...base, id: 'a', patientAlias: 'P-014', periodStart: '2026-10-19', periodEnd: '2026-10-25', status: 'PENDIENTE_REVISION' },
  { ...base, id: 'b', patientAlias: 'P-021', periodStart: '2026-10-12', periodEnd: '2026-10-18', status: 'APROBADO' },
  { ...base, id: 'c', patientAlias: 'P-007', periodStart: '2026-10-05', periodEnd: '2026-10-11', status: 'INCOMPLETO' },
  { ...base, id: 'd', patientAlias: 'P-033', periodStart: '2026-09-28', periodEnd: '2026-10-04', status: 'RECHAZADO' },
];

describe('ReportListComponent (vInfLista del mockup)', () => {
  let http: HttpTestingController;

  async function open(view: string, answer: object[] = REPORTS) {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { data: { view } } } }],
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(ReportListComponent);
    fixture.detectChanges();
    http.expectOne('/api/reports').flush(answer);
    await new Promise((resolve) => setTimeout(resolve));
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  afterEach(() => http.verify());

  const rowsOf = (root: HTMLElement) => Array.from(root.querySelectorAll('tbody tr')).map((r) => r.textContent!.replace(/\s+/g, ' ').trim());

  it('«En revisión» lista solo los pendientes, con paciente, periodo, fecha del servidor (Bogota) y estado', async () => {
    const root = await open('revision');
    expect(Array.from(root.querySelectorAll('th')).map((th) => th.textContent)).toEqual(['Paciente', 'Período', 'Generado', 'Estado']);
    // 03:30 UTC del 29 es el 28 a las 22:30 en Bogota.
    expect(rowsOf(root)).toEqual(['P-014 19 al 25 de octubre de 2026 28 de octubre de 2026 En revisión']);
    expect(root.querySelector('tbody tr')!.getAttribute('data-href')).toBe('/enf/informes/ver/a');
  });

  it('«Aprobados» lista todo lo que no esta en revision: aprobados, incompletos y rechazados', async () => {
    const root = await open('aprobados');
    expect(rowsOf(root).map((r) => r.split(' ')[0])).toEqual(['P-021', 'P-007', 'P-033']);
    expect(rowsOf(root).join('|')).toContain('Aprobado');
    expect(rowsOf(root).join('|')).toContain('Incompleto');
    expect(rowsOf(root).join('|')).toContain('Rechazado');
  });

  it('sin informes muestra el estado vacio con el camino a generar', async () => {
    const root = await open('revision', []);
    expect(root.textContent).toContain('No hay informes por revisar');
    expect(root.querySelector('.vacio a')!.getAttribute('href')).toBe('/enf/informes/generar');
  });
});
