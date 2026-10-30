import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';

import { SessionService } from '../core/session.service';
import { EntryNewComponent } from './entry-new.component';

const PATIENTS = [
  { id: 'p1', alias: 'P-014', bed: '204-A' },
  { id: 'p2', alias: 'P-021', bed: '207-B' },
  { id: 'p3', alias: 'P-045' },
];

const PROPOSAL = {
  events: [
    { symptom: 'DIARREA', intensity: 'INTENSO', occurrences: 2 },
    { symptom: 'APETITO_BAJO', intensity: 'SIN_INDICAR' },
  ],
  provider: 'groq', model: 'openai/gpt-oss-120b', fallbackUsed: false, runId: 'r1',
};

describe('EntryNewComponent (vRegNuevo del mockup)', () => {
  let http: HttpTestingController;
  let root: HTMLElement;
  let settle: () => Promise<void>;

  async function open(routeId: string | null = null, llm: object = { available: true, primary: { configured: true } }) {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap(routeId ? { id: routeId } : {}) } } },
      ],
    });
    TestBed.inject(SessionService).today.set('2026-10-28');
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(EntryNewComponent);
    fixture.detectChanges();
    http.expectOne('/api/llm/status').flush(llm);
    http.expectOne('/api/patients').flush(PATIENTS);
    root = fixture.nativeElement;
    settle = async () => { await new Promise((resolve) => setTimeout(resolve)); fixture.detectChanges(); };
    await settle();
  }

  afterEach(() => http.verify());

  const type = (selector: string, value: string, event = 'input') => {
    const el = root.querySelector<HTMLInputElement | HTMLSelectElement>(selector)!;
    el.value = value;
    el.dispatchEvent(new Event(event));
  };
  const submit = () => root.querySelector('form.entrada')!.dispatchEvent(new Event('submit'));
  const click = (selector: string) => root.querySelector<HTMLElement>(selector)!.click();
  const rows = () => Array.from(root.querySelectorAll('.fila-sint'));

  async function send(text: string, answer: object | 'error' = PROPOSAL): Promise<TestRequest> {
    type('#rn-txt', text);
    await settle();
    submit();
    await settle();
    const request = http.expectOne('/api/patients/p1/entries/propose');
    if (answer === 'error') {
      request.flush('<html>Internal Server Error</html>', { status: 500, statusText: 'Error' });
    } else {
      request.flush(answer);
    }
    await settle();
    return request;
  }

  async function save(selector = '[data-a="guardar"]', answer: object = { id: 'e1' }): Promise<TestRequest> {
    click(selector);
    await settle();
    const request = http.expectOne('/api/patients/p1/entries');
    request.flush(answer, { status: 201, statusText: 'Created' });
    await settle();
    return request;
  }

  it('ofrece los pacientes con su cama, la fecha de hoy como valor y máximo, y la ayuda del mockup', async () => {
    await open();

    expect(Array.from(root.querySelectorAll('#rn-pac option')).map((o) => o.textContent)).toEqual(
      ['P-014 (cama 204-A)', 'P-021 (cama 207-B)', 'P-045']);
    expect(root.querySelector<HTMLInputElement>('#rn-fecha')!.value).toBe('2026-10-28');
    expect(root.querySelector('#rn-fecha')!.getAttribute('max')).toBe('2026-10-28');
    expect(root.querySelector('.ayuda')?.textContent).toContain('Escriba cómo estuvo el paciente hoy, como en un chat.');
    expect(root.querySelector('.ayuda')?.textContent).not.toContain('corrige la redacción');
    expect(root.querySelectorAll('#catalogo option').length).toBe(9);
    expect(root.querySelector('.aviso')).toBeNull();
  });

  it('abre con el paciente de la ruta', async () => {
    await open('p2');

    expect(root.querySelector<HTMLSelectElement>('#rn-pac')!.value).toBe('p2');
  });

  it('avisa arriba si falta la llave de la IA', async () => {
    await open(null, { available: false, primary: { configured: false }, warning: 'La IA no está configurada: falta la llave del proveedor.' });

    expect(root.querySelector('.aviso')?.textContent).toContain('La IA no está configurada');
  });

  it('un texto vacío no envía nada', async () => {
    await open();
    type('#rn-txt', '   ');
    submit();
    await settle();

    http.expectNone('/api/patients/p1/entries/propose');
  });

  it('una fecha futura no envía nada y lo dice junto al campo', async () => {
    await open();
    type('#rn-fecha', '2026-10-30');
    type('#rn-txt', 'Tranquilo.');
    await settle();
    submit();
    await settle();

    expect(root.querySelector('#rn-err')?.textContent).toBe('La fecha del registro no puede ser futura.');
    http.expectNone('/api/patients/p1/entries/propose');
  });

  it('envía el texto tal como se escribió, lo muestra en la burbuja y pinta la propuesta', async () => {
    await open();
    const request = await send('  Tuvo diarrea intensa dos veces. Comió poco.  ');

    expect(request.request.body).toEqual({ body: '  Tuvo diarrea intensa dos veces. Comió poco.  ' });
    expect(root.querySelector('.burbuja')?.textContent).toBe('  Tuvo diarrea intensa dos veces. Comió poco.  ');
    expect(root.querySelector<HTMLInputElement>('#rn-txt')!.value).toBe(''); // la caja queda lista para el siguiente texto
    expect(root.querySelector('.ayuda')).toBeNull();
    expect(root.querySelector('.vista-ia h3')?.textContent).toBe('Así quedaría el registro');
    expect(root.querySelector('.vista-ia textarea')).toBeNull(); // sin «texto corregido»
    expect(rows().length).toBe(2);
    expect(rows()[0].querySelector<HTMLInputElement>('[data-s="n"]')!.value).toBe('Diarrea');
    expect(rows()[0].querySelector<HTMLSelectElement>('[data-s="intensidad"]')!.value).toBe('INTENSO');
    expect(rows()[0].querySelector<HTMLInputElement>('[data-s="veces"]')!.value).toBe('2');
    expect(rows()[1].querySelector<HTMLInputElement>('[data-s="n"]')!.value).toBe('Apetito bajo');
    expect(rows()[1].querySelector<HTMLInputElement>('[data-s="veces"]')!.value).toBe('');
    expect(Array.from(rows()[0].querySelectorAll('select option')).map((o) => o.textContent))
      .toEqual(['Sin indicar', 'leve', 'moderado', 'intenso']);
  });

  it('«Aprobar y guardar» una propuesta sin cambios guarda el texto exacto y los eventos con origen IA', async () => {
    await open();
    await send('Tuvo diarrea intensa dos veces. Comió poco.');
    const request = await save();

    expect(request.request.body).toEqual({
      entryDate: '2026-10-28',
      body: 'Tuvo diarrea intensa dos veces. Comió poco.',
      events: [
        { symptom: 'DIARREA', intensity: 'INTENSO', occurrences: 2, origin: 'IA' },
        { symptom: 'APETITO_BAJO', intensity: 'SIN_INDICAR', occurrences: null, origin: 'IA' },
      ],
    });
    expect(root.querySelector('.vista-ia')).toBeNull();
    expect(root.querySelector('.aviso')?.textContent).toContain('Registro guardado.');
    expect(root.querySelector('.aviso a')?.getAttribute('href')).toBe('/enf/pacientes/ficha/p1');
  });

  it('lo que la persona cambia sale como EDITADO, lo que agrega como MANUAL y lo que quita no se guarda', async () => {
    await open();
    await send('Tuvo diarrea. Comió poco. Mareo.');
    type('.fila-sint:nth-child(1) [data-s="intensidad"]', 'LEVE', 'change');
    click('.fila-sint:nth-child(2) [data-a="quitar"]');
    await settle();
    click('[data-a="agregar"]');
    await settle();
    type('.fila-sint:nth-child(2) [data-s="n"]', 'mareo');
    type('.fila-sint:nth-child(2) [data-s="veces"]', '1');
    click('[data-a="agregar"]'); // una fila vacía se ignora
    await settle();
    const request = await save();

    expect(request.request.body.events).toEqual([
      { symptom: 'DIARREA', intensity: 'LEVE', occurrences: 2, origin: 'EDITADO' },
      { symptom: 'MAREO', intensity: 'SIN_INDICAR', occurrences: 1, origin: 'MANUAL' },
    ]);
  });

  it('un síntoma fuera del catálogo, escrito a mano, se envía con su nombre y el servidor calcula el código', async () => {
    await open();
    await send('Tuvo diarrea intensa dos veces. Comió poco. Se queja de picazón en los brazos.');
    click('.fila-sint:nth-child(2) [data-a="quitar"]');
    await settle();
    click('[data-a="agregar"]');
    await settle();
    type('.fila-sint:nth-child(2) [data-s="n"]', '  picazón   en los brazos ');
    type('.fila-sint:nth-child(2) [data-s="intensidad"]', 'LEVE', 'change');
    const request = await save();

    expect(request.request.body.events).toEqual([
      { symptom: 'DIARREA', intensity: 'INTENSO', occurrences: 2, origin: 'IA' },
      { symptom: 'PICAZON_EN_LOS_BRAZOS', label: 'picazón en los brazos', intensity: 'LEVE', occurrences: null, origin: 'MANUAL' },
    ]);
  });

  it('un síntoma que propone la IA fuera del catálogo se muestra con su nombre y se guarda como IA si no se toca', async () => {
    await open();
    await send('Le duele la garganta.', {
      events: [{ symptom: 'DOLOR_DE_GARGANTA', label: 'Dolor de garganta', intensity: 'LEVE', occurrences: null }],
      fallbackUsed: false,
    });

    expect(rows()[0].querySelector<HTMLInputElement>('[data-s="n"]')!.value).toBe('Dolor de garganta');
    const request = await save();
    expect(request.request.body.events).toEqual([
      { symptom: 'DOLOR_DE_GARGANTA', label: 'Dolor de garganta', intensity: 'LEVE', occurrences: null, origin: 'IA' },
    ]);
  });

  it('un nombre que no parece un síntoma o un síntoma repetido se rechaza antes de enviar', async () => {
    await open();
    await send('Texto');
    click('[data-a="agregar"]');
    await settle();
    type('.fila-sint:nth-child(3) [data-s="n"]', '12345');
    click('[data-a="guardar"]');
    await settle();

    expect(root.querySelector('[data-e="sint"]')?.textContent).toBe('Escriba el nombre del síntoma con letras (hasta 60 caracteres).');
    http.expectNone('/api/patients/p1/entries');

    type('.fila-sint:nth-child(3) [data-s="n"]', 'DIARREA');
    click('[data-a="guardar"]');
    await settle();

    expect(root.querySelector('[data-e="sint"]')?.textContent).toBe('Hay un síntoma repetido. Déjelo una sola vez.');
    http.expectNone('/api/patients/p1/entries');
  });

  it('«Descartar» cierra la vista previa y no guarda nada', async () => {
    await open();
    await send('Texto');
    click('[data-a="descartar"]');
    await settle();

    expect(root.querySelector('.vista-ia')).toBeNull();
    http.expectNone('/api/patients/p1/entries');
  });

  it('si la IA falla muestra el motivo y ofrece guardar solo el texto o agregar a mano, sin perder lo escrito', async () => {
    await open();
    await send('Mareo leve al levantarse.', {
      events: [], failure: 'No se pudo obtener respuesta de la IA. Puede guardar solo el texto o agregar los síntomas a mano.',
      fallbackUsed: false,
    });

    expect(root.querySelector('.burbuja')?.textContent).toBe('Mareo leve al levantarse.');
    expect(root.querySelector('.vista-ia .aviso')?.textContent).toContain('No se pudo obtener respuesta de la IA.');
    expect(root.querySelector('[data-a="guardar-texto"]')?.textContent).toContain('Guardar solo el texto');
    expect(root.querySelector('[data-a="agregar"]')?.textContent).toBe('Agregar síntoma');
    expect(root.querySelector('[data-a="guardar"]')).toBeNull(); // sin síntomas no hay nada que «aprobar»
  });

  it('«Guardar solo el texto» envía la entrada sin eventos y lo dice', async () => {
    await open();
    await send('Mareo leve al levantarse.', { events: [], failure: 'La IA no respondió.', fallbackUsed: false });
    const request = await save('[data-a="guardar-texto"]');

    expect(request.request.body).toEqual({ entryDate: '2026-10-28', body: 'Mareo leve al levantarse.' });
    expect(root.querySelector('.aviso')?.textContent).toContain('Registro guardado solo con el texto; quedó sin estructurar.');
  });

  it('tras un fallo, agregar un síntoma a mano permite «Aprobar y guardar» con origen MANUAL', async () => {
    await open();
    await send('Mareo leve al levantarse.', { events: [], failure: 'La IA no respondió.', fallbackUsed: false });
    click('[data-a="agregar"]');
    await settle();
    type('.fila-sint [data-s="n"]', 'Mareo');
    type('.fila-sint [data-s="intensidad"]', 'LEVE', 'change');
    const request = await save();

    expect(request.request.body.events).toEqual([{ symptom: 'MAREO', intensity: 'LEVE', occurrences: null, origin: 'MANUAL' }]);
  });

  it('un error del servidor al proponer no muestra texto crudo y deja guardar el texto', async () => {
    await open();
    await send('Texto', 'error');

    expect(root.querySelector('.vista-ia .aviso')?.textContent).toBe('No se pudo completar la acción. Intente de nuevo. Puede guardar solo el texto o agregar los síntomas a mano.');
    expect(root.textContent).not.toContain('Internal Server Error');
    expect(root.querySelector('[data-a="guardar-texto"]')).not.toBeNull();
  });

  it('un rechazo del servidor sobre los síntomas se muestra junto a la lista y no se pierde la vista previa', async () => {
    await open();
    await send('Texto');
    click('[data-a="guardar"]');
    await settle();
    http.expectOne('/api/patients/p1/entries').flush(
      { message: 'Revise los datos indicados.', fields: { events: 'Las veces deben estar entre 1 y 20.' } },
      { status: 400, statusText: 'Bad Request' });
    await settle();

    expect(root.querySelector('[data-e="sint"]')?.textContent).toBe('Las veces deben estar entre 1 y 20.');
    expect(root.querySelector('.vista-ia')).not.toBeNull();
  });

  it('el paciente y la fecha de la propuesta son los elegidos al enviar', async () => {
    await open();
    type('#rn-pac', 'p2', 'change');
    type('#rn-fecha', '2026-10-27');
    type('#rn-txt', 'Tranquilo.');
    await settle();
    submit();
    await settle();
    http.expectOne('/api/patients/p2/entries/propose').flush({ events: [], fallbackUsed: false });
    await settle();
    click('[data-a="guardar"]');
    await settle();
    const create = http.expectOne('/api/patients/p2/entries');

    expect(create.request.body).toEqual({ entryDate: '2026-10-27', body: 'Tranquilo.', events: [] });
    create.flush({ id: 'e2' }, { status: 201, statusText: 'Created' });
  });
});
