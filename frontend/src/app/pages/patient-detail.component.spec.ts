import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';

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
  // La ruta reutiliza la instancia al pasar de una ficha a otra (misma ruta, `:id` distinto): este sujeto simula
  // el paramMap real de Angular, para poder empujarle un id nuevo sin recrear el componente.
  let paramMap: BehaviorSubject<ReturnType<typeof convertToParamMap>>;

  beforeEach(() => {
    paramMap = new BehaviorSubject(convertToParamMap({ id: 'p1' }));
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: 'p1' }) }, paramMap } },
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
      // Abrir la ficha marca leidas las notificaciones del paciente (D30); solo el medico la dispara.
      http.match('/api/patients/p1/notifications/read').forEach((r) => r.flush(null));
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

    // D30, RF-45: abrir la ficha marca leidas las notificaciones de registro de este paciente, como el mockup.
    it('al abrir la ficha, pide marcar leidas las notificaciones de este paciente', async () => {
      const fixture = TestBed.createComponent(PatientDetailComponent);
      fixture.detectChanges();
      http.expectOne('/api/patients/p1').flush(PATIENT);
      await fixture.whenStable();
      http.expectOne((r) => r.url === '/api/patients/p1/entries').flush([]);

      const request = http.expectOne('/api/patients/p1/notifications/read');
      expect(request.request.method).toBe('POST');
      request.flush(null);
    });
  });

  it('con enfermería, abrir la ficha no pide marcar ninguna notificación (no tiene el permiso)', async () => {
    const fixture = TestBed.createComponent(PatientDetailComponent);
    fixture.detectChanges();
    http.expectOne('/api/patients/p1').flush(PATIENT);
    await fixture.whenStable();
    http.expectOne((r) => r.url === '/api/patients/p1/entries').flush([]);

    // No queda ninguna peticion de notificaciones pendiente; si la hubiera, el afterEach (http.verify()) fallaría.
    expect(http.match('/api/patients/p1/notifications/read').length).toBe(0);
  });

  // Hallazgo de la validación de la Pieza 5 (2026-11-08): la campana lleva de una ficha a otra por la misma ruta
  // (distinto `:id`), y la pantalla se quedaba mostrando al paciente anterior.
  describe('navegar de una ficha a otra por la misma ruta (misma instancia)', () => {
    const settle = async (fixture: ComponentFixture<PatientDetailComponent>) => {
      await new Promise((resolve) => setTimeout(resolve));
      fixture.detectChanges();
    };

    it('al cambiar el id, recarga con el paciente nuevo y vuelve a pedir marcar leído', async () => {
      TestBed.inject(SessionService).session.set({
        id: 'm1', displayName: 'Dr. Andrés Salazar', role: 'MEDICO',
        permissions: ['PATIENTS_READ', 'ENTRIES_READ', 'ENTRIES_CREATE', 'REPORTS_READ'],
      });
      const fixture = TestBed.createComponent(PatientDetailComponent);
      fixture.detectChanges();
      http.expectOne('/api/patients/p1').flush(PATIENT);
      await settle(fixture);
      http.expectOne((r) => r.url === '/api/patients/p1/entries').flush([]);
      http.expectOne('/api/patients/p1/notifications/read').flush(null);
      await settle(fixture);

      paramMap.next(convertToParamMap({ id: 'p2' }));
      await settle(fixture);
      http.expectOne('/api/patients/p2').flush({ id: 'p2', alias: 'P-021' });
      await settle(fixture);
      http.expectOne((r) => r.url === '/api/patients/p2/entries').flush([]);
      const markNew = http.expectOne('/api/patients/p2/notifications/read');
      markNew.flush(null);
      await settle(fixture);

      const root: HTMLElement = fixture.nativeElement;
      expect(root.querySelector('.cab h2')?.textContent).toBe('Paciente P-021');
    });

    it('si una respuesta vieja llega tarde (ya se navegó a otro paciente), no pisa lo que se está viendo', async () => {
      const fixture = TestBed.createComponent(PatientDetailComponent);
      fixture.detectChanges();
      http.expectOne('/api/patients/p1').flush(PATIENT);
      await settle(fixture);
      http.expectOne((r) => r.url === '/api/patients/p1/entries').flush([]);
      await settle(fixture);

      paramMap.next(convertToParamMap({ id: 'p2' }));
      await settle(fixture);
      const lenta = http.expectOne('/api/patients/p2');

      paramMap.next(convertToParamMap({ id: 'p3' }));
      await settle(fixture);
      const rapida = http.expectOne('/api/patients/p3');
      rapida.flush({ id: 'p3', alias: 'P-033' });
      await settle(fixture);
      http.expectOne((r) => r.url === '/api/patients/p3/entries').flush([]);
      await settle(fixture);

      const root: HTMLElement = fixture.nativeElement;
      expect(root.querySelector('.cab h2')?.textContent).toBe('Paciente P-033');

      // p2 responde tarde, despues de p3: no debe pisar lo que ya se ve.
      lenta.flush({ id: 'p2', alias: 'P-021' });
      await settle(fixture);
      expect(root.querySelector('.cab h2')?.textContent).toBe('Paciente P-033');
    });
  });
});
