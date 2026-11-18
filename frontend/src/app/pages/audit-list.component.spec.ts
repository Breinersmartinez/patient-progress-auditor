import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { routes } from '../app.routes';
import { AuditItem, AuditPage } from '../core/audit.service';
import { fechaHora } from '../core/date-es';
import { ADMIN_MENU, DOCTOR_MENU, NURSING_MENU } from '../core/menu';
import { AppUser } from '../core/users.service';
import { AuditListComponent } from './audit-list.component';

const USERS: AppUser[] = [
  { id: 'u-nurse', name: 'Marcela Rojas', email: 'marcela.rojas@example.test', role: 'ENFERMERIA', active: true },
  { id: 'u-admin', name: 'Administrador Auditor', email: 'admin@example.test', role: 'ADMIN', active: true },
];

const noSubject = { patientCode: null, reportType: null, periodStart: null, entryDate: null, userName: null };

function item(partial: Partial<AuditItem> & Pick<AuditItem, 'id' | 'action' | 'entityType'>): AuditItem {
  return {
    occurredAt: '2026-11-12T14:15:00Z',
    actor: { id: 'u-nurse', name: 'Marcela Rojas', role: 'ENFERMERIA' },
    entityId: 'e' + partial.id,
    subject: noSubject,
    before: null,
    after: null,
    ...partial,
  };
}

const ITEMS: AuditItem[] = [
  item({ id: 7, action: 'APROBAR_INFORME', entityType: 'REPORT',
    subject: { ...noSubject, patientCode: 'P-014', reportType: 'SEMANAL', periodStart: '2026-10-12' },
    before: { status: 'PENDIENTE_REVISION' }, after: { status: 'APROBADO', approvedVersionNo: 2 } }),
  item({ id: 6, action: 'RECHAZAR_INFORME', entityType: 'REPORT',
    subject: { ...noSubject, patientCode: 'P-021', reportType: 'MENSUAL', periodStart: '2026-09-28' },
    before: { status: 'PENDIENTE_REVISION' }, after: { status: 'RECHAZADO', reasonLength: 54 } }),
  item({ id: 5, action: 'REGISTRAR_ENTRADA_MEDICO', entityType: 'ENTRY',
    actor: { id: 'u-doc', name: 'Dr. Andrés Salazar', role: 'MEDICO' },
    subject: { ...noSubject, patientCode: 'P-021', entryDate: '2026-11-12' },
    after: { entryDate: '2026-11-12', source: 'VOZ', structuringStatus: 'ESTRUCTURADA', events: 2 } }),
  item({ id: 4, action: 'ACTUALIZAR_USUARIO', entityType: 'USER',
    actor: { id: 'u-admin', name: 'Administrador Auditor', role: 'ADMIN' },
    subject: { ...noSubject, userName: 'Luis Gómez' },
    before: { name: 'Luis Gómez', role: 'ENFERMERIA', active: true }, after: { name: 'Luis Gómez', role: 'MEDICO', active: false } }),
  item({ id: 3, action: 'EDITAR_INFORME', entityType: 'REPORT', subject: noSubject,
    before: { versionNo: 1 }, after: { versionNo: 2 } }),
];

const page = (items: AuditItem[], total = items.length, n = 0): AuditPage => ({ items, page: n, size: 50, total });

describe('AuditListComponent (bitácora del administrador, RF-35)', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<AuditListComponent>;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  const auditRequest = (): TestRequest => http.expectOne((r) => r.url === '/api/admin/audit');

  async function settle(): Promise<HTMLElement> {
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  async function load(answer: AuditPage | 'error', users: AppUser[] = USERS): Promise<HTMLElement> {
    fixture = TestBed.createComponent(AuditListComponent);
    fixture.detectChanges();
    http.expectOne('/api/admin/users').flush(users);
    const request = auditRequest();
    expect(request.request.params.get('page')).toBe('0');
    if (answer === 'error') {
      request.flush({ message: 'El servidor no respondió.' }, { status: 500, statusText: 'Error' });
    } else {
      request.flush(answer);
    }
    return settle();
  }

  const cells = (row: Element) => Array.from(row.querySelectorAll('td')).map((td) => td.textContent?.trim());

  it('muestra cuándo, quién, rol, qué hizo, sobre qué y el cambio, con códigos y no texto clínico', async () => {
    const root = await load(page(ITEMS));
    const rows = Array.from(root.querySelectorAll('tbody tr'));

    expect(Array.from(root.querySelectorAll('th')).map((th) => th.textContent?.trim()))
      .toEqual(['Cuándo', 'Quién', 'Rol', 'Qué hizo', 'Sobre qué', 'Cambio']);
    expect(rows.length).toBe(5);
    expect(cells(rows[0])).toEqual([fechaHora('2026-11-12T14:15:00Z'), 'Marcela Rojas', 'Enfermería', 'Aprobó un informe',
      'P-014 · Informe semanal del 12 al 18 de octubre de 2026', 'En revisión → Aprobado (versión 2)']);
    expect(cells(rows[1])[4]).toBe('P-021 · Informe mensual del 28 de septiembre al 25 de octubre de 2026');
    expect(cells(rows[1])[5]).toBe('En revisión → Rechazado · motivo de 54 caracteres');
    expect(cells(rows[2]).slice(1)).toEqual(['Dr. Andrés Salazar', 'Médico', 'Registró una entrada',
      'P-021 · Registro del 12 de noviembre de 2026', 'Registro dictado, 2 eventos confirmados']);
    expect(cells(rows[3]).slice(2)).toEqual(['Administración', 'Actualizó un usuario', 'Usuario Luis Gómez',
      'Rol: Enfermería → Médico · Estado: Activo → Inactivo']);
    // Un informe que ya se borró se sigue viendo en la bitácora, sin datos.
    expect(cells(rows[4]).slice(3)).toEqual(['Editó un informe', 'Un informe que ya no existe', 'Versión 1 → 2']);
    expect(root.querySelector('.total')?.textContent?.trim()).toBe('5 resultados');
    expect(fechaHora('2026-11-12T14:15:00Z')).toBe('12 nov 2026, 09:15');
  });

  it('es de solo lectura: sin botones, sin enlaces de edición y las filas no llevan a ninguna parte', async () => {
    const root = await load(page(ITEMS));
    expect(root.querySelectorAll('.cont button, .cab button, .cab a, tbody a').length).toBe(0);
    expect(root.querySelector('tbody tr')?.getAttribute('data-href')).toBeNull();
  });

  it('sin acciones dice que aún no hay; con un filtro, que no hay resultados', async () => {
    let root = await load(page([]));
    expect(root.querySelector('.vacio h3')?.textContent).toBe('Aún no hay acciones registradas');

    (root.querySelector('#aud-accion') as HTMLSelectElement).value = 'ENVIAR_INFORME';
    root.querySelector('#aud-accion')!.dispatchEvent(new Event('change'));
    auditRequest().flush(page([]));
    root = await settle();
    expect(root.querySelector('.vacio h3')?.textContent).toBe('Sin resultados');
  });

  it('filtra por persona y por tipo de acción en el servidor, y vuelve a la primera página', async () => {
    const root = await load(page(ITEMS, 120));
    const persona = root.querySelector('#aud-persona') as HTMLSelectElement;
    expect(Array.from(persona.options).map((o) => o.textContent?.trim()))
      .toEqual(['Todas las personas', 'Marcela Rojas (Enfermería)', 'Administrador Auditor (Administración)']);

    (root.querySelector('.paginas button:last-child') as HTMLButtonElement).click();
    const second = auditRequest();
    expect(second.request.params.get('page')).toBe('1');
    second.flush(page(ITEMS, 120, 1));
    await settle();

    persona.value = 'u-nurse';
    persona.dispatchEvent(new Event('change'));
    const byPerson = auditRequest();
    expect(byPerson.request.params.get('actorId')).toBe('u-nurse');
    expect(byPerson.request.params.get('page')).toBe('0');
    expect(byPerson.request.params.has('action')).toBeFalse();
    byPerson.flush(page(ITEMS.slice(0, 2), 2));
    await settle();

    const accion = root.querySelector('#aud-accion') as HTMLSelectElement;
    expect(accion.options.length).toBe(8);
    accion.value = 'RECHAZAR_INFORME';
    accion.dispatchEvent(new Event('change'));
    const both = auditRequest();
    expect(both.request.params.get('actorId')).toBe('u-nurse');
    expect(both.request.params.get('action')).toBe('RECHAZAR_INFORME');
    both.flush(page([ITEMS[1]], 1));
    const after = await settle();
    expect(after.querySelectorAll('tbody tr').length).toBe(1);
    expect(after.querySelector('.total')?.textContent?.trim()).toBe('1 resultado');
  });

  it('pagina de a 50 con «Anterior» y «Siguiente»', async () => {
    let root = await load(page(ITEMS, 120));
    const nav = () => root.querySelector('.paginas') as HTMLElement;
    expect(nav().textContent).toContain('Página 1 de 3');
    expect((nav().querySelectorAll('button')[0] as HTMLButtonElement).disabled).toBeTrue();

    (nav().querySelectorAll('button')[1] as HTMLButtonElement).click();
    auditRequest().flush(page(ITEMS, 120, 1));
    root = await settle();
    (nav().querySelectorAll('button')[1] as HTMLButtonElement).click();
    const last = auditRequest();
    expect(last.request.params.get('page')).toBe('2');
    last.flush(page(ITEMS.slice(0, 1), 120, 2));
    root = await settle();
    expect(nav().textContent).toContain('Página 3 de 3');
    expect((nav().querySelectorAll('button')[1] as HTMLButtonElement).disabled).toBeTrue();
    expect((nav().querySelectorAll('button')[0] as HTMLButtonElement).disabled).toBeFalse();
  });

  it('sin paginación cuando todo cabe en una página', async () => {
    const root = await load(page(ITEMS));
    expect(root.querySelector('.paginas')).toBeNull();
  });

  it('si el servidor falla, lo dice; si falla la lista de personas, la bitácora se muestra igual', async () => {
    let root = await load('error');
    expect(root.querySelector('.aviso')?.textContent).toContain('El servidor no respondió.');

    fixture = TestBed.createComponent(AuditListComponent);
    fixture.detectChanges();
    http.expectOne('/api/admin/users').flush({ message: 'x' }, { status: 500, statusText: 'Error' });
    auditRequest().flush(page(ITEMS));
    root = await settle();
    expect(root.querySelectorAll('tbody tr').length).toBe(5);
    expect((root.querySelector('#aud-persona') as HTMLSelectElement).options.length).toBe(1);
  });
});

describe('Bitácora en el menú y en las rutas', () => {
  it('solo el administrador la tiene en su menú', () => {
    expect(ADMIN_MENU.map((m) => m.path)).toEqual(['/adm/usuarios', '/adm/auditoria']);
    const all = (menu: typeof ADMIN_MENU) => menu.flatMap((m) => m.sub?.map((s) => s.path) ?? [m.path]);
    expect(all(NURSING_MENU)).not.toContain('/adm/auditoria');
    expect(all(DOCTOR_MENU)).not.toContain('/adm/auditoria');
  });

  it('la ruta exige el rol de administración', () => {
    const route = routes.find((r) => r.path === 'adm/auditoria');
    expect(route?.component).toBe(AuditListComponent);
    expect(route?.canMatch?.length).toBe(1);
    expect(route?.data?.['title']).toBe('Auditoría');
  });
});
