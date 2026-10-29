import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { apiFailure } from '../core/api-error';
import { rango } from '../core/date-es';
import { IconComponent } from '../core/icon.component';
import { LlmStatus, LlmStatusService } from '../core/llm-status.service';
import { Patient, PatientsService } from '../core/patients.service';
import { ReportsService, WeekOption } from '../core/reports.service';
import { Run, RunStep, RunsService } from '../core/runs.service';
import { ToastService } from '../core/toast.service';
import { ProcessButtonComponent } from '../ui/process-button.component';

// vInfGenerar() del mockup, sin el mundo simulado: pide la ejecucion del agente (D24) y muestra sus pasos reales por SSE.
// Diferencias con el mockup (tabla 3.3): solo semanas completas, sin rango de fechas ni «Comparar con».
@Component({
  selector: 'app-report-generate',
  standalone: true,
  imports: [IconComponent, ProcessButtonComponent],
  template: `
    @if (llm()?.warning; as warning) {
      <div class="aviso"><svg appIcon="ia"></svg><span>{{ warning }}</span></div>
    }
    <div class="chat-pag">
      <p class="sec">Elija el paciente y el período. El informe habla solo de ese período.</p>
      <div class="chat-datos">
        <div class="campo">
          <label for="ig-pac">Paciente</label>
          <select class="inp" id="ig-pac" [disabled]="running()" (change)="choosePatient($any($event.target).value)">
            @for (p of patients(); track p.id) {
              <option [value]="p.id" [selected]="p.id === patientId()">{{ p.alias }}{{ p.bed ? ' (cama ' + p.bed + ')' : '' }}</option>
            }
          </select>
        </div>
        <div class="campo">
          <label for="ig-tipo">Período del informe</label>
          <select class="inp" id="ig-tipo" [disabled]="running()">
            <option value="semana">Una semana</option>
          </select>
        </div>
      </div>
      <div class="campo" id="ig-semana">
        <label for="ig-sem">Semana</label>
        <select class="inp" id="ig-sem" [disabled]="running()" (change)="weekStart.set($any($event.target).value)">
          @for (w of weeks(); track w.weekStart) {
            <option [value]="w.weekStart" [selected]="w.weekStart === weekStart()">Del {{ range(w.weekStart) }}{{ w.reportId ? ' (ya tiene informe)' : w.runId ? ' (se está generando)' : '' }}</option>
          }
        </select>
      </div>
      <p class="sec">Se compara con la semana anterior; el agente decide cuántas semanas más atrás revisar.</p>
      <span class="error" id="ig-err">{{ error() }}</span>
      <div>
        <button appProcessButton #generate="processButton" label="Generar con IA" busyLabel="Generando" icon="ia" id="ig-btn"
                class="btn--primary" (click)="start(generate)"></button>
      </div>
      @if (steps().length || failure()) {
        <div class="aviso" role="status">
          <svg appIcon="ia"></svg>
          <span>{{ failure() ?? 'El agente está trabajando. Estos son sus pasos, tal como ocurren.' }}</span>
        </div>
        <div class="pasos" aria-live="polite">
          @for (step of steps(); track step.id) {
            <div><span class="est est--d"><i class="dot" [style.background]="step.error ? '#E24B4A' : '#7F77DD'"></i>{{ step.message }}</span></div>
          }
        </div>
      }
    </div>
  `,
})
export class ReportGenerateComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly patientsService = inject(PatientsService);
  private readonly reports = inject(ReportsService);
  private readonly runs = inject(RunsService);
  private readonly llmStatus = inject(LlmStatusService);
  private readonly toast = inject(ToastService);

  protected readonly range = rango;
  protected readonly patients = signal<Patient[]>([]);
  protected readonly patientId = signal('');
  protected readonly weeks = signal<WeekOption[]>([]);
  protected readonly weekStart = signal('');
  protected readonly llm = signal<LlmStatus | null>(null);
  protected readonly error = signal('');
  protected readonly steps = signal<RunStep[]>([]);
  // Por que la ejecucion no termino bien (un aviso, no un codigo): el proveedor cayo, se agoto un limite...
  protected readonly failure = signal<string | null>(null);
  protected readonly running = signal(false);
  private stopFollowing: (() => void) | null = null;

  async ngOnInit(): Promise<void> {
    this.llmStatus.get().then((status) => this.llm.set(status)).catch(() => undefined);
    try {
      const list = await this.patientsService.list();
      this.patients.set(list);
      const wanted = this.route.snapshot.paramMap.get('id');
      this.patientId.set(list.find((p) => p.id === wanted)?.id ?? list[0]?.id ?? '');
      await this.loadWeeks();
    } catch (e) {
      this.toast.show(apiFailure(e).message, false);
    }
  }

  ngOnDestroy(): void {
    this.stopFollowing?.();
  }

  protected async choosePatient(id: string): Promise<void> {
    this.patientId.set(id);
    this.error.set('');
    await this.loadWeeks();
  }

  private async loadWeeks(): Promise<void> {
    if (!this.patientId()) {
      return;
    }
    try {
      const weeks = await this.reports.weeks(this.patientId());
      this.weeks.set(weeks);
      this.weekStart.set((weeks.find((w) => w.canGenerate) ?? weeks[0])?.weekStart ?? '');
    } catch (e) {
      this.weeks.set([]);
      this.weekStart.set('');
      this.error.set(apiFailure(e).message);
    }
  }

  protected async start(button: ProcessButtonComponent): Promise<void> {
    this.error.set('');
    this.failure.set(null);
    const week = this.weeks().find((w) => w.weekStart === this.weekStart());
    if (!week) {
      this.error.set('Ese paciente no tiene registros en una semana completa, no hay con qué generar el informe.');
      return;
    }
    if (week.reportId) {
      this.toast.show('Ya existe un informe igual');
      void this.router.navigate(['/enf/informes/ver', week.reportId]);
      return;
    }
    await button.run(async () => {
      this.steps.set([]);
      this.running.set(true);
      try {
        // Si ya se esta generando, se engancha a esa ejecucion en lugar de pedir otra (el servidor tambien lo hace).
        const run = week.runId ? await this.runs.get(week.runId) : (await this.runs.request(this.patientId(), week.weekStart)).run;
        await new Promise<void>((resolve) => {
          this.stopFollowing = this.runs.follow(run.id, {
            onStep: (step) => this.steps.update((list) => [...list, step]),
            onEnd: (final) => {
              this.finished(final);
              resolve();
            },
          });
        });
      } catch (e) {
        this.error.set(apiFailure(e).message);
      } finally {
        this.running.set(false);
        this.stopFollowing = null;
      }
    });
  }

  private finished(run: Run): void {
    if (run.reportId) {
      if (run.status === 'INCOMPLETA') {
        this.toast.show('El informe quedó incompleto: revise el motivo', false);
      }
      void this.router.navigate(['/enf/informes/ver', run.reportId]);
      return;
    }
    this.failure.set(run.message ?? 'La ejecución no terminó. Intente de nuevo en unos minutos.');
  }
}
