import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';

import { SessionService } from '../core/session.service';
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
    if (view === 'recibidos') {
      TestBed.inject(SessionService).session.set({
        id: 'm1', displayName: 'Dr. Andrés Salazar', role: 'MEDICO',
        permissions: ['PATIENTS_READ', 'ENTRIES_READ', 'ENTRIES_CREATE', 'REPORTS_READ'],
      });
    }
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

  it('un informe enviado sale en «Aprobados» con el estado «Enviado» y «Generado» sigue siendo la fecha de generacion (D29)', async () => {
    const sent = { ...base, id: 'e', patientAlias: 'P-014', periodStart: '2026-10-05', periodEnd: '2026-10-11', status: 'ENVIADO',
      reviewedBy: 'Marcela Rojas', sentBy: 'Marcela Rojas', sentAt: '2026-10-31T15:00:00Z' };
    const root = await open('aprobados', [...REPORTS, sent]);
    expect(rowsOf(root)).toContain('P-014 5 al 11 de octubre de 2026 28 de octubre de 2026 Enviado');
    expect(rowsOf(root).join('|')).not.toContain('31 de octubre');
    expect(Array.from(root.querySelectorAll('.est')).some((chip) => chip.textContent!.trim() === 'Enviado')).toBeTrue();
  });

  it('sin informes muestra el estado vacio con el camino a generar', async () => {
    const root = await open('revision', []);
    expect(root.textContent).toContain('No hay informes por revisar');
    expect(root.querySelector('.vacio a')!.getAttribute('href')).toBe('/enf/informes/generar');
  });

  it('«Recibidos» (el médico) no filtra: el servidor ya solo le da los ENVIADO, y «Recibido» es la fecha de envío', async () => {
    const sent = { ...base, id: 'e', patientAlias: 'P-014', periodStart: '2026-10-05', periodEnd: '2026-10-11', status: 'ENVIADO',
      reviewedBy: 'Marcela Rojas', sentBy: 'Marcela Rojas', sentAt: '2026-10-31T15:00:00Z' };
    const root = await open('recibidos', [sent]);

    expect(Array.from(root.querySelectorAll('th')).map((th) => th.textContent)).toEqual(['Paciente', 'Período', 'Recibido', 'Estado']);
    expect(rowsOf(root)).toEqual(['P-014 5 al 11 de octubre de 2026 31 de octubre de 2026 Enviado']);
    expect(root.querySelector('tbody tr')!.getAttribute('data-href')).toBe('/med/informes/ver/e');
  });

  it('«Recibidos» (el médico) sin informes invita a armar un informe propio (Pieza 6, D31)', async () => {
    const root = await open('recibidos', []);

    expect(root.textContent).toContain('Aún no ha recibido informes');
    expect(root.textContent).toContain('También puede armar uno propio');
    expect(root.querySelector('.vacio a')!.getAttribute('href')).toBe('/med/informes/armar');
  });
});
