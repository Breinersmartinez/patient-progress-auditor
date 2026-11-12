import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { FakeEventSource } from '../core/runs.service.spec';
import { EVENT_SOURCE_FACTORY } from '../core/runs.service';
import { SessionService } from '../core/session.service';
import { ConsultationFormComponent } from './consultation-form.component';

const PATIENTS = [{ id: 'p1', alias: 'P-014', bed: '204-A' }, { id: 'p2', alias: 'P-021' }];

describe('ConsultationFormComponent («Armar informe» del mockup, D31, RF-46)', () => {
  let http: HttpTestingController;
  let root: HTMLElement;
  let settle: () => Promise<void>;
  let sources: FakeEventSource[];
  let navigate: jasmine.Spy;

  async function open() {
    sources = [];
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        { provide: EVENT_SOURCE_FACTORY, useValue: (url: string) => sources[sources.push(new FakeEventSource(url)) - 1] },
      ],
    });
    TestBed.inject(SessionService).currentId.set('m1');
    navigate = spyOn(TestBed.inject(Router), 'navigate').and.resolveTo(true);
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(ConsultationFormComponent);
    fixture.detectChanges();
    http.expectOne('/api/patients').flush(PATIENTS);
    root = fixture.nativeElement;
    settle = async () => { await new Promise((resolve) => setTimeout(resolve)); fixture.detectChanges(); };
    await settle();
  }

  afterEach(() => http.verify());

  const select = (id: string) => root.querySelector<HTMLSelectElement>(id)!;
  const input = (id: string) => root.querySelector<HTMLInputElement>(id)!;
  const button = () => root.querySelector<HTMLButtonElement>('#ig-btn')!;
  const stepTexts = () => Array.from(root.querySelectorAll('.pasos .est')).map((e) => e.textContent!.trim());
  const setInput = async (id: string, value: string) => {
    input(id).value = value;
    input(id).dispatchEvent(new Event('input'));
    await settle();
  };

  it('muestra el paciente y el período de una semana por defecto, con el aviso de no guardarse en el historial', async () => {
    await open();

    expect(Array.from(select('#ig-pac').options).map((o) => o.textContent!.trim())).toEqual(['P-014 (cama 204-A)', 'P-021']);
    expect(Array.from(select('#ig-tipo').options).map((o) => o.textContent!.trim())).toEqual(['Una semana', 'Un mes', 'Rango de fechas']);
    expect(root.querySelector('#ig-semana')).not.toBeNull();
    expect(root.querySelector('#ig-rango')).toBeNull();
    expect(root.textContent).toContain('no pasa por la revisión de enfermería ni se guarda en el historial');
    expect(button().textContent).toContain('Generar con IA');
  });

  it('rotula el período semanal como el mockup («Semana») y muestra el rango del lunes elegido', async () => {
    await open();

    expect(root.querySelector('label[for="ig-sem"]')?.textContent?.trim()).toBe('Semana');
    await setInput('#ig-sem', '2026-10-19');
    expect(root.querySelector('#ig-semana .sec')?.textContent).toContain('Del 19 al 25 de octubre de 2026');
  });

  it('cambia a rango de fechas y pide la consulta con el período elegido, sin comparar', async () => {
    await open();
    select('#ig-tipo').value = 'rango';
    select('#ig-tipo').dispatchEvent(new Event('change'));
    await settle();
    expect(root.querySelector('#ig-semana')).toBeNull();
    expect(root.querySelector('#ig-rango')).not.toBeNull();

    await setInput('#ig-d', '2026-10-01');
    await setInput('#ig-h', '2026-10-15');
    button().click();
    await settle();

    const request = http.expectOne('/api/consultations');
    expect(request.request.body).toEqual({ patientId: 'p1', periodStart: '2026-10-01', periodEnd: '2026-10-15' });
    request.flush({ id: 'r1', runType: 'CONSULTA', status: 'EN_COLA' }, { status: 202, statusText: 'Accepted' });
    await settle();

    expect(sources[0].url).toBe('/api/runs/r1/events?demoUser=m1');
    sources[0].emit('step', { id: 1, seq: 1, type: 'ESTADO', message: 'Consultando al modelo (llamada 1 de 5)…', error: false });
    await settle();
    expect(stepTexts()).toEqual(['Consultando al modelo (llamada 1 de 5)…']);

    sources[0].emit('end', { id: 'r1', status: 'COMPLETADA', consultationId: 'cons1' });
    await settle();
    expect(navigate).toHaveBeenCalledWith(['/med/informes/consulta', 'cons1']);
  });

  it('«Comparar con» el período anterior calcula las mismas fechas que un período de igual duración antes del elegido', async () => {
    await open();
    select('#ig-tipo').value = 'rango';
    select('#ig-tipo').dispatchEvent(new Event('change'));
    await settle();
    await setInput('#ig-d', '2026-10-12');
    await setInput('#ig-h', '2026-10-25');
    select('#ig-cmp').value = 'ant';
    select('#ig-cmp').dispatchEvent(new Event('change'));
    await settle();

    button().click();
    await settle();
    const request = http.expectOne('/api/consultations');
    expect(request.request.body).toEqual({
      patientId: 'p1', periodStart: '2026-10-12', periodEnd: '2026-10-25', comparisonStart: '2026-09-28', comparisonEnd: '2026-10-11',
    });
  });

  it('«Comparar con» otro período deja elegir sus dos fechas', async () => {
    await open();
    select('#ig-tipo').value = 'rango';
    select('#ig-tipo').dispatchEvent(new Event('change'));
    await settle();
    await setInput('#ig-d', '2026-10-19');
    await setInput('#ig-h', '2026-10-25');
    select('#ig-cmp').value = 'otro';
    select('#ig-cmp').dispatchEvent(new Event('change'));
    await settle();
    expect(root.querySelector('#ig-cmp-rango')).not.toBeNull();
    await setInput('#ig-cd', '2026-09-01');
    await setInput('#ig-ch', '2026-09-07');

    button().click();
    await settle();
    const request = http.expectOne('/api/consultations');
    expect(request.request.body).toEqual({
      patientId: 'p1', periodStart: '2026-10-19', periodEnd: '2026-10-25', comparisonStart: '2026-09-01', comparisonEnd: '2026-09-07',
    });
  });

  it('un rango con la fecha final antes de la inicial se rechaza sin pedir nada al servidor', async () => {
    await open();
    select('#ig-tipo').value = 'rango';
    select('#ig-tipo').dispatchEvent(new Event('change'));
    await settle();
    await setInput('#ig-d', '2026-10-15');
    await setInput('#ig-h', '2026-10-01');

    button().click();
    await settle();
    expect(root.querySelector('#ig-err')!.textContent).toContain('no puede ser posterior a la final');
    http.expectNone('/api/consultations');
  });

  it('si el servidor rechaza el período (más de 31 días, 400), el mensaje va junto al botón', async () => {
    await open();
    select('#ig-tipo').value = 'rango';
    select('#ig-tipo').dispatchEvent(new Event('change'));
    await settle();
    await setInput('#ig-d', '2026-01-01');
    await setInput('#ig-h', '2026-02-15');

    button().click();
    await settle();
    http.expectOne('/api/consultations').flush({ message: 'El período no puede pasar de 31 días.', fields: { periodEnd: 'El período no puede pasar de 31 días.' } },
      { status: 400, statusText: 'Bad Request' });
    await settle();

    expect(root.querySelector('#ig-err')!.textContent).toBe('El período no puede pasar de 31 días.');
    expect(sources.length).toBe(0);
  });

  it('un período sin registros (409) se explica sin pedir la ejecución otra vez', async () => {
    await open();
    button().click();
    await settle();
    http.expectOne('/api/consultations').flush({ message: 'El período del 2026-01-01 al 2026-01-07 no tiene registros: no hay nada que resumir.' },
      { status: 409, statusText: 'Conflict' });
    await settle();

    expect(root.querySelector('#ig-err')!.textContent).toContain('no tiene registros');
  });

  it('una ejecución fallida se explica con un aviso claro y no navega a ninguna consulta', async () => {
    await open();
    button().click();
    await settle();
    http.expectOne('/api/consultations').flush({ id: 'r1', status: 'EN_COLA' }, { status: 202, statusText: 'Accepted' });
    await settle();

    sources[0].emit('end', { id: 'r1', status: 'FALLIDA', message: 'El servicio de IA no responde en este momento. Intente de nuevo en unos minutos.' });
    await settle();
    expect(root.querySelector('.aviso')!.textContent).toContain('El servicio de IA no responde en este momento');
    expect(navigate).not.toHaveBeenCalled();
  });

  it('un mes elegido pide el primer y el último día del mes', async () => {
    await open();
    select('#ig-tipo').value = 'mes';
    select('#ig-tipo').dispatchEvent(new Event('change'));
    await settle();
    expect(root.querySelector('#ig-mes')).not.toBeNull();
    await setInput('#ig-m', '2026-09');

    button().click();
    await settle();
    const request = http.expectOne('/api/consultations');
    expect(request.request.body).toEqual({ patientId: 'p1', periodStart: '2026-09-01', periodEnd: '2026-09-30' });
  });
});
