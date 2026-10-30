import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { SessionService } from '../core/session.service';
import { ToastService } from '../core/toast.service';
import { PatientFormComponent } from './patient-form.component';

describe('PatientFormComponent (alta de paciente, patrón vUsuForm)', () => {
  let http: HttpTestingController;
  let root: HTMLElement;
  let detect: () => void;
  let settle: () => Promise<void>;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    TestBed.inject(SessionService).today.set('2026-10-28');
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(PatientFormComponent);
    fixture.detectChanges();
    root = fixture.nativeElement;
    detect = () => fixture.detectChanges();
    // Sin whenStable: esperaria el temporizador del toast y la prueba lo veria ya quitado.
    settle = async () => { await new Promise((resolve) => setTimeout(resolve)); fixture.detectChanges(); };
  });

  afterEach(() => http.verify());

  const type = (id: string, value: string) => {
    const input = root.querySelector<HTMLInputElement>('#' + id)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  };
  const submit = () => { root.querySelector('form')!.dispatchEvent(new Event('submit')); detect(); };
  const error = (name: string) => root.querySelector(`[data-e="${name}"]`)?.textContent?.trim();

  it('trae los cuatro campos y el botón "Crear paciente" con el icono guardar', () => {
    expect(Array.from(root.querySelectorAll('label')).map((l) => l.textContent)).toEqual(
      ['Código del paciente', 'Cama', 'Edad', 'Fecha de ingreso']);
    expect(root.querySelector('#pf-admitted')?.getAttribute('max')).toBe('2026-10-28');
    expect(root.querySelector('button.btn--primary')?.textContent).toContain('Crear paciente');
  });

  it('sin código no envía nada y lo dice junto al campo', () => {
    submit();

    expect(error('alias')).toBe('Escriba el código del paciente.');
    http.expectNone('/api/patients');
  });

  it('rechaza una edad fuera de rango y un ingreso futuro antes de enviar', () => {
    type('pf-alias', 'P-045');
    type('pf-age', '150');
    type('pf-admitted', '2026-10-30');
    submit();

    expect(error('age')).toBe('La edad debe estar entre 0 y 120 años.');
    expect(error('admittedOn')).toBe('La fecha de ingreso no puede ser futura.');
    http.expectNone('/api/patients');
  });

  it('envía los datos, avisa "Paciente creado" y vuelve a la lista', async () => {
    const navigate = vi_spy(TestBed.inject(Router));
    type('pf-alias', ' P-045 ');
    type('pf-bed', '204-A');
    type('pf-age', '64');
    type('pf-admitted', '2026-10-20');
    submit();

    const request = http.expectOne('/api/patients');
    expect(request.request.body).toEqual({ alias: ' P-045 ', bed: '204-A', age: 64, admittedOn: '2026-10-20' });
    request.flush({ id: 'n1', alias: 'P-045' }, { status: 201, statusText: 'Created' });
    await settle();

    expect(TestBed.inject(ToastService).toasts()[0].text).toBe('Paciente creado');
    expect(navigate).toHaveBeenCalledWith('/enf/pacientes');
  });

  it('manda la edad y el ingreso vacíos como nulos', () => {
    type('pf-alias', 'P-046');
    submit();

    expect(http.expectOne('/api/patients').request.body).toEqual({ alias: 'P-046', bed: '', age: null, admittedOn: null });
  });

  it('un código repetido se muestra junto al campo y no se navega', async () => {
    const navigate = vi_spy(TestBed.inject(Router));
    type('pf-alias', 'P-014');
    submit();
    http.expectOne('/api/patients').flush(
      { message: 'Ya existe un paciente con ese código.', fields: { alias: 'Ya existe un paciente con ese código.' } },
      { status: 409, statusText: 'Conflict' });
    await settle();

    expect(error('alias')).toBe('Ya existe un paciente con ese código.');
    expect(navigate).not.toHaveBeenCalled();
    expect(TestBed.inject(ToastService).toasts().length).toBe(0);
  });

  it('un fallo sin campo se muestra como aviso, sin texto crudo del error', async () => {
    type('pf-alias', 'P-047');
    submit();
    http.expectOne('/api/patients').flush('<html>Internal Server Error</html>', { status: 500, statusText: 'Error' });
    await settle();

    expect(root.querySelector('.aviso')?.textContent).toBe('No se pudo completar la acción. Intente de nuevo.');
  });
});

// Reemplaza la navegación por un espía: la prueba verifica a dónde se va, no la ruta destino.
function vi_spy(router: Router): jasmine.Spy {
  return spyOn(router, 'navigateByUrl').and.resolveTo(true);
}
