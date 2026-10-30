import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

export type ReportStatus = 'PENDIENTE_REVISION' | 'APROBADO' | 'INCOMPLETO' | 'RECHAZADO';

// Los campos nulos no viajan en el JSON del API: todo lo opcional llega ausente (nunca null).
export interface ReportSummary {
  id: string;
  patientId: string;
  patientAlias: string;
  reportType: 'SEMANAL' | 'MENSUAL';
  periodStart: string;
  periodEnd: string;
  status: ReportStatus;
  statusReason?: string;
  generatedAt: string;
  reviewedBy?: string;
  reviewedAt?: string;
  provider?: string;
  model?: string;
}

export interface Claim {
  claim_key: string;
  text: string;
  criterion: string;
  entry_ids: string[];
  figures?: { key: string; value: number }[];
}

export interface SymptomMetric {
  // Solo en un síntoma fuera del catálogo.
  label?: string;
  days: number;
  dates: string[];
  events: number;
  occurrences?: number | null;
  max_intensity?: string | null;
}

// Las cifras las calculo el codigo (RN-04); la pantalla solo las muestra.
export interface ReportMetrics {
  week: string;
  entries: number;
  days_with_entry: number;
  days_without_entry: number;
  missing_dates: string[];
  unstructured_entries: number;
  total_events: number;
  symptoms: Record<string, SymptomMetric>;
  vs_previous: {
    week: string;
    comparable: boolean;
    previous_entries: number;
    // Por sintoma: [dias de la semana anterior, dias de esta semana].
    days: Record<string, [number, number]>;
  };
}

export interface InsufficientData {
  kind: string;
  text: string;
  dates?: string[];
  week?: string;
}

// Una de las cuatro semanas de un informe mensual y el estado de su informe semanal (RN-10).
export interface MonthWeek {
  weekNo: number;
  weekStart: string;
  weekEnd: string;
  reportId?: string;
  status?: ReportStatus;
  approved: boolean;
}

// Una ventana de cuatro semanas completas consecutivas que se puede pedir como informe mensual (RF-36).
export interface MonthOption {
  start: string;
  end: string;
  weeks: MonthWeek[];
  reportId?: string;
  reportStatus?: ReportStatus;
  runId?: string;
  canGenerate: boolean;
  reason?: string;
}

// De cual informe semanal aprobado sale cada semana del mensual.
export interface WeeklySource {
  week_no: number;
  week_start: string;
  report_id: string;
  summary: string;
}

// En el mensual `metrics` no es una semana sino las cuatro, cada una con la forma de ReportMetrics.
export interface MonthlyMetrics {
  weeks: ReportMetrics[];
}

export interface ReportContent {
  period: { start: string; end: string; compared_with: string; weeks?: string[] };
  weekly_reports?: WeeklySource[];
  summary: string;
  claims: Claim[];
  changes_vs_previous: string[];
  recurring_patterns: string[];
  metrics: ReportMetrics;
  insufficient_data: InsufficientData[];
  out_of_scope?: string | null;
}

export interface VersionMeta {
  versionNo: number;
  origin: 'MODELO' | 'ENFERMERIA';
  editedBy?: string;
  createdAt: string;
}

export interface VersionView extends VersionMeta {
  content?: ReportContent;
  plainText?: string;
}

export interface CitedEntry {
  id: string;
  date: string;
  // El texto original de la entrada: la fuente citable (RN-05, RN-07).
  body: string;
  author?: string;
}

export interface ReportDetail {
  report: ReportSummary;
  requestedBy?: string;
  runId?: string;
  approvedVersionNo?: number;
  version: VersionView;
  versions: VersionMeta[];
  citations: Record<string, CitedEntry[]>;
}

export interface TraceStep {
  id: number;
  seq: number;
  type: string;
  message?: string;
  tool?: string;
  params?: unknown;
  result?: { chars?: number; text?: string } | null;
  error: boolean;
  durationMs?: number;
  tokensIn?: number;
  tokensOut?: number;
  at: string;
}

export interface WeekOption {
  weekStart: string;
  weekEnd: string;
  entries: number;
  reportId?: string;
  reportStatus?: ReportStatus;
  runId?: string;
  canGenerate: boolean;
  reason?: string;
}

export interface ClaimEdit {
  claimKey: string;
  text: string;
  criterion: string;
  entryIds: string[];
}

export interface ReportEdit {
  summary: string;
  claims: ClaimEdit[];
}

@Injectable({ providedIn: 'root' })
export class ReportsService {
  private readonly http = inject(HttpClient);

  list(filter: { status?: ReportStatus; patientId?: string } = {}): Promise<ReportSummary[]> {
    let params = new HttpParams();
    if (filter.status) {
      params = params.set('status', filter.status);
    }
    if (filter.patientId) {
      params = params.set('patientId', filter.patientId);
    }
    return firstValueFrom(this.http.get<ReportSummary[]>('/api/reports', { params }));
  }

  get(id: string): Promise<ReportDetail> {
    return firstValueFrom(this.http.get<ReportDetail>(`/api/reports/${encodeURIComponent(id)}`));
  }

  version(id: string, versionNo: number): Promise<VersionView> {
    return firstValueFrom(this.http.get<VersionView>(`/api/reports/${encodeURIComponent(id)}/versions/${versionNo}`));
  }

  trace(id: string): Promise<TraceStep[]> {
    return firstValueFrom(this.http.get<TraceStep[]>(`/api/reports/${encodeURIComponent(id)}/trace`));
  }

  edit(id: string, edit: ReportEdit): Promise<ReportDetail> {
    return firstValueFrom(this.http.put<ReportDetail>(`/api/reports/${encodeURIComponent(id)}/content`, edit));
  }

  approve(id: string): Promise<ReportDetail> {
    return firstValueFrom(this.http.post<ReportDetail>(`/api/reports/${encodeURIComponent(id)}/approve`, {}));
  }

  reject(id: string, reason: string): Promise<ReportDetail> {
    return firstValueFrom(this.http.post<ReportDetail>(`/api/reports/${encodeURIComponent(id)}/reject`, { reason }));
  }

  // El PDF de un informe aprobado (RF-37). Va por HttpClient y no por un enlace porque el usuario elegido viaja en una
  // cabecera. Si el servidor lo niega (409, 404), el cuerpo del error llega como Blob: se vuelve a leer como JSON.
  async pdf(id: string): Promise<{ blob: Blob; fileName: string | null }> {
    try {
      const response = await firstValueFrom(this.http.get(`/api/reports/${encodeURIComponent(id)}/pdf`, { observe: 'response', responseType: 'blob' }));
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

  // Semanas completas con registros y el informe vigente o la ejecucion en marcha de cada una.
  weeks(patientId: string): Promise<WeekOption[]> {
    return firstValueFrom(this.http.get<WeekOption[]>(`/api/patients/${encodeURIComponent(patientId)}/report-weeks`));
  }

  // Ventanas de cuatro semanas con el estado del informe semanal de cada una (RN-10), la mas reciente primero.
  months(patientId: string): Promise<MonthOption[]> {
    return firstValueFrom(this.http.get<MonthOption[]>(`/api/patients/${encodeURIComponent(patientId)}/report-months`));
  }
}
