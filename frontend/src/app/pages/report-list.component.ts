import { Component, OnInit, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

import { apiFailure } from '../core/api-error';
import { bogotaDay, dFull, periodoTxt } from '../core/date-es';
import { IconComponent } from '../core/icon.component';
import { ReportSummary, ReportsService } from '../core/reports.service';
import { CellDirective, ListingComponent } from '../ui/listing.component';
import { REPORT_STATUS_LABELS, StatusChipComponent } from '../ui/status-chip.component';

// vInfLista() del mockup. «En revisión» son los pendientes; la otra lista es todo lo demás (`estado !== 'En revisión'` en
// el mockup): aprobados, y tambien rechazados e incompletos, que no tienen otra pantalla donde encontrarse.
@Component({
  selector: 'app-report-list',
  standalone: true,
  imports: [ListingComponent, CellDirective, StatusChipComponent, IconComponent],
  template: `
    @if (error(); as message) {
      <div class="aviso"><svg appIcon="rechazar"></svg><span>{{ message }}</span></div>
    }
    @if (reports(); as rows) {
      <app-listing [cols]="cols" [rows]="rows" placeholder="Busque por paciente o período" [searchText]="searchText" [href]="href"
                   [empty]="empty()">
        <ng-template appCell let-r let-col="col">
          @switch (col) {
            @case (0) { <b>{{ r.patientAlias }}</b> }
            @case (1) { {{ period(r) }} }
            @case (2) { {{ day(r.generatedAt) }} }
            @case (3) { <app-status-chip [name]="label(r.status)" [dense]="true" /> }
          }
        </ng-template>
      </app-listing>
    }
  `,
})
export class ReportListComponent implements OnInit {
  private readonly service = inject(ReportsService);
  private readonly view: string = inject(ActivatedRoute).snapshot.data['view'];

  protected readonly cols = ['Paciente', 'Período', 'Generado', 'Estado'];
  protected readonly reports = signal<ReportSummary[] | null>(null);
  protected readonly error = signal<string | null>(null);

  protected readonly searchText = (r: ReportSummary) => `${r.patientAlias} ${this.period(r)} ${this.label(r.status)}`;
  protected readonly href = (r: ReportSummary) => `/enf/informes/ver/${r.id}`;
  protected readonly label = (status: string) => REPORT_STATUS_LABELS[status] ?? status;
  protected readonly period = (r: ReportSummary) => (r.reportType === 'MENSUAL' ? 'Mensual: ' : '') + periodoTxt(r.periodStart, r.periodEnd);
  protected readonly day = (instant: string) => dFull(bogotaDay(instant));

  protected empty() {
    return {
      title: this.view === 'revision' ? 'No hay informes por revisar' : 'Aún no hay informes aprobados',
      text: 'Genere un informe y aparecerá aquí.',
      action: { text: 'Generar informe', path: '/enf/informes/generar' },
    };
  }

  async ngOnInit(): Promise<void> {
    try {
      const all = await this.service.list();
      this.reports.set(all.filter((r) => (this.view === 'revision') === (r.status === 'PENDIENTE_REVISION')));
    } catch (e) {
      this.error.set(apiFailure(e).message);
    }
  }
}
