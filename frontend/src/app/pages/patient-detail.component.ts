import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { apiFailure } from '../core/api-error';
import { dFull, periodoTxt, sumar } from '../core/date-es';
import { Entry, EntriesService } from '../core/entries.service';
import { IconComponent } from '../core/icon.component';
import { PageTitleService } from '../core/page-title.service';
import { Patient, PatientsService } from '../core/patients.service';
import { SessionService } from '../core/session.service';
import { EmptyStateComponent } from '../ui/empty-state.component';
import { EntryCardComponent } from '../ui/entry-card.component';
import { REPORT_STATUS_LABELS, StatusChipComponent } from '../ui/status-chip.component';
import { NotFoundComponent } from './not-found.component';

// vPacFicha() del mockup: datos del paciente, ultimo informe y los siete registros mas recientes. El medico no
// tiene REPORTS_REQUEST (V4): no ve "Generar informe"; "Nuevo registro" es de los dos roles (D12, 2026-11-05),
// aunque el mockup no se lo daba al medico.
@Component({
  selector: 'app-patient-detail',
  standalone: true,
  imports: [RouterLink, IconComponent, StatusChipComponent, EntryCardComponent, EmptyStateComponent, NotFoundComponent],
  template: `
    @if (error(); as message) {
      <div class="aviso"><svg appIcon="rechazar"></svg><span>{{ message }}</span></div>
    }
    @if (missing()) {
      <app-not-found />
    }
    @if (patient(); as p) {
      <div class="cab">
        <div>
          <h2>Paciente {{ p.alias }}</h2>
          @if (summary(); as line) { <p class="sec">{{ line }}</p> }
        </div>
        <div class="acc">
          @if (canRequest()) {
            <a class="btn btn--sec" [routerLink]="['/' + prefix(), 'informes', 'generar', p.id]"><svg appIcon="ia"></svg>Generar informe</a>
          }
          <a class="btn btn--primary" [routerLink]="['/' + prefix(), 'registro', 'nuevo', p.id]"><svg appIcon="registro"></svg>Nuevo registro</a>
        </div>
      </div>
      @if (p.lastReport; as report) {
        <section>
          <h3 class="sec" style="font-size:16px;margin-bottom:12px">{{ isDoctor() ? 'Último informe recibido' : 'Último informe' }}</h3>
          <a class="card card--nav" [routerLink]="['/' + prefix(), 'informes', 'ver', report.id]">
            <b>{{ reportPeriod(report.periodStart, report.periodEnd) }}</b>
            <p><app-status-chip [name]="statusLabel(report.status)" /></p>
          </a>
        </section>
      }
      <section class="bloque">
        <h3>Registros recientes</h3>
        @if (entries(); as list) {
          @if (list.length) {
            <div class="grid">
              @for (entry of list; track entry.id) { <app-entry-card [entry]="entry" /> }
            </div>
          } @else if (isDoctor()) {
            <app-empty-state title="Sin registros todavía" text="Cuando enfermería guarde el primer registro aparecerá aquí." />
          } @else {
            <app-empty-state title="Sin registros todavía"
                             text="Cuando enfermería guarde el primer registro aparecerá aquí."
                             [action]="{ text: 'Crear el primer registro', path: '/enf/registro/nuevo/' + p.id }" />
          }
        }
      </section>
    }
  `,
})
export class PatientDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly patients = inject(PatientsService);
  private readonly entriesService = inject(EntriesService);
  private readonly title = inject(PageTitleService);
  private readonly session = inject(SessionService);

  protected readonly prefix = this.session.prefix;
  protected readonly isDoctor = computed(() => this.session.session()?.role === 'MEDICO');
  protected readonly canRequest = computed(() => this.session.session()?.permissions.includes('REPORTS_REQUEST') ?? false);

  protected readonly patient = signal<Patient | null>(null);
  protected readonly entries = signal<Entry[] | null>(null);
  protected readonly missing = signal(false);
  protected readonly error = signal<string | null>(null);

  // "Cama 204-A, 71 años, ingreso el 5 de octubre de 2026": solo con los datos que existen.
  protected readonly summary = computed(() => {
    const p = this.patient();
    if (!p) {
      return '';
    }
    return [
      p.bed ? `Cama ${p.bed}` : '',
      p.age !== undefined ? `${p.age} años` : '',
      p.admittedOn ? `ingreso el ${dFull(p.admittedOn)}` : '',
    ]
      .filter(Boolean)
      .join(', ');
  });

  protected readonly statusLabel = (status: string) => REPORT_STATUS_LABELS[status] ?? status;
  protected readonly reportPeriod = (from: string, to: string) =>
    `${to === sumar(from, 6) ? 'Semana' : 'Período'} del ${periodoTxt(from, to)}`;

  async ngOnInit(): Promise<void> {
    const id = this.route.snapshot.paramMap.get('id') ?? '';
    try {
      const patient = await this.patients.get(id);
      this.patient.set(patient);
      this.title.set(`Paciente ${patient.alias}`);
      this.entries.set(await this.entriesService.forPatient(id, { order: 'desc', limit: 7 }));
    } catch (e) {
      // Un paciente que no existe (o un id mal formado) es "No encontramos lo que busca", como el mockup.
      if (this.patient() === null && e instanceof HttpErrorResponse && (e.status === 404 || e.status === 400)) {
        this.missing.set(true);
      } else {
        this.error.set(apiFailure(e).message);
      }
    }
  }
}
