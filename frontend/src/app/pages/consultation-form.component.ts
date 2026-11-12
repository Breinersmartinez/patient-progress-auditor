import { Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

import { apiFailure } from '../core/api-error';
import { ConsultationsService } from '../core/consultations.service';
import { isoDe, sumar } from '../core/date-es';
import { IconComponent } from '../core/icon.component';
import { Patient, PatientsService } from '../core/patients.service';
import { Run, RunStep, RunsService } from '../core/runs.service';
import { ToastService } from '../core/toast.service';
import { ProcessButtonComponent } from '../ui/process-button.component';

type Kind = 'semana' | 'mes' | 'rango';
type Compare = 'no' | 'ant' | 'otro';

// «Armar informe» del mockup (vInfGenerar con rol 'med'): el médico arma, de sus propios registros, una consulta de
// un período cualquiera (D31, RF-46). Las reglas de fondo (tope de 31 días, fin antes de inicio, período futuro,
// comparación que se cruza, período sin registros) las valida el servidor (ConsultationService); aquí solo se
// completan los campos y se muestran los mensajes de campo que el servidor devuelve, junto al campo que falló.
@Component({
  selector: 'app-consultation-form',
  standalone: true,
  imports: [IconComponent, ProcessButtonComponent],
  template: `
    <div class="chat-pag">
      <p class="sec">Arme su propio informe de un paciente para el período que elija. Se hace con los registros del
        período y es solo para su consulta: no pasa por la revisión de enfermería ni se guarda en el historial.</p>
      <div class="chat-datos">
        <div class="campo">
          <label for="ig-pac">Paciente</label>
          <select class="inp" id="ig-pac" [disabled]="running()" (change)="patientId.set($any($event.target).value)">
            @for (p of patients(); track p.id) {
              <option [value]="p.id" [selected]="p.id === patientId()">{{ p.alias }}{{ p.bed ? ' (cama ' + p.bed + ')' : '' }}</option>
            }
          </select>
        </div>
        <div class="campo">
          <label for="ig-tipo">Período del informe</label>
          <select class="inp" id="ig-tipo" [disabled]="running()" (change)="kind.set($any($event.target).value)">
            <option value="semana" [selected]="kind() === 'semana'">Una semana</option>
            <option value="mes" [selected]="kind() === 'mes'">Un mes</option>
            <option value="rango" [selected]="kind() === 'rango'">Rango de fechas</option>
          </select>
        </div>
      </div>
      @if (kind() === 'semana') {
        <div class="campo" id="ig-semana">
          <label for="ig-sem">Lunes de la semana</label>
          <input class="inp" type="date" id="ig-sem" [disabled]="running()" [value]="weekStart()" [max]="hoy"
                 (input)="weekStart.set($any($event.target).value)">
        </div>
      } @else if (kind() === 'mes') {
        <div class="campo" id="ig-mes">
          <label for="ig-m">Mes</label>
          <input class="inp" type="month" id="ig-m" [disabled]="running()" [value]="month()" [max]="hoy.slice(0, 7)"
                 (input)="month.set($any($event.target).value)">
        </div>
      } @else {
        <div class="chat-datos" id="ig-rango">
          <div class="campo">
            <label for="ig-d">Desde</label>
            <input class="inp" type="date" id="ig-d" [disabled]="running()" [value]="rangeStart()" [max]="hoy" (input)="rangeStart.set($any($event.target).value)">
          </div>
          <div class="campo">
            <label for="ig-h">Hasta</label>
            <input class="inp" type="date" id="ig-h" [disabled]="running()" [value]="rangeEnd()" [max]="hoy" (input)="rangeEnd.set($any($event.target).value)">
          </div>
        </div>
      }
      <div class="campo">
        <label for="ig-cmp">Comparar con</label>
        <select class="inp" id="ig-cmp" [disabled]="running()" (change)="compare.set($any($event.target).value)">
          <option value="no" [selected]="compare() === 'no'">No comparar, solo este período</option>
          <option value="ant" [selected]="compare() === 'ant'">El período anterior (misma duración)</option>
          <option value="otro" [selected]="compare() === 'otro'">Otro período que yo elija</option>
        </select>
      </div>
      @if (compare() === 'otro') {
        <div class="chat-datos" id="ig-cmp-rango">
          <div class="campo">
            <label for="ig-cd">Comparar desde</label>
            <input class="inp" type="date" id="ig-cd" [disabled]="running()" [value]="comparisonStart()" [max]="hoy" (input)="comparisonStart.set($any($event.target).value)">
          </div>
          <div class="campo">
            <label for="ig-ch">Comparar hasta</label>
            <input class="inp" type="date" id="ig-ch" [disabled]="running()" [value]="comparisonEnd()" [max]="hoy" (input)="comparisonEnd.set($any($event.target).value)">
          </div>
        </div>
      }
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
export class ConsultationFormComponent implements OnInit, OnDestroy {
  private readonly router = inject(Router);
  private readonly patientsService = inject(PatientsService);
  private readonly consultations = inject(ConsultationsService);
  private readonly runs = inject(RunsService);
  private readonly toast = inject(ToastService);

  protected readonly hoy = isoDe(new Date());
  protected readonly patients = signal<Patient[]>([]);
  protected readonly patientId = signal('');
  protected readonly kind = signal<Kind>('semana');
  protected readonly weekStart = signal(sumar(this.hoy, -7));
  protected readonly month = signal(this.hoy.slice(0, 7));
  protected readonly rangeStart = signal(sumar(this.hoy, -6));
  protected readonly rangeEnd = signal(this.hoy);
  protected readonly compare = signal<Compare>('no');
  protected readonly comparisonStart = signal('');
  protected readonly comparisonEnd = signal('');
  protected readonly error = signal('');
  protected readonly steps = signal<RunStep[]>([]);
  protected readonly failure = signal<string | null>(null);
  protected readonly running = signal(false);
  private stopFollowing: (() => void) | null = null;

  async ngOnInit(): Promise<void> {
    try {
      const list = await this.patientsService.list();
      this.patients.set(list);
      this.patientId.set(list[0]?.id ?? '');
    } catch (e) {
      this.toast.show(apiFailure(e).message, false);
    }
  }

  ngOnDestroy(): void {
    this.stopFollowing?.();
  }

  // El período que se arma con lo elegido; null con el mensaje si falta algo (periodo() del mockup, sin el tope de
  // un año: el tope real lo exige el servidor y su mensaje llega por 400).
  private period(): { start: string; end: string } | null {
    if (this.kind() === 'semana') {
      if (!this.weekStart()) {
        this.error.set('Elija la semana.');
        return null;
      }
      return { start: this.weekStart(), end: sumar(this.weekStart(), 6) };
    }
    if (this.kind() === 'mes') {
      if (!this.month()) {
        this.error.set('Elija el mes.');
        return null;
      }
      const [y, m] = this.month().split('-').map(Number);
      const first = `${y}-${String(m).padStart(2, '0')}-01`;
      const lastOfMonth = isoDe(new Date(y, m, 0));
      return { start: first, end: lastOfMonth > this.hoy ? this.hoy : lastOfMonth };
    }
    if (!this.rangeStart() || !this.rangeEnd()) {
      this.error.set('Elija las dos fechas.');
      return null;
    }
    if (this.rangeStart() > this.rangeEnd()) {
      this.error.set('La fecha inicial no puede ser posterior a la final.');
      return null;
    }
    return { start: this.rangeStart(), end: this.rangeEnd() };
  }

  // La comparacion() del mockup: «anterior» se calcula del lado del cliente (misma duración, justo antes).
  private comparison(period: { start: string; end: string }): { start?: string; end?: string } | null {
    if (this.compare() === 'no') {
      return {};
    }
    if (this.compare() === 'ant') {
      const days = (new Date(period.end).getTime() - new Date(period.start).getTime()) / 86_400_000 + 1;
      const end = sumar(period.start, -1);
      const start = sumar(end, -(days - 1));
      return { start, end };
    }
    if (!this.comparisonStart() || !this.comparisonEnd()) {
      this.error.set('Elija las dos fechas del período de comparación.');
      return null;
    }
    if (this.comparisonStart() > this.comparisonEnd()) {
      this.error.set('En la comparación, la fecha inicial no puede ser posterior a la final.');
      return null;
    }
    return { start: this.comparisonStart(), end: this.comparisonEnd() };
  }

  protected async start(button: ProcessButtonComponent): Promise<void> {
    this.error.set('');
    this.failure.set(null);
    if (!this.patientId()) {
      this.error.set('Elija el paciente.');
      return;
    }
    const period = this.period();
    if (!period) {
      return;
    }
    const comparison = this.comparison(period);
    if (!comparison) {
      return;
    }
    await button.run(async () => {
      this.steps.set([]);
      this.running.set(true);
      try {
        const run = await this.consultations.request(this.patientId(), period.start, period.end, comparison.start, comparison.end);
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
        const failure = apiFailure(e);
        this.error.set(failure.message);
      } finally {
        this.running.set(false);
        this.stopFollowing = null;
      }
    });
  }

  private finished(run: Run): void {
    if (run.consultationId) {
      void this.router.navigate(['/med/informes/consulta', run.consultationId]);
      return;
    }
    this.failure.set(run.message ?? 'La ejecución no terminó. Intente de nuevo en unos minutos.');
  }
}
