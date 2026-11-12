import { Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { map } from 'rxjs';

import { apiFailure } from '../core/api-error';
import { ConsultationContent, ConsultationDetail, ConsultationsService } from '../core/consultations.service';
import { bogotaDay, dCorto, dFull, periodoTxt } from '../core/date-es';
import { IconComponent } from '../core/icon.component';
import { roleHome } from '../core/roles';
import { SessionService } from '../core/session.service';
import { symptomName } from '../core/symptoms';
import { EmptyStateComponent } from '../ui/empty-state.component';
import { ProcessButtonComponent } from '../ui/process-button.component';

interface Row {
  name: string;
  previous: string;
  current: string;
}

const SHORT = 90;

// «Informe de consulta» del mockup (vInfVer con tmp:true): la consulta del médico, «Armar informe» (D31, RF-46,
// RN-12). No tiene estado, no se aprueba, no se envía y no se edita: solo se lee y se imprime. La marca «Consulta
// del médico» va en pantalla y en el PDF (ConsultationPdfBlocks), como pide RN-12.
@Component({
  selector: 'app-consultation-view',
  standalone: true,
  imports: [RouterLink, IconComponent, ProcessButtonComponent, EmptyStateComponent],
  template: `
    @if (missing()) {
      <app-empty-state title="No encontramos lo que busca" text="Puede que haya cambiado o que no sea suya."
                       [action]="{ text: 'Volver al inicio', path: home() }" />
    }
    @if (loadError(); as message) {
      <div class="aviso"><svg appIcon="rechazar"></svg><span>{{ message }}</span></div>
    }
    @if (detail(); as d) {
      <div class="acciones">
        <div class="izq">
          <b>Consulta de {{ d.patientAlias }}</b>
          <span class="marca">CONSULTA DEL MÉDICO</span>
        </div>
        <button appProcessButton #download="processButton" label="Descargar PDF" busyLabel="Descargando" icon="descargar"
                class="btn--primary" data-a="descargar" (click)="downloadPdf(download)"></button>
      </div>
      @if (formError(); as message) {
        <div class="aviso" role="alert"><svg appIcon="rechazar"></svg><span>{{ message }}</span></div>
      }

      <article class="hoja" id="hoja">
        <span class="marca marca--hoja">CONSULTA DEL MÉDICO</span>
        <h1>Informe de consulta</h1>
        <p class="sub">Paciente {{ d.patientAlias }}, período del {{ period() }}</p>
        @if (content(); as c) {
          <div class="meta">
            <span><b>Comparado con:</b> {{ comparedWith() }}</span>
            <span><b>Armada:</b> {{ generated() }}</span>
          </div>
          <h2>Resumen de la evolución</h2>
          <p class="resumen">{{ c.summary }}
            @for (claim of c.claims; track claim.claim_key; let i = $index) {
              <span class="txt" [attr.data-i]="i"> {{ claim.text }}</span><sup><a class="ref" role="link" tabindex="0" [attr.aria-label]="'Ir a la fuente ' + (i + 1)"
                  (click)="goTo(i + 1)" (keydown.enter)="goTo(i + 1)">[{{ i + 1 }}]</a></sup>
            }
            @for (item of c.insufficient_data; track $index; let j = $index) {
              <span> {{ item.text }}</span><sup><a class="ref" role="link" tabindex="0" (click)="goTo(c.claims.length + j + 1)" (keydown.enter)="goTo(c.claims.length + j + 1)">[{{ c.claims.length + j + 1 }}]</a></sup>
            }
          </p>
          <h2>Cifras del período</h2>
          <table>
            <tr><th>Síntoma</th><th>Período de comparación</th><th>Este período</th></tr>
            @for (row of rows(); track row.name) {
              <tr><td>{{ row.name }}</td><td>{{ row.previous }}</td><td>{{ row.current }}</td></tr>
            }
          </table>
          <p class="nota">Cifras calculadas por el sistema a partir de los registros, no por la IA. Solo se listan los síntomas que aparecieron en alguno de los dos períodos.</p>
          @if (pattern(); as p) {
            <h2>Patrón a destacar</h2>
            <p class="resumen">{{ p.claim.text }}<sup><a class="ref" role="link" tabindex="0" (click)="goTo(p.index)" (keydown.enter)="goTo(p.index)">[{{ p.index }}]</a></sup></p>
          }
          <h2>Fuentes (registros originales)</h2>
          <ol>
            @for (claim of c.claims; track claim.claim_key; let i = $index) {
              <li [id]="'fuente-' + (i + 1)">[{{ i + 1 }}]
                @for (source of sources(claim); track source.id; let last = $last) {
                  <span>{{ dCorto(source.date) }}: "{{ source.body }}"</span>@if (!last) { / }
                }
              </li>
            }
            @for (item of c.insufficient_data; track $index; let j = $index) {
              <li [id]="'fuente-' + (c.claims.length + j + 1)">[{{ c.claims.length + j + 1 }}] {{ missingSource(item) }}</li>
            }
          </ol>
        } @else {
          <h2>Texto sin validar</h2>
          <p class="resumen" style="white-space:pre-wrap">{{ d.plainText }}</p>
        }
        <div class="pie">
          <span>Generado con IA a partir de los registros del período. No pasó por la revisión de enfermería ni forma parte del historial oficial. No contiene diagnóstico ni recomendación de tratamiento.</span>
        </div>
      </article>
    }
  `,
})
export class ConsultationViewComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly consultations = inject(ConsultationsService);
  private readonly sessionService = inject(SessionService);

  protected readonly detail = signal<ConsultationDetail | null>(null);
  protected readonly missing = signal(false);
  protected readonly loadError = signal<string | null>(null);
  protected readonly formError = signal<string | null>(null);

  protected readonly dCorto = dCorto;
  protected readonly home = computed(() => roleHome(this.sessionService.session()?.role));
  protected readonly content = computed<ConsultationContent | undefined>(() => this.detail()?.content);
  protected readonly period = computed(() => {
    const d = this.detail();
    return d ? periodoTxt(d.periodStart, d.periodEnd) : '';
  });
  protected readonly generated = computed(() => dFull(bogotaDay(this.detail()?.createdAt ?? '')));
  protected readonly comparedWith = computed(() => {
    const d = this.detail();
    const c = this.content();
    if (!d || !c || !c.period.compared_with || !d.comparisonStart || !d.comparisonEnd) {
      return 'sin comparación';
    }
    return periodoTxt(d.comparisonStart, d.comparisonEnd) + (c.metrics.vs_previous.comparable ? '' : ' (sin registros)');
  });
  protected readonly rows = computed<Row[]>(() => {
    const m = this.content()?.metrics;
    if (!m) {
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

  // Misma razon que ReportViewComponent: la ruta reutiliza esta instancia al pasar de una consulta a otra.
  private readonly routeId = toSignal(this.route.paramMap.pipe(map((params) => params.get('id') ?? '')), {
    initialValue: this.route.snapshot.paramMap.get('id') ?? '',
  });

  constructor() {
    effect(() => {
      const id = this.routeId();
      this.reset();
      void this.load(id);
    });
  }

  private reset(): void {
    this.detail.set(null);
    this.missing.set(false);
    this.loadError.set(null);
    this.formError.set(null);
  }

  private async load(id: string): Promise<void> {
    try {
      const detail = await this.consultations.get(id);
      if (this.routeId() === id) {
        this.detail.set(detail);
      }
    } catch (e) {
      if (this.routeId() !== id) {
        return;
      }
      const status = (e as { status?: number }).status;
      if (status === 404) {
        this.missing.set(true);
      } else {
        this.loadError.set(apiFailure(e).message);
      }
    }
  }

  protected sources(claim: { claim_key: string }) {
    return this.detail()?.citations[claim.claim_key] ?? [];
  }

  protected missingSource(item: { kind: string; text: string; dates?: string[] }): string {
    if (item.kind === 'DIAS_SIN_REGISTRO' && item.dates?.length) {
      return item.dates.map((date) => `${dCorto(date)}: sin registro`).join(' / ');
    }
    return 'Registros guardados solo con el texto: sus síntomas no se contaron';
  }

  protected goTo(n: number): void {
    const target = document.getElementById('fuente-' + n);
    target?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    target?.focus?.();
  }

  protected async downloadPdf(button: ProcessButtonComponent): Promise<void> {
    const d = this.detail();
    if (!d) {
      return;
    }
    this.formError.set(null);
    await button.run(async () => {
      try {
        const file = await this.consultations.pdf(d.id);
        const url = URL.createObjectURL(file.blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = file.fileName ?? `consulta-${d.patientAlias}-${d.periodStart}.pdf`;
        link.click();
        URL.revokeObjectURL(url);
      } catch (e) {
        this.formError.set(apiFailure(e).message);
      }
    });
  }
}
