import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';

import { SessionService } from '../core/session.service';
import { ToastService } from '../core/toast.service';
import { AppUser } from '../core/users.service';
import { UserFormComponent } from './user-form.component';

const ANA: AppUser = { id: 'u1', name: 'Ana Prueba', email: 'ana@example.test', role: 'ENFERMERIA', active: true };

describe('UserFormComponent (vUsuForm del mockup)', () => {
  let http: HttpTestingController;
  let router: Router;
  let toast: ToastService;

  function setup(id: string | null, selfId = 'admin-1') {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap(id ? { id } : {}) } } },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
    // Se espía `show`: whenStable() espera también el temporizador del aviso (3,8 s) y para entonces ya se quitó solo.
    toast = TestBed.inject(ToastService);
    spyOn(toast, 'show').and.callThrough();
    spyOn(router, 'navigateByUrl').and.resolveTo(true);
    TestBed.inject(SessionService).session.set({ id: selfId, displayName: 'Admin', role: 'ADMIN', permissions: [] });
  }

  async function render(users?: AppUser[]) {
    const fixture = TestBed.createComponent(UserFormComponent);
    fixture.detectChanges();
    if (users) {
      http.expectOne('/api/admin/users').flush(users);
    }
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  const field = (root: HTMLElement, id: string) => root.querySelector('#' + id) as HTMLInputElement & HTMLSelectElement;
  const type = (el: HTMLInputElement, value: string) => { el.value = value; el.dispatchEvent(new Event('input')); };
  const submit = async (fixture: { nativeElement: HTMLElement; detectChanges(): void; whenStable(): Promise<unknown> }) => {
    (fixture.nativeElement.querySelector('form') as HTMLFormElement).dispatchEvent(new Event('submit'));
    await fixture.whenStable();
    fixture.detectChanges();
  };

  afterEach(() => http.verify());

  describe('nuevo usuario', () => {
    beforeEach(() => setup(null));

    it('pide nombre, correo y rol; el estado arranca en «Activo» y se puede cambiar', async () => {
      const root = (await render()).nativeElement as HTMLElement;
      expect(root.querySelector('button[type=submit]')?.textContent).toContain('Crear usuario');
      expect(field(root, 'uf-correo').disabled).toBeFalse();
      expect(field(root, 'uf-estado').disabled).toBeFalse();
      expect(Array.from(field(root, 'uf-rol').options).map((o) => o.textContent?.trim())).toEqual(['Enfermería', 'Médico', 'Administración']);
      expect(root.querySelector('button[type=button]')).toBeNull();
    });

    it('rechaza nombre vacío y correo mal escrito sin llamar al servidor', async () => {
      const fixture = await render();
      const root = fixture.nativeElement as HTMLElement;
      type(field(root, 'uf-correo'), 'sin-arroba');
      await submit(fixture);

      expect(root.querySelector('[data-e=nombre]')?.textContent).toBe('Escriba el nombre.');
      expect(root.querySelector('[data-e=correo]')?.textContent).toBe('Escriba un correo válido.');
      http.expectNone('/api/admin/users');
    });

    it('crea el usuario y vuelve a la lista con «Usuario creado»', async () => {
      const fixture = await render();
      const root = fixture.nativeElement as HTMLElement;
      type(field(root, 'uf-nombre'), ' Ana Prueba ');
      type(field(root, 'uf-correo'), 'ana@example.test');
      field(root, 'uf-rol').value = 'MEDICO';
      field(root, 'uf-rol').dispatchEvent(new Event('change'));
      await submit(fixture);

      const request = http.expectOne('/api/admin/users');
      expect(request.request.body).toEqual({ name: 'Ana Prueba', email: 'ana@example.test', role: 'MEDICO', active: true });
      request.flush({ user: { ...ANA, role: 'MEDICO' }, invitationSent: true });
      await fixture.whenStable();

      expect(toast.show).toHaveBeenCalledWith('Usuario creado');
      expect(router.navigateByUrl).toHaveBeenCalledWith('/adm/usuarios');
    });

    it('se puede crear ya inactivo: avisa que al activarlo hay que reenviar la invitación', async () => {
      const fixture = await render();
      const root = fixture.nativeElement as HTMLElement;
      type(field(root, 'uf-nombre'), 'Ana Prueba');
      type(field(root, 'uf-correo'), 'ana@example.test');
      field(root, 'uf-estado').value = 'Inactivo';
      field(root, 'uf-estado').dispatchEvent(new Event('change'));
      await submit(fixture);

      const request = http.expectOne('/api/admin/users');
      expect(request.request.body).toEqual({ name: 'Ana Prueba', email: 'ana@example.test', role: 'ENFERMERIA', active: false });
      request.flush({ user: { ...ANA, active: false }, invitationSent: false });
      await fixture.whenStable();

      expect(toast.show).toHaveBeenCalledWith('Usuario creado como inactivo. Al activarlo, reenvíe la invitación.');
      expect(router.navigateByUrl).toHaveBeenCalledWith('/adm/usuarios');
    });

    it('si la invitación no salió, avisa y abre la edición para reenviarla', async () => {
      const fixture = await render();
      const root = fixture.nativeElement as HTMLElement;
      type(field(root, 'uf-nombre'), 'Ana Prueba');
      type(field(root, 'uf-correo'), 'ana@example.test');
      await submit(fixture);
      http.expectOne('/api/admin/users').flush({ user: ANA, invitationSent: false });
      await fixture.whenStable();

      expect(toast.show).toHaveBeenCalledWith('Usuario creado, pero no se pudo enviar la invitación. Reenvíela desde aquí.', false);
      expect(router.navigateByUrl).toHaveBeenCalledWith('/adm/usuarios/editar/u1');
    });

    it('muestra el correo repetido bajo el campo y se queda en el formulario', async () => {
      const fixture = await render();
      const root = fixture.nativeElement as HTMLElement;
      type(field(root, 'uf-nombre'), 'Ana Prueba');
      type(field(root, 'uf-correo'), 'ana@example.test');
      await submit(fixture);
      http.expectOne('/api/admin/users').flush(
        { message: 'Ya existe un usuario con ese correo.', fields: { email: 'Ya existe un usuario con ese correo.' } },
        { status: 409, statusText: 'Conflict' });
      await fixture.whenStable();
      fixture.detectChanges();

      expect(root.querySelector('[data-e=correo]')?.textContent).toBe('Ya existe un usuario con ese correo.');
      expect(router.navigateByUrl).not.toHaveBeenCalled();
    });

    it('si Keycloak no responde (502) muestra el aviso del servidor, sin detalles', async () => {
      const fixture = await render();
      const root = fixture.nativeElement as HTMLElement;
      type(field(root, 'uf-nombre'), 'Ana Prueba');
      type(field(root, 'uf-correo'), 'ana@example.test');
      await submit(fixture);
      http.expectOne('/api/admin/users').flush({ message: 'No se pudo contactar el servicio de inicio de sesión. Intente de nuevo.' },
        { status: 502, statusText: 'Bad Gateway' });
      await fixture.whenStable();
      fixture.detectChanges();

      expect(root.querySelector('.aviso')?.textContent).toContain('No se pudo contactar el servicio de inicio de sesión');
    });
  });

  describe('editar usuario', () => {
    beforeEach(() => setup('u1'));

    it('carga al usuario, deja el correo fijo y permite desactivarlo', async () => {
      const fixture = await render([ANA]);
      const root = fixture.nativeElement as HTMLElement;
      expect(field(root, 'uf-nombre').value).toBe('Ana Prueba');
      expect(field(root, 'uf-correo').value).toBe('ana@example.test');
      expect(field(root, 'uf-correo').disabled).toBeTrue();
      expect(field(root, 'uf-estado').disabled).toBeFalse();
      expect(root.querySelector('button[type=submit]')?.textContent).toContain('Guardar cambios');

      field(root, 'uf-estado').value = 'Inactivo';
      field(root, 'uf-estado').dispatchEvent(new Event('change'));
      await submit(fixture);
      const request = http.expectOne('/api/admin/users/u1');
      expect(request.request.method).toBe('PUT');
      expect(request.request.body).toEqual({ name: 'Ana Prueba', role: 'ENFERMERIA', active: false });
      request.flush({ ...ANA, active: false });
      await fixture.whenStable();

      expect(toast.show).toHaveBeenCalledWith('Cambios guardados');
      expect(router.navigateByUrl).toHaveBeenCalledWith('/adm/usuarios');
    });

    it('«Reenviar invitación» llama al servidor y lo confirma', async () => {
      const fixture = await render([ANA]);
      const button = (fixture.nativeElement as HTMLElement).querySelector('button[type=button]') as HTMLButtonElement;
      expect(button.textContent).toContain('Reenviar invitación');
      button.click();
      http.expectOne('/api/admin/users/u1/invitation').flush(null, { status: 204, statusText: 'No Content' });
      await fixture.whenStable();

      expect(toast.show).toHaveBeenCalledWith('Invitación reenviada');
    });

    it('un usuario desactivado no ofrece reenviar la invitación', async () => {
      const root = (await render([{ ...ANA, active: false }])).nativeElement as HTMLElement;
      expect(root.querySelector('button[type=button]')).toBeNull();
    });

    it('un usuario que ya no existe vuelve a la lista con un aviso', async () => {
      await render([]);
      expect(router.navigateByUrl).toHaveBeenCalledWith('/adm/usuarios');
      expect(toast.show).toHaveBeenCalledWith('No encontramos a ese usuario.', false);
    });
  });

  describe('editarse a sí mismo', () => {
    it('no deja cambiar el propio rol ni desactivar la propia cuenta', async () => {
      setup('u1', 'u1');
      const root = (await render([ANA])).nativeElement as HTMLElement;
      expect(field(root, 'uf-rol').disabled).toBeTrue();
      expect(field(root, 'uf-estado').disabled).toBeTrue();
      expect(root.textContent).toContain('Es su propia cuenta');
    });
  });
});
