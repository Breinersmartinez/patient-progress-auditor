import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { NotificationsService, NotificationView } from './notifications.service';
import { SessionService } from './session.service';

const DOCTOR = { id: 'm1', displayName: 'Dr. Andrés Salazar', role: 'MEDICO', permissions: ['NOTIFICATIONS_READ'] };
const NURSE = { id: 'n1', displayName: 'Camilo Torres', role: 'ENFERMERIA', permissions: [] };

const NOTIFICATION: NotificationView = {
  id: 'n1', type: 'ENTRY', patientId: 'p1', patientAlias: 'P-014', entryId: 'e1', entryDate: '2026-10-25',
  createdAt: '2026-10-25T19:10:00Z', read: false,
};

describe('NotificationsService (D30, RF-45)', () => {
  let http: HttpTestingController;
  let service: NotificationsService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    service = TestBed.inject(NotificationsService);
  });

  afterEach(() => http.verify());

  it('con enfermería, refresh() no pide nada y deja la lista vacía', async () => {
    TestBed.inject(SessionService).session.set(NURSE);
    await service.refresh();

    expect(service.notifications()).toEqual([]);
  });

  it('con el médico, refresh() trae la lista y hasUnread() refleja lo sin leer', async () => {
    TestBed.inject(SessionService).session.set(DOCTOR);
    const promise = service.refresh();
    http.expectOne('/api/notifications').flush([NOTIFICATION]);
    await promise;

    expect(service.notifications()).toEqual([NOTIFICATION]);
    expect(service.hasUnread()).toBe(true);
  });

  it('markRead() actualiza la lista de inmediato y confirma con el servidor', async () => {
    TestBed.inject(SessionService).session.set(DOCTOR);
    const refreshing = service.refresh();
    http.expectOne('/api/notifications').flush([NOTIFICATION]);
    await refreshing;

    const marking = service.markRead('n1');
    expect(service.notifications()[0].read).toBe(true); // optimista: no espera al servidor
    const request = http.expectOne('/api/notifications/n1/read');
    expect(request.request.method).toBe('POST');
    request.flush(null);
    await marking;

    expect(service.hasUnread()).toBe(false);
  });

  it('markPatientRead() marca leídas las de ese paciente y ninguna otra', async () => {
    TestBed.inject(SessionService).session.set(DOCTOR);
    const other: NotificationView = { ...NOTIFICATION, id: 'n2', patientId: 'p9', patientAlias: 'P-021' };
    const refreshing = service.refresh();
    http.expectOne('/api/notifications').flush([NOTIFICATION, other]);
    await refreshing;

    const marking = service.markPatientRead('p1');
    const request = http.expectOne('/api/patients/p1/notifications/read');
    request.flush(null);
    await marking;

    expect(service.notifications().find((n) => n.id === 'n1')?.read).toBe(true);
    expect(service.notifications().find((n) => n.id === 'n2')?.read).toBe(false);
  });

  it('con enfermería, markPatientRead() no pide nada (no tiene el permiso)', async () => {
    TestBed.inject(SessionService).session.set(NURSE);
    await service.markPatientRead('p1');

    expect(http.match('/api/patients/p1/notifications/read').length).toBe(0);
  });

  it('markReportRead() refresca y marca leídas solo las del informe dado', async () => {
    TestBed.inject(SessionService).session.set(DOCTOR);
    const reportNotif: NotificationView = { ...NOTIFICATION, id: 'n3', type: 'REPORT', entryId: undefined, reportId: 'rep1', reportType: 'SEMANAL' };
    const marking = service.markReportRead('rep1');
    http.expectOne('/api/notifications').flush([NOTIFICATION, reportNotif]);
    await new Promise((resolve) => setTimeout(resolve));
    const mark = http.expectOne('/api/notifications/n3/read');
    mark.flush(null);
    await marking;

    expect(service.notifications().find((n) => n.id === 'n3')?.read).toBe(true);
    expect(service.notifications().find((n) => n.id === 'n1')?.read).toBe(false);
  });
});
