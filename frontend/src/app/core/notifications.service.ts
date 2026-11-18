import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { SessionService } from './session.service';

export interface NotificationView {
  id: string;
  type: 'ENTRY' | 'REPORT';
  patientId: string;
  patientAlias: string;
  entryId?: string;
  entryDate?: string;
  reportId?: string;
  reportType?: 'SEMANAL' | 'MENSUAL';
  createdAt: string;
  read: boolean;
}

// La campana del medico (D30, RF-45). Se actualiza al navegar entre pantallas, como el mockup: sin sondeo ni SSE
// (docs/PLAN_M3.md, Pieza 5). Enfermeria no tiene el permiso NOTIFICATIONS_READ: para ella no se pide nada.
@Injectable({ providedIn: 'root' })
export class NotificationsService {
  private readonly http = inject(HttpClient);
  private readonly session = inject(SessionService);

  readonly notifications = signal<NotificationView[]>([]);
  readonly hasUnread = computed(() => this.notifications().some((n) => !n.read));

  async refresh(): Promise<void> {
    if (this.session.session()?.role !== 'MEDICO') {
      this.notifications.set([]);
      return;
    }
    try {
      this.notifications.set(await firstValueFrom(this.http.get<NotificationView[]>('/api/notifications')));
    } catch {
      // La campana no es critica para el flujo de enfermeria: si falla, se queda con lo que ya tenia.
    }
  }

  async markRead(id: string): Promise<void> {
    this.notifications.update((list) => list.map((n) => (n.id === id ? { ...n, read: true } : n)));
    try {
      await firstValueFrom(this.http.post<void>(`/api/notifications/${encodeURIComponent(id)}/read`, {}));
    } catch {
      await this.refresh();
    }
  }

  // Al abrir la ficha de un paciente: solo las de registro (ENTRY) de ese paciente quedan leidas (como el mockup,
  // auditor.fuente.html:421); las de informe se marcan al abrir el informe o la notificación misma.
  async markPatientRead(patientId: string): Promise<void> {
    if (this.session.session()?.role !== 'MEDICO') {
      return;
    }
    this.notifications.update((list) => list.map((n) => (n.type === 'ENTRY' && n.patientId === patientId ? { ...n, read: true } : n)));
    try {
      await firstValueFrom(this.http.post<void>(`/api/patients/${encodeURIComponent(patientId)}/notifications/read`, {}));
    } catch {
      await this.refresh();
    }
  }

  // Al abrir el informe que avisa: evita que quede sin leer un informe que ya se vio (decision del encargo).
  async markReportRead(reportId: string): Promise<void> {
    if (this.session.session()?.role !== 'MEDICO') {
      return;
    }
    await this.refresh();
    const pending = this.notifications().filter((n) => n.type === 'REPORT' && n.reportId === reportId && !n.read);
    await Promise.all(pending.map((n) => this.markRead(n.id)));
  }
}
