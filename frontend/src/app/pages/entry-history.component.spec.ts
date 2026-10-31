import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { Entry } from '../core/entries.service';
import { EntryHistoryComponent } from './entry-history.component';

const entry = (over: Partial<Entry>): Entry => ({
  id: 'e', patientId: 'p1', patientAlias: 'P-014', entryDate: '2026-10-25', body: 'Tranquilo.', authorName: 'Camilo Torres',
  structuringStatus: 'ESTRUCTURADA', createdAt: '2026-10-25T23:00:00Z', symptoms: [], ...over,
});

describe('EntryHistoryComponent (vRegHistorial del mockup)', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function load(entries: Entry[]) {
    const fixture = TestBed.createComponent(EntryHistoryComponent);
    fixture.detectChanges();
    http.expectOne('/api/entries').flush(entries);
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  const cells = (row: Element) => Array.from(row.querySelectorAll('td')).map((td) => td.textContent?.trim());

  it('muestra fecha, paciente, texto y nombres de síntomas, en el orden que trae el servidor', async () => {
    const fixture = await load([
      entry({ id: 'a', body: 'Vomitó.', symptoms: [{ symptom: 'VOMITO', intensity: 'LEVE' }, { symptom: 'NAUSEAS', intensity: 'SIN_INDICAR' }] }),
      entry({ id: 'b', patientAlias: 'P-021', patientId: 'p2', entryDate: '2026-10-24' }),
    ]);
    const rows = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('tbody tr'));

    expect(cells(rows[0])).toEqual(['dom 25', 'P-014', 'Vomitó.', 'Vómito, Náuseas']);
    expect(cells(rows[1])).toEqual(['sáb 24', 'P-021', 'Tranquilo.', 'Ninguno']);
    expect(rows[1].getAttribute('data-href')).toBe('/enf/pacientes/ficha/p2');
  });

  it('una entrada sin estructurar no dice "Ninguno"', async () => {
    const fixture = await load([entry({ structuringStatus: 'SIN_ESTRUCTURAR' })]);

    expect(cells(fixture.nativeElement.querySelector('tbody tr'))[3]).toBe('Sin estructurar');
  });

  it('busca por síntoma y por texto', async () => {
    const fixture = await load([
      entry({ id: 'a', body: 'Vomitó.', symptoms: [{ symptom: 'VOMITO', intensity: 'LEVE' }] }),
      entry({ id: 'b', body: 'Sin novedades.' }),
    ]);
    const input: HTMLInputElement = fixture.nativeElement.querySelector('input[type=search]');
    input.value = 'vómito';
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelectorAll('tbody tr').length).toBe(1);
  });

  it('sin registros muestra el estado vacío con la acción "Nuevo registro"', async () => {
    const fixture = await load([]);

    expect(fixture.nativeElement.querySelector('.vacio h3').textContent).toBe('Aún no hay registros');
    expect(fixture.nativeElement.querySelector('.vacio a').getAttribute('href')).toBe('/enf/registro/nuevo');
  });

  it('muestra el texto corregido y deja el original en el título; la búsqueda encuentra los dos', async () => {
    const fixture = await load([entry({ id: 'a', body: 'vomito 2 veces', correctedBody: 'Vomitó 2 veces.' })]);
    const root = fixture.nativeElement as HTMLElement;
    const cell = root.querySelectorAll('tbody tr')[0].querySelectorAll('td')[2];

    expect(cell.textContent?.trim()).toBe('Vomitó 2 veces.');
    expect(cell.querySelector('span')?.getAttribute('title')).toBe('Texto original: vomito 2 veces');
    const search = root.querySelector<HTMLInputElement>('input[type="search"], input.inp')!;
    search.value = 'vomito';
    search.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(root.querySelectorAll('tbody tr').length).toBe(1);
  });
});
