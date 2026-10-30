import { TestBed } from '@angular/core/testing';

import { Entry } from '../core/entries.service';
import { EntryCardComponent } from './entry-card.component';

const ENTRY: Entry = {
  id: 'e1', patientId: 'p1', patientAlias: 'P-014', entryDate: '2026-10-25', body: 'Vomitó dos veces. <b>no es html</b>',
  authorName: 'Camilo Torres', structuringStatus: 'ESTRUCTURADA', createdAt: '2026-10-25T23:00:00Z',
  symptoms: [{ symptom: 'VOMITO', intensity: 'MODERADO', occurrences: 2 }],
};

function render(entry: Entry): HTMLElement {
  const fixture = TestBed.createComponent(EntryCardComponent);
  fixture.componentRef.setInput('entry', entry);
  fixture.detectChanges();
  return fixture.nativeElement;
}

describe('EntryCardComponent (regCard del mockup)', () => {
  it('muestra la fecha larga, el texto como texto y los síntomas en lista', () => {
    const root = render(ENTRY);

    expect(root.querySelector('article.card b')?.textContent).toBe('domingo 25 de octubre');
    expect(root.querySelector('article.card p')?.textContent).toBe('Vomitó dos veces. <b>no es html</b>');
    expect(root.querySelector('article.card p b')).toBeNull();
    expect(Array.from(root.querySelectorAll('ul.sint li')).map((li) => li.textContent)).toEqual(['Vómito, moderado, 2 veces']);
  });

  it('sin síntomas dice "Sin síntomas registrados."', () => {
    expect(render({ ...ENTRY, symptoms: [] }).querySelector('p.sec')?.textContent).toBe('Sin síntomas registrados.');
  });

  it('una entrada sin estructurar lo dice, en vez de afirmar que no hubo síntomas', () => {
    const root = render({ ...ENTRY, symptoms: [], structuringStatus: 'SIN_ESTRUCTURAR' });
    expect(root.querySelector('p.sec')?.textContent).toBe('Sin estructurar: solo se guardó el texto.');
  });
});
