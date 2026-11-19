import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

// Una anotación de la bitácora de acciones (RF-35). Solo metadatos: "sobre qué" son códigos y fechas, nunca texto clínico.
export interface AuditItem {
  id: number;
  occurredAt: string;
  actor: { id: string; name: string; role: string };
  action: string;
  entityType: 'REPORT' | 'ENTRY' | 'USER' | string;
  entityId: string;
  subject: {
    patientCode: string | null;
    reportType: string | null;
    periodStart: string | null;
    entryDate: string | null;
    userName: string | null;
  };
  before: Record<string, unknown> | null;
  after: Record<string, unknown> | null;
}

export interface AuditPage {
  items: AuditItem[];
  page: number;
  size: number;
  total: number;
}

export interface AuditFilters {
  actorId?: string;
  action?: string;
  page: number;
}

// Solo lectura: la API no tiene forma de escribir ni de borrar la bitácora.
@Injectable({ providedIn: 'root' })
export class AuditService {
  private readonly http = inject(HttpClient);

  list(filters: AuditFilters): Promise<AuditPage> {
    let params = new HttpParams().set('page', filters.page);
    if (filters.actorId) {
      params = params.set('actorId', filters.actorId);
    }
    if (filters.action) {
      params = params.set('action', filters.action);
    }
    return firstValueFrom(this.http.get<AuditPage>('/api/admin/audit', { params }));
  }
}
