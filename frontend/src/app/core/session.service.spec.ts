import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { bearerInterceptor } from './api.interceptor';
import { AuthService } from './auth.service';
import { SessionService } from './session.service';

const NURSE = { id: 'n1', displayName: 'Marcela Rojas', role: 'ENFERMERIA', permissions: ['PATIENTS_READ'] };

describe('SessionService y el interceptor del token', () => {
  let service: SessionService;
  let http: HttpTestingController;
  const auth = { init: jasmine.createSpy('init'), token: jasmine.createSpy('token'), logout: jasmine.createSpy('logout') };

  beforeEach(() => {
    auth.init.calls.reset();
    auth.init.and.resolveTo();
    auth.token.and.resolveTo('tok-123');
    auth.logout.calls.reset();
    auth.logout.and.resolveTo();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([bearerInterceptor])), provideHttpClientTesting(), { provide: AuthService, useValue: auth }],
    });
    service = TestBed.inject(SessionService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  const tick = async (): Promise<void> => {
    for (let i = 0; i < 5; i++) {
      await Promise.resolve();
    }
  };

  async function start(): Promise<void> {
    void service.init();
    await tick();
    http.expectOne('/api/system/status').flush({ status: 'UP', database: 'UP', today: '2026-10-28' });
    await tick();
  }

  it('inicia sesion, toma la fecha del servidor y carga la sesion con el token en la cabecera', async () => {
    await start();
    const request = http.expectOne('/api/session');
    expect(request.request.headers.get('Authorization')).toBe('Bearer tok-123');
    request.flush(NURSE);
    await tick();

    expect(auth.init).toHaveBeenCalled();
    expect(service.today()).toBe('2026-10-28');
    expect(service.session()?.displayName).toBe('Marcela Rojas');
    expect(service.hasScreens()).toBeTrue();
  });

  it('el medico entra con pantallas y su prefijo de ruta es med; enfermeria usa enf', async () => {
    await start();
    http.expectOne('/api/session').flush({ ...NURSE, role: 'MEDICO' });
    await tick();

    expect(service.session()?.role).toBe('MEDICO');
    expect(service.hasScreens()).toBeTrue();
    expect(service.prefix()).toBe('med');
  });

  it('un administrador entra y tiene sus pantallas (gestión de usuarios, prefijo adm)', async () => {
    await start();
    http.expectOne('/api/session').flush({ ...NURSE, role: 'ADMIN' });
    await tick();

    expect(service.session()?.role).toBe('ADMIN');
    expect(service.hasScreens()).toBeTrue();
    expect(service.prefix()).toBe('adm');
  });

  it('no manda el token a direcciones que no son del API', () => {
    TestBed.inject(HttpClient).get('https://example.test/x').subscribe();

    expect(http.expectOne('https://example.test/x').request.headers.has('Authorization')).toBeFalse();
  });

  it('si no hay token, la peticion sale sin cabecera', async () => {
    auth.token.and.resolveTo(null);
    TestBed.inject(HttpClient).get('/api/patients').subscribe();
    await tick();

    expect(http.expectOne('/api/patients').request.headers.has('Authorization')).toBeFalse();
  });

  it('si el usuario no esta registrado (403), lo dice con un mensaje claro', async () => {
    await start();
    http.expectOne('/api/session').flush({ message: 'x' }, { status: 403, statusText: 'Forbidden' });
    await tick();

    expect(service.error()).toContain('no está registrado');
    expect(service.session()).toBeNull();
  });

  it('si el servidor no responde, avisa con un mensaje claro y no lanza', async () => {
    void service.init();
    await tick();
    http.expectOne('/api/system/status').error(new ProgressEvent('error'));
    await tick();

    expect(service.error()).toContain('No se pudo conectar con el servidor');
  });

  it('si el inicio de sesion no arranca (Keycloak caido), avisa y no llama al API', async () => {
    auth.init.and.rejectWith(new Error('sin keycloak'));
    await service.init();

    expect(service.error()).toContain('inicio de sesión');
    http.expectNone('/api/system/status');
  });

  it('cerrar sesion se delega en Keycloak', async () => {
    await service.logout();

    expect(auth.logout).toHaveBeenCalled();
  });
});
