import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { NotificationsService, NotificationView } from '../core/notifications.service';
import { BellComponent } from './bell.component';

const ENTRY_NOTIF: NotificationView = {
  id: 'n1', type: 'ENTRY', patientId: 'p1', patientAlias: 'P-014', entryId: 'e1', entryDate: '2026-10-25',
  createdAt: '2026-10-25T19:10:00Z', read: false,
};

const REPORT_NOTIF: NotificationView = {
  id: 'n2', type: 'REPORT', patientId: 'p9', patientAlias: 'P-021', reportId: 'rep1', reportType: 'SEMANAL',
  createdAt: '2026-10-26T08:15:00Z', read: true,
};

const MONTHLY_REPORT_NOTIF: NotificationView = {
  id: 'n3', type: 'REPORT', patientId: 'p1', patientAlias: 'P-014', reportId: 'rep2', reportType: 'MENSUAL',
  createdAt: '2026-10-30T08:15:00Z', read: false,
};

@Component({
  standalone: true,
  imports: [BellComponent],
  template: `<div><app-bell /><button id="afuera" type="button">Afuera</button></div>`,
})
class HostComponent {}

describe('BellComponent (pintarCampana del mockup)', () => {
  let http: HttpTestingController;
  let notifications: NotificationsService;
  let root: HTMLElement;

  function setup() {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])] });
    http = TestBed.inject(HttpTestingController);
    notifications = TestBed.inject(NotificationsService);
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    root = fixture.nativeElement;
    return fixture;
  }

  afterEach(() => http.verify());

  it('sin notificaciones sin leer, no muestra el punto, y el panel empieza cerrado', () => {
    setup();

    expect(root.querySelector('.punto-notif')).toBeNull();
    expect(root.querySelector('.campana-panel')).toBeNull();
    expect(root.querySelector('.icbtn')?.getAttribute('aria-label')).toBe('Notificaciones');
  });

  it('con alguna sin leer, muestra el punto y lo dice en aria-label', () => {
    const fixture = setup();
    notifications.notifications.set([ENTRY_NOTIF, REPORT_NOTIF]);
    fixture.detectChanges();

    expect(root.querySelector('.punto-notif')).not.toBeNull();
    expect(root.querySelector('.icbtn')?.getAttribute('aria-label')).toBe('Notificaciones, hay sin leer');
  });

  // D30, RF-45: la campana distingue semanal de mensual (hallazgo de la validación de la Pieza 5, 2026-11-08).
  it('un informe mensual recibido dice «Informe mensual recibido», distinto del semanal', () => {
    const fixture = setup();
    notifications.notifications.set([REPORT_NOTIF, MONTHLY_REPORT_NOTIF]);
    fixture.detectChanges();
    root.querySelector<HTMLElement>('.icbtn')!.click();
    fixture.detectChanges();

    const items = Array.from(root.querySelectorAll('.notif'));
    expect(items[0].textContent).toContain('Informe semanal recibido, paciente P-021');
    expect(items[1].textContent).toContain('Informe mensual recibido, paciente P-014');
  });

  it('al pulsar la campana, abre el panel con cada notificación y su hora; sin ninguna, dice "Sin notificaciones."', () => {
    const fixture = setup();
    notifications.notifications.set([ENTRY_NOTIF, REPORT_NOTIF]);
    fixture.detectChanges();

    root.querySelector<HTMLElement>('.icbtn')!.click();
    fixture.detectChanges();

    const items = Array.from(root.querySelectorAll('.notif'));
    expect(items.length).toBe(2);
    expect(items[0].textContent).toContain('Nuevo registro del domingo 25 de octubre, paciente P-014');
    expect(items[1].textContent).toContain('Informe semanal recibido, paciente P-021');
    // La sin leer tiene el punto pintado; la leída, transparente (pintarCampana del mockup).
    expect((items[0].querySelector('.pt') as HTMLElement).style.background).not.toBe('transparent');
    expect((items[1].querySelector('.pt') as HTMLElement).style.background).toBe('transparent');
  });

  it('sin notificaciones, el panel dice "Sin notificaciones."', () => {
    const fixture = setup();
    root.querySelector<HTMLElement>('.icbtn')!.click();
    fixture.detectChanges();

    expect(root.querySelector('.campana-panel')?.textContent).toContain('Sin notificaciones.');
  });

  it('pulsar una notificación sin leer la marca leída, cierra el panel y navega a su destino', async () => {
    const fixture = setup();
    const navigate = spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);
    notifications.notifications.set([ENTRY_NOTIF]);
    fixture.detectChanges();
    root.querySelector<HTMLElement>('.icbtn')!.click();
    fixture.detectChanges();

    root.querySelector<HTMLElement>('.notif')!.click();
    const request = http.expectOne('/api/notifications/n1/read');
    expect(request.request.method).toBe('POST');
    request.flush(null);
    await fixture.whenStable();
    fixture.detectChanges();

    expect(root.querySelector('.campana-panel')).toBeNull();
    expect(navigate).toHaveBeenCalledWith('/med/pacientes/ficha/p1');
  });

  it('pulsar un informe recibido navega al informe, sin pedir marcarlo (ya está leído)', async () => {
    const fixture = setup();
    const navigate = spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);
    notifications.notifications.set([REPORT_NOTIF]);
    fixture.detectChanges();
    root.querySelector<HTMLElement>('.icbtn')!.click();
    fixture.detectChanges();

    root.querySelector<HTMLElement>('.notif')!.click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(navigate).toHaveBeenCalledWith('/med/informes/ver/rep1');
  });

  it('un clic afuera de la campana cierra el panel', () => {
    const fixture = setup();
    root.querySelector<HTMLElement>('.icbtn')!.click();
    fixture.detectChanges();
    expect(root.querySelector('.campana-panel')).not.toBeNull();

    root.querySelector<HTMLElement>('#afuera')!.click();
    fixture.detectChanges();

    expect(root.querySelector('.campana-panel')).toBeNull();
  });
});
