import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { demoUserInterceptor } from './api.interceptor';
import { SessionService } from './session.service';

const USERS = [
  { id: 'n1', displayName: 'Camilo Torres', role: 'ENFERMERIA' },
  { id: 'n2', displayName: 'Marcela Rojas', role: 'ENFERMERIA' },
  { id: 'm1', displayName: 'Dr. Andrés Salazar', role: 'MEDICO' },
];

describe('SessionService y el interceptor del usuario elegido', () => {
  let service: SessionService;
  let http: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(withInterceptors([demoUserInterceptor])), provideHttpClientTesting()],
    });
    service = TestBed.inject(SessionService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function start(): Promise<void> {
    void service.init();
    http.expectOne('/api/system/status').flush({ status: 'UP', database: 'UP', today: '2026-10-28' });
    await Promise.resolve();
    http.expectOne('/api/demo-users').flush(USERS);
    await Promise.resolve();
    await Promise.resolve();
  }

  it('toma la fecha del servidor y ofrece solo enfermeras', async () => {
    await start();
    const request = http.expectOne('/api/session');
    expect(request.request.headers.get('X-Demo-User')).toBe('n1');
    request.flush({ ...USERS[0], permissions: ['PATIENTS_READ'] });

    expect(service.today()).toBe('2026-10-28');
    expect(service.selectable().map((u) => u.displayName)).toEqual(['Camilo Torres', 'Marcela Rojas']);
    expect(service.demoUsers().length).toBe(3);
  });

  it('recuerda al usuario elegido y lo vuelve a usar al abrir', async () => {
    localStorage.setItem('ppa.demoUser', 'n2');
    await start();
    const request = http.expectOne('/api/session');
    expect(request.request.headers.get('X-Demo-User')).toBe('n2');
    request.flush({ ...USERS[1], permissions: [] });
    await Promise.resolve();

    expect(service.session()?.displayName).toBe('Marcela Rojas');
    expect(localStorage.getItem('ppa.demoUser')).toBe('n2');
  });

  it('cambiar de usuario manda el nuevo id en las peticiones siguientes', async () => {
    await start();
    http.expectOne('/api/session').flush({ ...USERS[0], permissions: [] });
    await Promise.resolve();

    const change = service.select('n2');
    const request = http.expectOne('/api/session');
    expect(request.request.headers.get('X-Demo-User')).toBe('n2');
    request.flush({ ...USERS[1], permissions: [] });
    await change;

    TestBed.inject(HttpClient).get('/api/patients').subscribe();
    expect(http.expectOne('/api/patients').request.headers.get('X-Demo-User')).toBe('n2');
  });

  it('no manda el usuario a direcciones que no son del API', () => {
    service.currentId.set('n1');
    TestBed.inject(HttpClient).get('https://example.test/x').subscribe();

    expect(http.expectOne('https://example.test/x').request.headers.has('X-Demo-User')).toBeFalse();
  });

  it('si el servidor no responde, avisa con un mensaje claro y no lanza', async () => {
    const done = service.init();
    http.expectOne('/api/system/status').error(new ProgressEvent('error'));
    await done;

    expect(service.error()).toContain('No se pudo conectar con el servidor');
    expect(service.session()).toBeNull();
  });
});
