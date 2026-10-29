import { Component, OnInit, inject, signal } from '@angular/core';

import { apiFailure } from '../core/api-error';
import { dCorto } from '../core/date-es';
import { Entry, EntriesService } from '../core/entries.service';
import { IconComponent } from '../core/icon.component';
import { symptomName } from '../core/symptoms';
import { CellDirective, ListingComponent } from '../ui/listing.component';

// vRegHistorial() del mockup: las entradas de todos los pacientes, la mas reciente primero.
@Component({
  selector: 'app-entry-history',
  standalone: true,
  imports: [ListingComponent, CellDirective, IconComponent],
  template: `
    @if (error(); as message) {
      <div class="aviso"><svg appIcon="rechazar"></svg><span>{{ message }}</span></div>
    }
    @if (entries(); as rows) {
      <app-listing [cols]="cols" [rows]="rows" placeholder="Busque por paciente, síntoma o texto del registro"
                   [searchText]="searchText" [href]="href"
                   [empty]="{ title: 'Aún no hay registros', text: 'Cree el primero para que aparezca aquí.',
                              action: { text: 'Nuevo registro', path: '/enf/registro/nuevo' } }">
        <ng-template appCell let-e let-col="col">
          @switch (col) {
            @case (0) { <span style="white-space: nowrap">{{ dCorto(e.entryDate) }}</span> }
            @case (1) { <b>{{ e.patientAlias }}</b> }
            @case (2) { {{ e.body }} }
            @case (3) {
              @if (e.symptoms.length) {
                {{ symptomNames(e) }}
              } @else if (e.structuringStatus === 'SIN_ESTRUCTURAR') {
                <span class="sec">Sin estructurar</span>
              } @else {
                <span class="sec">Ninguno</span>
              }
            }
          }
        </ng-template>
      </app-listing>
    }
  `,
})
export class EntryHistoryComponent implements OnInit {
  private readonly service = inject(EntriesService);

  protected readonly cols = ['Fecha', 'Paciente', 'Registro', 'Síntomas'];
  protected readonly entries = signal<Entry[] | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly dCorto = dCorto;

  protected readonly symptomNames = (e: Entry) => e.symptoms.map((s) => symptomName(s.symptom)).join(', ');
  protected readonly searchText = (e: Entry) =>
    `${e.patientAlias} ${e.body} ${e.entryDate} ${this.symptomNames(e)}`;
  protected readonly href = (e: Entry) => `/enf/pacientes/ficha/${e.patientId}`;

  async ngOnInit(): Promise<void> {
    try {
      this.entries.set(await this.service.history());
    } catch (e) {
      this.error.set(apiFailure(e).message);
    }
  }
}
