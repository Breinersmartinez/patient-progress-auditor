import { Component, computed, input } from '@angular/core';

// Equivale a estado() del mockup. "Incompleto" usa el rojo que el mockup ya trae para "Falló" (tabla 3.3).
const COLOR_ESTADO: Record<string, string> = {
  'En revisión': '#7F77DD',
  Aprobado: '#1D9E75',
  Incompleto: '#E24B4A',
  Rechazado: '#E24B4A',
};

// Estados del informe en la API y como se muestran (RF-23).
export const REPORT_STATUS_LABELS: Record<string, string> = {
  PENDIENTE_REVISION: 'En revisión',
  APROBADO: 'Aprobado',
  INCOMPLETO: 'Incompleto',
  RECHAZADO: 'Rechazado',
};

@Component({
  selector: 'app-status-chip',
  standalone: true,
  host: { style: 'display: contents' },
  template: `<span class="est" [class.est--d]="dense()"><i class="dot" [style.background]="color()"></i>{{ name() }}</span>`,
})
export class StatusChipComponent {
  readonly name = input.required<string>();
  readonly dense = input(false);
  protected readonly color = computed(() => COLOR_ESTADO[this.name()] ?? '#888780');
}
