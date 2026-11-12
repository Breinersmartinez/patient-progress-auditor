import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { Claim, CitedEntry, InsufficientData, ReportMetrics } from './reports.service';
import { Run } from './runs.service';

// El contenido de la consulta (D31, RF-46): el mismo esquema del informe semanal (resumen, afirmaciones, cifras,
// patrón, lo que falta), sin `weekly_reports` (eso es solo del mensual) y con `compared_with` null si no se comparó.
export interface ConsultationContent {
  period: { start: string; end: string; compared_with: string | null };
  summary: string;
  claims: Claim[];
  changes_vs_previous: string[];
  recurring_patterns: string[];
  metrics: ReportMetrics;
  insufficient_data: InsufficientData[];
  out_of_scope?: string | null;
}

export interface ConsultationDetail {
  id: string;
  patientId: string;
  patientAlias: string;
  periodStart: string;
  periodEnd: string;
  comparisonStart?: string;
  comparisonEnd?: string;
  content?: ConsultationContent;
  plainText?: string;
  runId: string;
  createdAt: string;
  citations: Record<string, CitedEntry[]>;
}

// La consulta del médico, «Armar informe» (D31, RF-46): pedirla, leerla y su PDF. Seguir el progreso en vivo es
// RunsService (se reutiliza /api/runs/{id}, abierto al médico para sus propias consultas), no esta clase.
@Injectable({ providedIn: 'root' })
export class ConsultationsService {
  private readonly http = inject(HttpClient);

  request(patientId: string, periodStart: string, periodEnd: string, comparisonStart?: string, comparisonEnd?: string): Promise<Run> {
    const body: Record<string, string> = { patientId, periodStart, periodEnd };
    if (comparisonStart && comparisonEnd) {
      body['comparisonStart'] = comparisonStart;
      body['comparisonEnd'] = comparisonEnd;
    }
    return firstValueFrom(this.http.post<Run>('/api/consultations', body));
  }

  get(id: string): Promise<ConsultationDetail> {
    return firstValueFrom(this.http.get<ConsultationDetail>(`/api/consultations/${encodeURIComponent(id)}`));
  }

  // El PDF de la consulta, con la marca «Consulta del médico» (RN-12). Mismo patrón que ReportsService.pdf.
  async pdf(id: string): Promise<{ blob: Blob; fileName: string | null }> {
    try {
      const response = await firstValueFrom(this.http.get(`/api/consultations/${encodeURIComponent(id)}/pdf`, { observe: 'response', responseType: 'blob' }));
      const name = /filename="?([^";]+)"?/.exec(response.headers.get('Content-Disposition') ?? '');
      return { blob: response.body as Blob, fileName: name ? name[1] : null };
    } catch (e) {
      if (e instanceof HttpErrorResponse && e.error instanceof Blob) {
        const body = JSON.parse(await e.error.text().catch(() => 'null')) as unknown;
        throw new HttpErrorResponse({ status: e.status, statusText: e.statusText, error: body });
      }
      throw e;
    }
  }
}
