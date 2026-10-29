import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { Patient } from '../core/patients.service';
import { PatientsListComponent } from './patients-list.component';

const PATIENTS: Patient[] = [
  { id: 'a', alias: 'P-007', bed: '215-A', age: 58, admittedOn: '2026-09-21', lastEntryDate: '2026-10-25' },
  {
    id: 'b', alias: 'P-014', bed: '204-A', age: 71, admittedOn: '2026-09-21', lastEntryDate: '2026-10-25',
    lastReport: { id: 'r1', type: 'SEMANAL', periodStart: '2026-10-19', periodEnd: '2026-10-25', status: 'PENDIENTE_REVISION' },
  },
  { id: 'c', alias: 'P-045' },
];

describe('PatientsListComponent (vPacLista del mockup)', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function load(answer: Patient[] | 'error') {
    const fixture = TestBed.createComponent(PatientsListComponent);
    fixture.detectChanges();
    const request = http.expectOne('/api/patients');
    if (answer === 'error') {
      request.flush({ message: 'El servidor no respondió.' }, { status: 500, statusText: 'Error' });
    } else {
      request.flush(answer);
    }
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('lista cada paciente con cama, edad, último registro y último informe', async () => {
    const root = await load(PATIENTS);
    const rows = Array.from(root.querySelectorAll('tbody tr'));
    const cells = (row: Element) => Array.from(row.querySelectorAll('td')).map((td) => td.textContent?.trim());

    expect(rows.length).toBe(3);
    expect(cells(rows[1])).toEqual(['P-014', '204-A', '71 años', 'dom 25', 'En revisión']);
    expect(cells(rows[0])[4]).toBe('Sin informes');
    expect(rows[1].getAttribute('data-href')).toBe('/enf/pacientes/ficha/b');
    expect(root.querySelector('.total')?.textContent).toBe('3 resultados');
  });

  it('un paciente sin cama, edad ni registros lo dice sin inventar datos', async () => {
    const root = await load(PATIENTS);
    const cells = Array.from(root.querySelectorAll('tbody tr')[2].querySelectorAll('td')).map((td) => td.textContent?.trim());

    expect(cells).toEqual(['P-045', 'Sin dato', 'Sin dato', 'Sin registros', 'Sin informes']);
  });

  it('trae el botón "Nuevo paciente" hacia el alta', async () => {
    const root = await load(PATIENTS);
    const button = root.querySelector('.cab a.btn--primary');

    expect(button?.textContent).toContain('Nuevo paciente');
    expect(button?.getAttribute('href')).toBe('/enf/pacientes/nuevo');
  });

  it('sin pacientes muestra el estado vacío del mockup', async () => {
    const root = await load([]);

    expect(root.querySelector('.vacio h3')?.textContent).toBe('Aún no hay pacientes');
  });

  it('si el servidor falla avisa con su mensaje y no pinta la tabla', async () => {
    const root = await load('error');

    expect(root.querySelector('.aviso')?.textContent).toContain('El servidor no respondió.');
    expect(root.querySelector('table')).toBeNull();
  });
});
