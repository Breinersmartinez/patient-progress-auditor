import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { AppComponent } from './app.component';
import { routes } from './app.routes';
import { SessionService } from './core/session.service';
import { ToastService } from './core/toast.service';

describe('AppComponent (armazón del mockup)', () => {
  const logout = jasmine.createSpy('logout').and.resolveTo();
  const fakeSession = {
    session: signal<{ id: string; displayName: string; role: string; permissions: string[] } | null>({ id: 'n2', displayName: 'Marcela Rojas', role: 'ENFERMERIA', permissions: [] }),
    hasScreens: signal(true),
    error: signal<string | null>(null),
    logout,
  };

  beforeEach(async () => {
    logout.calls.reset();
    fakeSession.session.set({ id: 'n2', displayName: 'Marcela Rojas', role: 'ENFERMERIA', permissions: [] });
    fakeSession.hasScreens.set(true);
    fakeSession.error.set(null);
    await TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter(routes), { provide: SessionService, useValue: fakeSession }],
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

  it('"Cerrar sesión" cierra la sesión de Keycloak y ya no hay selector "Ver como"', async () => {
    const fixture = await open('/enf/pacientes');
    const root: HTMLElement = fixture.nativeElement;

    expect(root.querySelector('select#rol')).toBeNull();
    (root.querySelector('button#cerrar-sesion') as HTMLButtonElement).click();

    expect(logout).toHaveBeenCalled();
  });

  it('un rol sin pantallas ve el aviso, sin menú, y puede cerrar sesión', async () => {
    fakeSession.session.set({ id: 'm1', displayName: 'Dr. Andrés Salazar', role: 'MEDICO', permissions: [] });
    fakeSession.hasScreens.set(false);
    const root: HTMLElement = (await open('/enf/pacientes')).nativeElement;

    expect(root.querySelector('#sin-pantallas h3')?.textContent).toBe('Su rol aún no tiene pantallas');
    expect(root.querySelector('nav#sb')).toBeNull();
    expect(root.querySelector('h1#titulo')?.textContent).toBe('Pacientes');
    expect(root.querySelector('button#cerrar-sesion')).not.toBeNull();
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
