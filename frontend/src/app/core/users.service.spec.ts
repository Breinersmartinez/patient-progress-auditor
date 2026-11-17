import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { UsersService } from './users.service';

describe('UsersService (RF-33)', () => {
  let service: UsersService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(UsersService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lista los usuarios', async () => {
    const promise = service.list();
    const request = http.expectOne('/api/admin/users');
    expect(request.request.method).toBe('GET');
    request.flush([{ id: 'u1', name: 'Ana', email: 'ana@example.test', role: 'MEDICO', active: true }]);
    expect((await promise)[0].name).toBe('Ana');
  });

  it('crea con nombre, correo y rol, y trae si la invitación salió', async () => {
    const promise = service.create({ name: 'Ana', email: 'ana@example.test', role: 'MEDICO', active: true });
    const request = http.expectOne('/api/admin/users');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ name: 'Ana', email: 'ana@example.test', role: 'MEDICO', active: true });
    request.flush({ user: { id: 'u1', name: 'Ana', email: 'ana@example.test', role: 'MEDICO', active: true }, invitationSent: false });
    expect((await promise).invitationSent).toBeFalse();
  });

  it('edita nombre, rol y estado, sin correo', async () => {
    const promise = service.update('u1', { name: 'Ana P.', role: 'ENFERMERIA', active: false });
    const request = http.expectOne('/api/admin/users/u1');
    expect(request.request.method).toBe('PUT');
    expect(Object.keys(request.request.body as object).sort()).toEqual(['active', 'name', 'role']);
    request.flush({ id: 'u1', name: 'Ana P.', email: 'ana@example.test', role: 'ENFERMERIA', active: false });
    expect((await promise).active).toBeFalse();
  });

  it('reenvía la invitación', async () => {
    const promise = service.resendInvitation('u1');
    const request = http.expectOne('/api/admin/users/u1/invitation');
    expect(request.request.method).toBe('POST');
    request.flush(null, { status: 204, statusText: 'No Content' });
    await promise;
  });
});
