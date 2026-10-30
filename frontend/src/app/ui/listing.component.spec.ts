import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { CellDirective, ListingComponent } from './listing.component';

interface Row { id: string; bed: string }

@Component({
  standalone: true,
  imports: [ListingComponent, CellDirective],
  template: `
    <app-listing [cols]="['Paciente', 'Cama']" [rows]="rows" placeholder="Busque por código de paciente o cama"
                 [searchText]="search" [href]="href" [empty]="empty">
      <ng-template appCell let-row let-col="col">{{ col === 0 ? row.id : row.bed }}</ng-template>
    </app-listing>`,
})
class HostComponent {
  rows: Row[] = [{ id: 'P-014', bed: '204-A' }, { id: 'P-021', bed: '207-B' }, { id: 'P-007', bed: '215-A' }];
  search = (row: Row) => `${row.id} ${row.bed}`;
  href = (row: Row) => `/enf/pacientes/ficha/${row.id}`;
  empty = { title: 'Aún no hay pacientes', text: 'Cree el primero.', action: { text: 'Nuevo paciente', path: '/enf/pacientes/nuevo' } };
}

describe('ListingComponent (listado del mockup)', () => {
  function setup(rows?: Row[]) {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(HostComponent);
    if (rows) { fixture.componentInstance.rows = rows; }
    fixture.detectChanges();
    return fixture;
  }

  const type = (fixture: ReturnType<typeof setup>, text: string) => {
    const input: HTMLInputElement = fixture.nativeElement.querySelector('input');
    input.value = text;
    input.dispatchEvent(new Event('input'));
    fixture.detectChanges();
  };

  it('pinta la tabla con data-label en cada celda y el total en plural', () => {
    const fixture = setup();
    const root: HTMLElement = fixture.nativeElement;

    expect(root.querySelector('.total')?.textContent).toBe('3 resultados');
    expect(root.querySelectorAll('tbody tr').length).toBe(3);
    expect(root.querySelector('td')?.getAttribute('data-label')).toBe('Paciente');
    expect(root.querySelector('th')?.getAttribute('scope')).toBe('col');
  });

  it('filtra por el texto de búsqueda y dice "1 resultado" en singular', () => {
    const fixture = setup();
    type(fixture, '207');

    expect(fixture.nativeElement.querySelector('.total').textContent).toBe('1 resultado');
    expect(fixture.nativeElement.querySelectorAll('tbody tr').length).toBe(1);
  });

  it('sin coincidencias muestra "Sin resultados"', () => {
    const fixture = setup();
    type(fixture, 'zzz');

    expect(fixture.nativeElement.querySelector('.vacio h3').textContent).toBe('Sin resultados');
    expect(fixture.nativeElement.querySelector('.total').textContent).toBe('0 resultados');
  });

  it('sin filas muestra el estado vacío con su acción', () => {
    const fixture = setup([]);
    const root: HTMLElement = fixture.nativeElement;

    expect(root.querySelector('.vacio h3')?.textContent).toBe('Aún no hay pacientes');
    expect(root.querySelector('.vacio a.btn--primary')?.textContent).toBe('Nuevo paciente');
  });

  it('al hacer clic en una fila va a su ruta', () => {
    const fixture = setup();
    const navigate = spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);
    (fixture.nativeElement.querySelectorAll('tbody tr')[1] as HTMLElement).click();

    expect(navigate).toHaveBeenCalledWith('/enf/pacientes/ficha/P-021');
  });
});
