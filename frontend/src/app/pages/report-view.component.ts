import { Component, OnDestroy, OnInit, computed, effect, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { apiFailure } from '../core/api-error';
import { bogotaDay, dCorto, dFull, fechaCorta, periodoTxt, sumar } from '../core/date-es';
import { EntriesService } from '../core/entries.service';
import { IconComponent } from '../core/icon.component';
import { MenuMarkService } from '../core/menu-mark.service';
import { Claim, CitedEntry, MonthlyMetrics, ReportContent, ReportDetail, ReportMetrics, ReportsService, TraceStep, VersionView } from '../core/reports.service';
import { symptomName } from '../core/symptoms';
import { ToastService } from '../core/toast.service';
import { EmptyStateComponent } from '../ui/empty-state.component';
import { ProcessButtonComponent } from '../ui/process-button.component';
import { REPORT_STATUS_LABELS, StatusChipComponent } from '../ui/status-chip.component';

interface DraftClaim {
  claimKey: string;
  text: string;
  criterion: string;
  entryIds: string[];
}

interface Draft {
  summary: string;
  claims: DraftClaim[];
}

interface Row {
  name: string;
  previous: string;
  current: string;
}

// Una fila de la tabla «Evolución semana a semana» del informe mensual: el síntoma y su valor en cada una de las cuatro semanas.
interface MonthRow {
  name: string;
  cells: string[];
}

const SHORT = 90;

// vInfVer() y hojaHTML() del mockup. La hoja se dibuja con el contenido validado (secciones de 3.5); cada [n] lleva a su
// fuente, que muestra el texto ORIGINAL de la entrada (RN-05, RN-07) y enlaza a la ficha. Editar crea una version nueva
// de enfermeria; la que redacto la IA se puede ver aparte. Las cifras no se editan: las calculo el codigo (RN-04).
@Component({
  selector: 'app-report-view',
  standalone: true,
  imports: [RouterLink, IconComponent, ProcessButtonComponent, StatusChipComponent, EmptyStateComponent],
  template: `
    @if (missing()) {
      <app-empty-state title="No encontramos lo que busca" text="Puede que haya cambiado o que su rol no tenga acceso."
                       [action]="{ text: 'Volver al inicio', path: '/enf/pacientes' }" />
    }
    @if (loadError(); as message) {
      <div class="aviso"><svg appIcon="rechazar"></svg><span>{{ message }}</span></div>
    }
    @if (detail(); as d) {
      <div class="acciones">
        <div class="izq">
          <b>Informe de {{ d.report.patientAlias }}</b>
          <span><app-status-chip [name]="statusLabel()" [dense]="true" /></span>
        </div>
        @if (d.runId) {
          <button class="btn btn--sec" type="button" data-a="traza" [attr.aria-expanded]="traceOpen()" (click)="toggleTrace()">
            {{ traceOpen() ? 'Ocultar cómo se generó' : 'Ver cómo se generó' }}
          </button>
        }
        @if (d.versions.length > 1) {
          <button class="btn btn--sec" type="button" data-a="ia" [attr.aria-expanded]="aiOpen()" (click)="toggleAi()">
            {{ aiOpen() ? 'Ocultar lo que escribió la IA' : 'Ver lo que escribió la IA' }}
          </button>
        }
        @if (pending()) {
          @if (editing()) {
            <button class="btn btn--sec" type="button" data-a="cancelar" (click)="cancelEdit()">Cancelar</button>
            <button appProcessButton #save="processButton" label="Guardar cambios" busyLabel="Guardando" icon="guardar"
                    class="btn--primary" data-a="guardar" (click)="saveEdit(save)"></button>
          } @else {
            <button class="btn btn--sec" type="button" data-a="rechazar" (click)="rejecting.set(true)">Rechazar</button>
            <button class="btn btn--sec" type="button" data-a="editar" (click)="startEdit()">Editar texto</button>
            <button appProcessButton #approve="processButton" label="Aprobar informe" busyLabel="Aprobando" icon="aprobar"
                    class="btn--primary" data-a="aprobar" (click)="approve_(approve)"></button>
          }
        } @else if (d.report.status !== 'APROBADO') {
          <a class="btn btn--sec" [routerLink]="['/enf/informes/generar', d.report.patientId]">Generar de nuevo</a>
        } @else {
          <button appProcessButton #download="processButton" label="Descargar PDF" busyLabel="Descargando" icon="descargar"
                  class="btn--sec" data-a="descargar" (click)="downloadPdf(download)"></button>
        }
        @if (note(); as text) { <p class="nota">{{ text }}</p> }
      </div>

      @if (pending() && !editing()) {
        <div class="aviso"><svg appIcon="ia"></svg><span>Esta es la vista previa tal como la verá el médico. La redactó la IA a partir de los registros; usted decide si se aprueba.</span></div>
      }
      @if (d.report.status === 'INCOMPLETO') {
        <div class="aviso"><svg appIcon="rechazar"></svg><span>Este informe quedó incompleto: {{ d.report.statusReason }} Solo muestra lo que el sistema pudo calcular y no se puede aprobar.</span></div>
      }
      @if (d.report.status === 'RECHAZADO') {
        <div class="aviso"><svg appIcon="rechazar"></svg><span>Informe rechazado por {{ d.report.reviewedBy }}: {{ d.report.statusReason }}</span></div>
      }
      @if (content()?.out_of_scope; as scope) {
        <div class="aviso"><svg appIcon="rechazar"></svg><span>{{ scope }}</span></div>
      }
      @if (formError(); as message) {
        <div class="aviso" role="alert"><svg appIcon="rechazar"></svg><span>{{ message }}</span></div>
      }
      @if (rejecting()) {
        <section class="card card--quieta" style="margin-bottom:16px">
          <div class="campo">
            <label for="ir-motivo">Motivo del rechazo</label>
            <textarea class="inp" id="ir-motivo" [value]="reason()" (input)="reason.set($any($event.target).value)"></textarea>
            <span class="error" data-e="motivo">{{ reasonError() }}</span>
          </div>
          <div class="botones">
            <button class="btn btn--sec" type="button" data-a="no-rechazar" (click)="rejecting.set(false)">Cancelar</button>
            <button appProcessButton #reject="processButton" label="Rechazar informe" busyLabel="Rechazando" icon="rechazar"
                    class="btn--primary" data-a="confirmar-rechazo" (click)="reject_(reject)"></button>
          </div>
        </section>
      }
      @if (traceOpen()) {
        <section class="tabla-wrap" style="margin-bottom:16px" aria-label="Cómo se generó el informe">
          @if (trace(); as steps) {
            <table class="tabla tabla--traza">
              <colgroup><col style="width:25%"><col style="width:14%"><col style="width:17%"><col style="width:26%"><col style="width:10%"><col style="width:8%"></colgroup>
              <thead>
                <tr><th scope="col">Paso</th><th scope="col">Herramienta</th><th scope="col">Parámetros</th><th scope="col">Resultado</th><th scope="col">Duración</th><th scope="col">Tokens</th></tr>
              </thead>
              <tbody>
                @for (step of steps; track step.id) {
                  <tr data-t="paso">
                    <td data-label="Paso"><span class="est est--d"><i class="dot" [style.background]="step.error ? '#E24B4A' : '#1D9E75'"></i>{{ step.message }}</span></td>
                    <td data-label="Herramienta">{{ step.tool ?? '—' }}</td>
                    <td data-label="Parámetros">{{ json(step.params) }}</td>
                    <td data-label="Resultado">{{ step.result?.text ? short(step.result?.text ?? '', 110) : '—' }}</td>
                    <td data-label="Duración">{{ step.durationMs !== undefined ? step.durationMs + ' ms' : '—' }}</td>
                    <td data-label="Tokens">{{ tokens(step) }}</td>
                  </tr>
                }
              </tbody>
            </table>
          } @else {
            <p class="sec" style="padding:16px">{{ traceError() ?? 'Cargando los pasos…' }}</p>
          }
        </section>
      }
      @if (aiOpen()) {
        <section class="card card--quieta" style="margin-bottom:16px" data-t="version-ia">
          <h3>Lo que escribió la IA (versión 1)</h3>
          @if (aiVersion(); as ai) {
            @if (ai.content; as original) {
              <p class="resumen">{{ original.summary }}</p>
              <ol>
                @for (claim of original.claims; track claim.claim_key) { <li>{{ claim.text }}</li> }
              </ol>
            } @else {
              <p class="sec">{{ ai.plainText }}</p>
            }
          } @else {
            <p class="sec">Cargando…</p>
          }
        </section>
      }

      <article class="hoja" id="hoja">
        <h1>Informe de seguimiento {{ monthly() ? 'mensual' : 'semanal' }}</h1>
        <p class="sub">Paciente {{ d.report.patientAlias }}, {{ monthly() ? 'del ' + period() + ' (cuatro semanas)' : 'semana del ' + period() }}</p>
        @if (content(); as c) {
          <div class="meta">
            @if (monthly()) {
              <span><b>Semanas incluidas:</b> {{ c.weekly_reports?.length ?? 0 }} informes semanales aprobados</span>
              <span><b>Registros revisados:</b> {{ daysWithEntry() }} de {{ monthWeeks().length * 7 }} días</span>
            } @else {
              <span><b>Comparado con:</b> {{ comparedWith() }}</span>
              <span><b>Registros revisados:</b> {{ c.metrics.days_with_entry }} de 7 días</span>
            }
            <span><b>Generado:</b> {{ generated() }}</span>
            <span><b>Aprobado por:</b> {{ d.report.status === 'APROBADO' && d.report.reviewedBy ? d.report.reviewedBy : 'pendiente de aprobación' }}</span>
          </div>
          <h2>Resumen de la evolución</h2>
          @if (editing()) {
            <div class="campo">
              <label for="ie-resumen">Resumen</label>
              <textarea class="inp" id="ie-resumen" [value]="draft().summary" (input)="draft().summary = $any($event.target).value"></textarea>
              <span class="error" data-e="summary">{{ errors()['summary'] }}</span>
            </div>
            @for (claim of draft().claims; track claim.claimKey; let i = $index) {
              <div class="campo" data-t="edicion">
                <label [attr.for]="'ie-texto-' + i">Afirmación [{{ i + 1 }}]</label>
                <textarea class="inp" [id]="'ie-texto-' + i" [value]="claim.text" (input)="claim.text = $any($event.target).value"></textarea>
                <span class="error" [attr.data-e]="'texto-' + i">{{ errors()['claims[' + i + '].text'] }}</span>
                <label [attr.for]="'ie-criterio-' + i">Criterio de comparación</label>
                <input class="inp" [id]="'ie-criterio-' + i" [value]="claim.criterion" (input)="claim.criterion = $any($event.target).value">
                <span class="error" [attr.data-e]="'criterio-' + i">{{ errors()['claims[' + i + '].criterion'] }}</span>
                <label>Fuentes</label>
                @for (id of claim.entryIds; track id) {
                  <div class="botones" style="align-items:center">
                    <span>{{ describe(id) }}</span>
                    <button class="btn btn--sec" type="button" data-a="quitar-fuente" (click)="removeSource(claim, id)">Quitar</button>
                  </div>
                }
                <select class="inp" [attr.aria-label]="'Agregar una fuente a la afirmación ' + (i + 1)" data-a="agregar-fuente"
                        (change)="addSource(claim, $any($event.target))">
                  <option value="">Agregar una fuente…</option>
                  @for (option of candidates(claim); track option.id) { <option [value]="option.id">{{ describe(option.id) }}</option> }
                </select>
                <span class="error" [attr.data-e]="'fuentes-' + i">{{ errors()['claims[' + i + '].entryIds'] }}</span>
              </div>
            }
          } @else {
            <p class="resumen">{{ c.summary }}
              @for (claim of c.claims; track claim.claim_key; let i = $index) {
                <span class="txt" [attr.data-i]="i"> {{ claim.text }}</span><sup><a class="ref" role="link" tabindex="0" [attr.aria-label]="'Ir a la fuente ' + (i + 1)"
                    (click)="goTo(i + 1)" (keydown.enter)="goTo(i + 1)">[{{ i + 1 }}]</a></sup>
              }
              @for (item of c.insufficient_data; track $index; let j = $index) {
                <span> {{ item.text }}</span><sup><a class="ref" role="link" tabindex="0" (click)="goTo(c.claims.length + j + 1)" (keydown.enter)="goTo(c.claims.length + j + 1)">[{{ c.claims.length + j + 1 }}]</a></sup>
              }
            </p>
          }
          @if (monthly()) {
            <h2>Evolución semana a semana</h2>
            <div class="tabla-scroll"><table data-t="evolucion">
              <tr>
                <th>Síntoma</th>
                @for (week of monthWeeks(); track week.week; let i = $index) { <th>Semana {{ i + 1 }} ({{ corta(week.week) }})</th> }
              </tr>
              @for (row of monthRows(); track row.name) {
                <tr><td>{{ row.name }}</td>@for (cell of row.cells; track $index) { <td>{{ cell }}</td> }</tr>
              }
            </table></div>
            <p class="nota">Cifras calculadas por el sistema a partir de los registros, no por la IA. Solo se listan los síntomas que aparecieron en alguna de las cuatro semanas.</p>
          } @else {
            <h2>Cifras de la semana</h2>
            <table>
              <tr><th>Síntoma</th><th>Período de comparación</th><th>Esta semana</th></tr>
              @for (row of rows(); track row.name) {
                <tr><td>{{ row.name }}</td><td>{{ row.previous }}</td><td>{{ row.current }}</td></tr>
              }
            </table>
            <p class="nota">Cifras calculadas por el sistema a partir de los registros, no por la IA. Solo se listan los síntomas que aparecieron en alguno de los dos períodos.</p>
          }
          @if (monthly() && c.weekly_reports?.length) {
            <h2>Informes semanales de origen</h2>
            <ol data-t="origen">
              @for (source of c.weekly_reports; track source.report_id) {
                <li>Semana {{ source.week_no }} ({{ weekRange(source.week_start) }}): {{ source.summary }}
                  <a [routerLink]="['/enf/informes/ver', source.report_id]">Ver el informe de esa semana</a></li>
              }
            </ol>
          }
          @if (pattern(); as p) {
            <h2>Patrón a destacar</h2>
            <p class="resumen">{{ p.claim.text }}<sup><a class="ref" role="link" tabindex="0" (click)="goTo(p.index)" (keydown.enter)="goTo(p.index)">[{{ p.index }}]</a></sup></p>
          }
          <h2>Fuentes (registros originales)</h2>
          <ol>
            @for (claim of c.claims; track claim.claim_key; let i = $index) {
              <li [id]="'fuente-' + (i + 1)">[{{ i + 1 }}]
                @for (source of sources(claim); track source.id; let last = $last) {
                  <a [routerLink]="['/enf/pacientes/ficha', d.report.patientId]" [attr.title]="'Ver la ficha del paciente'">{{ dCorto(source.date) }}: "{{ source.body }}"</a>@if (!last) { / }
                }
              </li>
            }
            @for (item of c.insufficient_data; track $index; let j = $index) {
              <li [id]="'fuente-' + (c.claims.length + j + 1)">[{{ c.claims.length + j + 1 }}] {{ missingSource(item) }}</li>
            }
          </ol>
        } @else {
          <h2>Texto sin validar</h2>
          <p class="resumen" style="white-space:pre-wrap">{{ d.version.plainText }}</p>
        }
        @if (content() && d.version.plainText) {
          <h2>Texto del modelo, sin validar</h2>
          <p class="nota">Lo que escribió la IA no cumplió el formato ni pasó las comprobaciones del sistema; se conserva solo para que usted lo vea y no es parte del informe.</p>
          <p class="resumen" style="white-space:pre-wrap" data-t="sin-validar">{{ d.version.plainText }}</p>
        }
        <div class="pie">
          <span>Describe la evolución documentada. No contiene diagnóstico ni recomendación de tratamiento.</span>
          <span>Revisado por: <b>{{ d.report.reviewedBy ?? 'pendiente' }}</b></span>
        </div>
      </article>
    }
  `,
})
export class ReportViewComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly reports = inject(ReportsService);
  private readonly entriesService = inject(EntriesService);
  private readonly toast = inject(ToastService);
  private readonly menuMark = inject(MenuMarkService);

  protected readonly detail = signal<ReportDetail | null>(null);
  protected readonly missing = signal(false);
  protected readonly loadError = signal<string | null>(null);
  protected readonly editing = signal(false);
  protected readonly draft = signal<Draft>({ summary: '', claims: [] });
  protected readonly errors = signal<Record<string, string>>({});
  protected readonly formError = signal<string | null>(null);
  protected readonly rejecting = signal(false);
  protected readonly reason = signal('');
  protected readonly reasonError = signal('');
  protected readonly traceOpen = signal(false);
  protected readonly trace = signal<TraceStep[] | null>(null);
  protected readonly traceError = signal<string | null>(null);
  protected readonly aiOpen = signal(false);
  protected readonly aiVersion = signal<VersionView | null>(null);
  // Todo registro que se puede citar o ya se cita, por id, para describirlo en el editor.
  private readonly entryIndex = signal(new Map<string, CitedEntry>());

  protected readonly dCorto = dCorto;
  protected readonly corta = fechaCorta;
  protected readonly weekRange = (start: string): string => periodoTxt(start, sumar(start, 6));
  protected readonly short = (text: string, max = SHORT): string => (text.length > max ? text.slice(0, max) + '…' : text);

  protected readonly content = computed<ReportContent | undefined>(() => this.detail()?.version.content);
  protected readonly pending = computed(() => this.detail()?.report.status === 'PENDIENTE_REVISION');
  protected readonly statusLabel = computed(() => REPORT_STATUS_LABELS[this.detail()?.report.status ?? ''] ?? '');
  protected readonly period = computed(() => {
    const report = this.detail()?.report;
    return report ? periodoTxt(report.periodStart, report.periodEnd) : '';
  });
  protected readonly generated = computed(() => dFull(bogotaDay(this.detail()?.report.generatedAt ?? '')));
  protected readonly comparedWith = computed(() => {
    const c = this.content();
    if (!c || this.monthly()) {
      return '';
    }
    const start = c.period.compared_with;
    return periodoTxt(start, sumar(start, 6)) + (c.metrics.vs_previous.comparable ? '' : ' (sin registros)');
  });
  protected readonly note = computed(() => {
    switch (this.detail()?.report.status) {
      case 'PENDIENTE_REVISION':
        return 'Revise el texto y las fuentes. Al aprobar, el informe queda guardado y el médico podrá consultarlo.';
      case 'APROBADO':
        return 'Informe aprobado y guardado. Ya puede consultarlo el médico y descargarlo en PDF.';
      default:
        return null;
    }
  });
  protected readonly monthly = computed(() => this.detail()?.report.reportType === 'MENSUAL');
  // En el mensual `metrics` trae las cuatro semanas (cada una con la forma de una semana) en lugar de una sola.
  protected readonly monthWeeks = computed<ReportMetrics[]>(() =>
    this.monthly() ? ((this.content()?.metrics as unknown as MonthlyMetrics | undefined)?.weeks ?? []) : []);
  protected readonly daysWithEntry = computed(() => this.monthWeeks().reduce((sum, week) => sum + week.days_with_entry, 0));
  protected readonly monthRows = computed<MonthRow[]>(() => {
    const weeks = this.monthWeeks();
    if (!weeks.length) {
      return [];
    }
    const days = (n: number): string => (n > 0 ? `${n} ${n === 1 ? 'día' : 'días'}` : 'No registrado');
    const names = [...new Set(weeks.flatMap((week) => Object.keys(week.symptoms)))];
    return [
      { name: 'Registros', cells: weeks.map((week) => String(week.entries)) },
      ...names.map((code) => ({
        name: symptomName(code, weeks.map((week) => week.symptoms[code]?.label).find(Boolean)),
        cells: weeks.map((week) => days(week.symptoms[code]?.days ?? 0)),
      })),
    ];
  });
  protected readonly rows = computed<Row[]>(() => {
    const m = this.content()?.metrics;
    if (!m || this.monthly()) {
      return [];
    }
    const comparable = m.vs_previous.comparable;
    const days = (n: number): string => (n > 0 ? `${n} ${n === 1 ? 'día' : 'días'}` : 'No registrado');
    const names = [...new Set([...Object.keys(m.symptoms), ...Object.keys(m.vs_previous.days)])];
    return [
      { name: 'Registros', previous: comparable ? String(m.vs_previous.previous_entries) : 'Sin datos', current: String(m.entries) },
      ...names.map((code) => {
        const now = m.symptoms[code];
        const times = now?.occurrences && now.occurrences > now.days ? `, ${now.occurrences} veces` : '';
        return {
          name: symptomName(code, now?.label),
          previous: comparable ? days(m.vs_previous.days[code]?.[0] ?? 0) : 'Sin datos',
          current: now && now.days > 0 ? days(now.days) + times : 'No registrado',
        };
      }),
    ];
  });
  protected readonly pattern = computed(() => {
    const c = this.content();
    const key = c?.recurring_patterns?.[0];
    const index = c?.claims.findIndex((claim) => claim.claim_key === key) ?? -1;
    return c && key && index >= 0 ? { claim: c.claims[index], index: index + 1 } : null;
  });

  constructor() {
    // «En revisión» o «Aprobados» segun el estado del informe, como `marcar` del mockup.
    effect(() => {
      const status = this.detail()?.report.status;
      this.menuMark.path.set(status ? (status === 'PENDIENTE_REVISION' ? '/enf/informes/revision' : '/enf/informes/aprobados') : null);
    });
  }

  ngOnDestroy(): void {
    this.menuMark.path.set(null);
  }

  async ngOnInit(): Promise<void> {
    await this.load(this.route.snapshot.paramMap.get('id') ?? '');
  }

  private async load(id: string): Promise<void> {
    try {
      this.show(await this.reports.get(id));
    } catch (e) {
      const status = (e as { status?: number }).status;
      if (status === 404) {
        this.missing.set(true);
      } else {
        this.loadError.set(apiFailure(e).message);
      }
    }
  }

  private show(detail: ReportDetail): void {
    this.detail.set(detail);
    const index = new Map(this.entryIndex());
    Object.values(detail.citations).flat().forEach((entry) => index.set(entry.id, entry));
    this.entryIndex.set(index);
  }

  // ---- evidencia ----

  protected sources(claim: Claim): CitedEntry[] {
    return this.detail()?.citations[claim.claim_key] ?? [];
  }

  protected missingSource(item: { kind: string; text: string; dates?: string[] }): string {
    const c = this.content();
    if (item.kind === 'DIAS_SIN_REGISTRO' && item.dates?.length) {
      return item.dates.map((date) => `${dCorto(date)}: sin registro`).join(' / ');
    }
    if (item.kind === 'SEMANA_ANTERIOR_SIN_REGISTROS' && c) {
      return `Período de comparación (${periodoTxt(c.period.compared_with, sumar(c.period.compared_with, 6))}): sin registros`;
    }
    return 'Registros guardados solo con el texto: sus síntomas no se contaron';
  }

  protected goTo(n: number): void {
    const target = document.getElementById('fuente-' + n);
    target?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    target?.focus?.();
  }

  protected json(value: unknown): string {
    return value === undefined || value === null ? '—' : this.short(JSON.stringify(value), 120);
  }

  protected tokens(step: TraceStep): string {
    return step.tokensIn || step.tokensOut ? `${step.tokensIn ?? 0} + ${step.tokensOut ?? 0}` : '—';
  }

  protected async toggleTrace(): Promise<void> {
    this.traceOpen.update((open) => !open);
    const d = this.detail();
    if (this.traceOpen() && d && !this.trace()) {
      try {
        this.trace.set(await this.reports.trace(d.report.id));
      } catch (e) {
        this.traceError.set(apiFailure(e).message);
      }
    }
  }

  protected async toggleAi(): Promise<void> {
    this.aiOpen.update((open) => !open);
    const d = this.detail();
    if (this.aiOpen() && d && !this.aiVersion()) {
      try {
        this.aiVersion.set(await this.reports.version(d.report.id, 1));
      } catch (e) {
        this.toast.show(apiFailure(e).message, false);
      }
    }
  }

  // ---- decidir ----

  protected async approve_(button: ProcessButtonComponent): Promise<void> {
    const d = this.detail();
    if (!d) {
      return;
    }
    this.formError.set(null);
    await button.run(async () => {
      try {
        this.show(await this.reports.approve(d.report.id));
        this.toast.show('Informe aprobado y guardado');
      } catch (e) {
        this.formError.set(apiFailure(e).message);
      }
    });
  }

  // RF-37: el PDF lo arma el servidor (JasperReports); aqui solo se descarga con el nombre que el servidor propone.
  protected async downloadPdf(button: ProcessButtonComponent): Promise<void> {
    const d = this.detail();
    if (!d) {
      return;
    }
    this.formError.set(null);
    await button.run(async () => {
      try {
        const file = await this.reports.pdf(d.report.id);
        const url = URL.createObjectURL(file.blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = file.fileName ?? `informe-${d.report.patientAlias}-${d.report.periodStart}.pdf`;
        link.click();
        URL.revokeObjectURL(url);
      } catch (e) {
        this.formError.set(apiFailure(e).message);
      }
    });
  }

  protected async reject_(button: ProcessButtonComponent): Promise<void> {
    const d = this.detail();
    if (!d) {
      return;
    }
    if (!this.reason().trim()) {
      this.reasonError.set('Escriba el motivo del rechazo.');
      return;
    }
    this.reasonError.set('');
    await button.run(async () => {
      try {
        this.show(await this.reports.reject(d.report.id, this.reason().trim()));
        this.rejecting.set(false);
        this.toast.show('Informe rechazado');
      } catch (e) {
        const failure = apiFailure(e);
        this.reasonError.set(failure.fields['reason'] ?? failure.message);
      }
    });
  }

  // ---- editar ----

  protected async startEdit(): Promise<void> {
    const d = this.detail();
    const c = this.content();
    if (!d || !c) {
      return;
    }
    this.errors.set({});
    this.formError.set(null);
    this.draft.set({
      summary: c.summary,
      claims: c.claims.map((claim) => ({ claimKey: claim.claim_key, text: claim.text, criterion: claim.criterion, entryIds: [...claim.entry_ids] })),
    });
    this.editing.set(true);
    // Los registros que se pueden citar: los de la semana del informe y los de la anterior.
    try {
      const list = await this.entriesService.forPatient(d.report.patientId, { from: sumar(d.report.periodStart, -7), to: d.report.periodEnd });
      const index = new Map(this.entryIndex());
      list.forEach((entry) => index.set(entry.id, { id: entry.id, date: entry.entryDate, body: entry.body, author: entry.authorName }));
      this.entryIndex.set(index);
    } catch (e) {
      this.formError.set(apiFailure(e).message);
    }
  }

  protected cancelEdit(): void {
    this.editing.set(false);
    this.errors.set({});
    this.formError.set(null);
  }

  protected describe(id: string): string {
    const entry = this.entryIndex().get(id);
    return entry ? `${dCorto(entry.date)}: "${this.short(entry.body)}"` : 'Registro';
  }

  protected candidates(claim: DraftClaim): CitedEntry[] {
    const d = this.detail();
    return [...this.entryIndex().values()]
      .filter((entry) => !claim.entryIds.includes(entry.id) && !!d && entry.date <= d.report.periodEnd)
      .sort((a, b) => a.date.localeCompare(b.date));
  }

  protected removeSource(claim: DraftClaim, id: string): void {
    claim.entryIds = claim.entryIds.filter((entry) => entry !== id);
    this.draft.update((draft) => ({ ...draft }));
  }

  protected addSource(claim: DraftClaim, select: HTMLSelectElement): void {
    if (select.value) {
      claim.entryIds = [...claim.entryIds, select.value];
      select.value = '';
      this.draft.update((draft) => ({ ...draft }));
    }
  }

  protected async saveEdit(button: ProcessButtonComponent): Promise<void> {
    const d = this.detail();
    if (!d) {
      return;
    }
    this.errors.set({});
    this.formError.set(null);
    await button.run(async () => {
      try {
        this.show(await this.reports.edit(d.report.id, this.draft()));
        this.aiVersion.set(null);
        this.editing.set(false);
        this.toast.show('Cambios guardados');
      } catch (e) {
        const failure = apiFailure(e);
        this.errors.set(failure.fields);
        // Sin campo al que atribuirlo (409, 400): el mensaje va en un aviso.
        this.formError.set(Object.keys(failure.fields).length ? failure.message : failure.message);
      }
    });
  }
}
