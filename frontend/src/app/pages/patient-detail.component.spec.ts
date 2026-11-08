import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';

import { Entry } from '../core/entries.service';
import { PageTitleService } from '../core/page-title.service';
import { Patient } from '../core/patients.service';
import { SessionService } from '../core/session.service';
import { PatientDetailComponent } from './patient-detail.component';

const PATIENT: Patient = {
  id: 'p1', alias: 'P-014', bed: '204-A', age: 71, admittedOn: '2026-09-21',
  lastReport: { id: 'r1', type: 'SEMANAL', periodStart: '2026-10-19', periodEnd: '2026-10-25', status: 'APROBADO' },
};

const entry = (date: string, body: string): Entry => ({
  id: date, patientId: 'p1', patientAlias: 'P-014', entryDate: date, body, authorName: 'Camilo Torres',
  structuringStatus: 'ESTRUCTURADA', createdAt: date + 'T23:00:00Z', symptoms: [],
});

describe('PatientDetailComponent (vPacFicha del mockup)', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: 'p1' }) } } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    TestBed.inject(SessionService).session.set({
      id: 'n1', displayName: 'Camilo Torres', role: 'ENFERMERIA',
      permissions: ['PATIENTS_READ', 'ENTRIES_READ', 'ENTRIES_CREATE', 'REPORTS_READ', 'REPORTS_REQUEST'],
    });
  });

  afterEach(() => http.verify());

  async function open(patient: Patient | 404, entries: Entry[] = []) {
    const fixture = TestBed.createComponent(PatientDetailComponent);
    fixture.detectChanges();
    const request = http.expectOne('/api/patients/p1');
    if (patient === 404) {
      request.flush({ message: 'No encontramos ese paciente.' }, { status: 404, statusText: 'Not Found' });
    } else {
      request.flush(patient);
      await fixture.whenStable();
      const list = http.expectOne((r) => r.url === '/api/patients/p1/entries');
      expect(list.request.params.get('order')).toBe('desc');
      expect(list.request.params.get('limit')).toBe('7');
      list.flush(entries);
    }
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('muestra el título, los datos, las acciones y el último informe', async () => {
    const root = await open(PATIENT, [entry('2026-10-25', 'Tranquilo.')]);

    expect(root.querySelector('.cab h2')?.textContent).toBe('Paciente P-014');
    expect(root.querySelector('.cab .sec')?.textContent).toBe('Cama 204-A, 71 años, ingreso el 21 de septiembre de 2026');
    expect(root.querySelector('.acc a.btn--sec')?.getAttribute('href')).toBe('/enf/informes/generar/p1');
    expect(root.querySelector('.acc a.btn--primary')?.getAttribute('href')).toBe('/enf/registro/nuevo/p1');
    const report = root.querySelector('a.card--nav');
    expect(report?.querySelector('b')?.textContent).toBe('Semana del 19 al 25 de octubre de 2026');
    expect(report?.textContent).toContain('Aprobado');
    expect(report?.getAttribute('href')).toBe('/enf/informes/ver/r1');
    expect(TestBed.inject(PageTitleService).title()).toBe('Paciente P-014');
  });

  it('lista los registros recientes como tarjetas', async () => {
    const root = await open(PATIENT, [entry('2026-10-25', 'Tranquilo.'), entry('2026-10-24', 'Sin novedades.')]);

    expect(root.querySelectorAll('.bloque .grid article.card').length).toBe(2);
  });

  it('sin informe ni registros ni datos, no inventa ninguno y ofrece crear el primero', async () => {
    const root = await open({ id: 'p1', alias: 'P-045' }, []);

    expect(root.querySelector('.cab .sec')).toBeNull();
    expect(root.querySelector('a.card--nav')).toBeNull();
    expect(root.querySelector('.bloque .vacio h3')?.textContent).toBe('Sin registros todavía');
    expect(root.querySelector('.bloque .vacio a')?.getAttribute('href')).toBe('/enf/registro/nuevo/p1');
  });

  it('un paciente que no existe muestra "No encontramos lo que busca"', async () => {
    const root = await open(404);

    expect(root.querySelector('.vacio h3')?.textContent).toBe('No encontramos lo que busca');
    expect(root.querySelector('.cab')).toBeNull();
  });

  describe('con un médico (sin REPORTS_REQUEST, D12)', () => {
    beforeEach(() => {
      TestBed.inject(SessionService).session.set({
        id: 'm1', displayName: 'Dr. Andrés Salazar', role: 'MEDICO',
        permissions: ['PATIENTS_READ', 'ENTRIES_READ', 'ENTRIES_CREATE', 'REPORTS_READ'],
      });
    });

    it('no ofrece "Generar informe", pero sí "Nuevo registro" con su propio prefijo', async () => {
      const root = await open(PATIENT, [entry('2026-10-25', 'Tranquilo.')]);

      expect(root.querySelector('.acc a.btn--sec')).toBeNull();
      expect(root.querySelector('.acc a.btn--primary')?.getAttribute('href')).toBe('/med/registro/nuevo/p1');
    });

    it('el último informe dice "recibido" y enlaza con el prefijo del médico', async () => {
      const root = await open(PATIENT, [entry('2026-10-25', 'Tranquilo.')]);

      expect(root.querySelector('h3.sec')?.textContent).toBe('Último informe recibido');
      expect(root.querySelector('a.card--nav')?.getAttribute('href')).toBe('/med/informes/ver/r1');
    });

    it('sin registros no ofrece el atajo de crear el primero (fiel al mockup)', async () => {
      const root = await open({ id: 'p1', alias: 'P-045' }, []);

      expect(root.querySelector('.bloque .vacio h3')?.textContent).toBe('Sin registros todavía');
      expect(root.querySelector('.bloque .vacio a')).toBeNull();
    });
  });
});
