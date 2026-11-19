import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { AppUser } from '../core/users.service';
import { UsersListComponent } from './users-list.component';

const USERS: AppUser[] = [
  { id: 'u1', name: 'Marcela Rojas', email: 'marcela.rojas@example.test', role: 'ENFERMERIA', active: true },
  { id: 'u2', name: 'Dra. Paula Mejía', email: 'paula.mejia@example.test', role: 'MEDICO', active: false },
  { id: 'u3', name: 'Administrador Auditor', email: 'admin@example.test', role: 'ADMIN', active: true },
];

describe('UsersListComponent (vUsuLista del mockup)', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function load(answer: AppUser[] | 'error') {
    const fixture = TestBed.createComponent(UsersListComponent);
    fixture.detectChanges();
    const request = http.expectOne('/api/admin/users');
    if (answer === 'error') {
      request.flush({ message: 'El servidor no respondió.' }, { status: 500, statusText: 'Error' });
    } else {
      request.flush(answer);
    }
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('lista nombre, correo, rol y estado, y cada fila lleva a su edición', async () => {
    const root = await load(USERS);
    const rows = Array.from(root.querySelectorAll('tbody tr'));
    const cells = (row: Element) => Array.from(row.querySelectorAll('td')).map((td) => td.textContent?.trim());

    expect(rows.length).toBe(3);
    expect(cells(rows[0])).toEqual(['Marcela Rojas', 'marcela.rojas@example.test', 'Enfermería', 'Activo']);
    expect(cells(rows[1])).toEqual(['Dra. Paula Mejía', 'paula.mejia@example.test', 'Médico', 'Inactivo']);
    expect(cells(rows[2])[2]).toBe('Administración');
    expect(rows[0].getAttribute('data-href')).toBe('/adm/usuarios/editar/u1');
    expect(root.querySelector('.total')?.textContent).toBe('3 resultados');
  });

  it('el botón «Nuevo usuario» lleva al formulario', async () => {
    const root = await load(USERS);
    const button = root.querySelector('.cab a.btn--primary') as HTMLAnchorElement;
    expect(button.textContent).toContain('Nuevo usuario');
    expect(button.getAttribute('href')).toBe('/adm/usuarios/nuevo');
  });

  it('busca por nombre, correo o rol', async () => {
    const fixture = TestBed.createComponent(UsersListComponent);
    fixture.detectChanges();
    http.expectOne('/api/admin/users').flush(USERS);
    await fixture.whenStable();
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    const search = root.querySelector('input[type=search]') as HTMLInputElement;

    for (const [text, expected] of [['rojas', 1], ['example.test', 3], ['médico', 1], ['Administración', 1]] as const) {
      search.value = text;
      search.dispatchEvent(new Event('input'));
      fixture.detectChanges();
      expect(root.querySelectorAll('tbody tr').length).withContext(text).toBe(expected);
    }
    search.value = 'xyz';
    search.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(root.querySelector('.vacio h3')?.textContent).toBe('Sin resultados');
  });

  it('sin usuarios, invita a crear el primero', async () => {
    const root = await load([]);
    expect(root.querySelector('.vacio h3')?.textContent).toBe('Aún no hay usuarios');
    expect(root.querySelector('.vacio a')?.getAttribute('href')).toBe('/adm/usuarios/nuevo');
  });

  it('si el servidor falla, lo dice sin mostrar el error crudo', async () => {
    const root = await load('error');
    expect(root.querySelector('.aviso')?.textContent).toContain('El servidor no respondió.');
    expect(root.querySelector('table')).toBeNull();
  });
});
