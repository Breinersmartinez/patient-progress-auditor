import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { apiFailure } from '../core/api-error';
import { dCorto } from '../core/date-es';
import { IconComponent } from '../core/icon.component';
import { Patient, PatientsService } from '../core/patients.service';
import { SessionService } from '../core/session.service';
import { CellDirective, ListingComponent } from '../ui/listing.component';
import { REPORT_STATUS_LABELS, StatusChipComponent } from '../ui/status-chip.component';

// vPacLista() del mockup, con el boton "Nuevo paciente" en .cab como el "Nuevo usuario" de vUsuLista (3.4).
// El medico no tiene PATIENTS_CREATE (V4): el boton es solo de quien puede usarlo.
@Component({
  selector: 'app-patients-list',
  standalone: true,
  imports: [ListingComponent, CellDirective, StatusChipComponent, IconComponent, RouterLink],
  template: `
    <div class="cab">
      <p class="sec">Pacientes registrados en el servicio.</p>
      @if (canCreate()) {
        <a class="btn btn--primary" routerLink="/enf/pacientes/nuevo"><svg appIcon="usuarios"></svg>Nuevo paciente</a>
      }
    </div>
    @if (error(); as message) {
      <div class="aviso"><svg appIcon="rechazar"></svg><span>{{ message }}</span></div>
    }
    @if (patients(); as rows) {
      <app-listing [cols]="cols" [rows]="rows" placeholder="Busque por código de paciente o cama"
                   [searchText]="searchText" [href]="href"
                   [empty]="{ title: 'Aún no hay pacientes', text: 'Cuando se registre un paciente aparecerá aquí.' }">
        <ng-template appCell let-p let-col="col">
          @switch (col) {
            @case (0) {
              <b>{{ p.alias }}</b>
              @if (isDoctor() && p.hasUnreadNotifications) {
                <i class="punto-lista"></i><span class="solo-lector"> Registros nuevos</span>
              }
            }
            @case (1) { @if (p.bed) { {{ p.bed }} } @else { <span class="sec">Sin dato</span> } }
            @case (2) { @if (p.age !== undefined) { {{ p.age }} años } @else { <span class="sec">Sin dato</span> } }
            @case (3) { {{ p.lastEntryDate ? dCorto(p.lastEntryDate) : 'Sin registros' }} }
            @case (4) {
              @if (p.lastReport) {
                <app-status-chip [name]="statusLabel(p.lastReport.status)" [dense]="true" />
              } @else {
                <span class="sec">Sin informes</span>
              }
            }
          }
        </ng-template>
      </app-listing>
    }
  `,
})
export class PatientsListComponent implements OnInit {
  private readonly service = inject(PatientsService);
  private readonly session = inject(SessionService);

  protected readonly cols = ['Paciente', 'Cama', 'Edad', 'Último registro', 'Último informe'];
  protected readonly patients = signal<Patient[] | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly dCorto = dCorto;
  protected readonly canCreate = computed(() => this.session.session()?.permissions.includes('PATIENTS_CREATE') ?? false);
  // El punto de "Mis pacientes" (D30, RF-45) es solo del medico, como en el mockup.
  protected readonly isDoctor = computed(() => this.session.session()?.role === 'MEDICO');

  protected readonly searchText = (p: Patient) => `${p.alias} ${p.bed ?? ''}`;
  protected readonly href = (p: Patient) => `/${this.session.prefix()}/pacientes/ficha/${p.id}`;
  protected readonly statusLabel = (status: string) => REPORT_STATUS_LABELS[status] ?? status;

  async ngOnInit(): Promise<void> {
    try {
      this.patients.set(await this.service.list());
    } catch (e) {
      this.error.set(apiFailure(e).message);
    }
  }
}
