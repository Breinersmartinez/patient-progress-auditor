import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { AppComponent } from './app.component';
import { routes } from './app.routes';
import { SessionService } from './core/session.service';
import { ToastService } from './core/toast.service';

describe('AppComponent (armazón del mockup)', () => {
  const select = jasmine.createSpy('select').and.resolveTo();
  const fakeSession = {
    selectable: signal([
      { id: 'n1', displayName: 'Camilo Torres', role: 'ENFERMERIA' },
      { id: 'n2', displayName: 'Marcela Rojas', role: 'ENFERMERIA' },
    ]),
    currentId: signal<string | null>('n2'),
    session: signal({ id: 'n2', displayName: 'Marcela Rojas', role: 'ENFERMERIA', permissions: [] }),
    error: signal<string | null>(null),
    select,
  };

  beforeEach(async () => {
    select.calls.reset();
    fakeSession.error.set(null);
    await TestBed.configureTestingModule({
      providers: [provideRouter(routes), { provide: SessionService, useValue: fakeSession }],
    }).compileComponents();
  });

  async function open(url: string) {
    const fixture = TestBed.createComponent(AppComponent);
    await TestBed.inject(Router).navigateByUrl(url);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  it('muestra el menú, el título de la ruta y el usuario elegido', async () => {
    const root: HTMLElement = (await open('/enf/registro/historial')).nativeElement;

    expect(root.querySelector('nav#sb.sb')).not.toBeNull();
    expect(root.querySelector('h1#titulo')?.textContent).toBe('Historial de registros');
    expect(root.querySelector('.sb-user__btn b')?.textContent).toBe('Marcela Rojas');
  });

  it('la ruta por defecto es Pacientes', async () => {
    const root: HTMLElement = (await open('/')).nativeElement;

    expect(root.querySelector('h1#titulo')?.textContent).toBe('Pacientes');
  });

  it('una ruta desconocida dice "No encontramos lo que busca"', async () => {
    const root: HTMLElement = (await open('/nada/que/ver')).nativeElement;

    expect(root.querySelector('.vacio h3')?.textContent).toBe('No encontramos lo que busca');
    expect(root.querySelector('h1#titulo')?.textContent).toBe('No encontrado');
  });

  it('"Ver como" lista solo las enfermeras, deja marcada la elegida y cambia de usuario', async () => {
    const fixture = await open('/enf/pacientes');
    const selectElement: HTMLSelectElement = fixture.nativeElement.querySelector('select#rol');

    expect(Array.from(selectElement.options).map((option) => option.textContent)).toEqual(['Camilo Torres', 'Marcela Rojas']);
    expect(selectElement.value).toBe('n2');

    selectElement.value = 'n1';
    selectElement.dispatchEvent(new Event('change'));
    await fixture.whenStable();

    expect(select).toHaveBeenCalledWith('n1');
  });

  it('si no hay conexión con el servidor, lo dice con un aviso', async () => {
    fakeSession.error.set('No se pudo conectar con el servidor.');
    const root: HTMLElement = (await open('/enf/pacientes')).nativeElement;

    expect(root.querySelector('.aviso')?.textContent).toContain('No se pudo conectar con el servidor.');
  });

  it('los avisos (toast) salen en la zona con aria-live y se quitan solos', async () => {
    jasmine.clock().install();
    try {
      const fixture = await open('/enf/pacientes');
      TestBed.inject(ToastService).show('Registro guardado.');
      fixture.detectChanges();

      const toasts: HTMLElement = fixture.nativeElement.querySelector('#toasts');
      expect(toasts.getAttribute('aria-live')).toBe('polite');
      expect(toasts.querySelector('.toast--ok span')?.textContent).toBe('Registro guardado.');

      jasmine.clock().tick(3900);
      fixture.detectChanges();
      expect(toasts.querySelector('.toast')).toBeNull();
    } finally {
      jasmine.clock().uninstall();
    }
  });
});
