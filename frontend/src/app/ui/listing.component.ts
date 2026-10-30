import { NgTemplateOutlet } from '@angular/common';
import { Component, ContentChild, Directive, TemplateRef, computed, inject, input, signal } from '@angular/core';
import { Router } from '@angular/router';

import { EmptyAction, EmptyStateComponent } from './empty-state.component';

// Plantilla de celda: <ng-template appCell let-row let-col="col">…</ng-template>
@Directive({ selector: 'ng-template[appCell]', standalone: true })
export class CellDirective {
  constructor(public readonly template: TemplateRef<{ $implicit: unknown; col: number }>) {}
}

export interface ListingEmpty {
  title: string;
  text: string;
  action?: EmptyAction;
}

// Equivale a listado() del mockup: busqueda, "N resultados", tabla que se vuelve tarjetas bajo 700 px (con
// data-label), estado vacio y "Sin resultados".
@Component({
  selector: 'app-listing',
  standalone: true,
  imports: [NgTemplateOutlet, EmptyStateComponent],
  host: { style: 'display: contents' },
  template: `
    <div class="barra">
      <input class="inp" type="search" [placeholder]="placeholder()" [attr.aria-label]="placeholder()"
             (input)="query.set($any($event.target).value)">
      <span class="total">{{ total() }}</span>
    </div>
    <div class="cont">
      <div class="tabla-wrap">
        @if (filtered().length === 0) {
          @if (rows().length) {
            <app-empty-state title="Sin resultados" text="Ningún registro coincide con la búsqueda. Pruebe con otro texto." />
          } @else {
            <app-empty-state [title]="empty().title" [text]="empty().text" [action]="empty().action ?? null" />
          }
        } @else {
          <table class="tabla">
            <thead>
              <tr>@for (col of cols(); track col) { <th scope="col">{{ col }}</th> }</tr>
            </thead>
            <tbody>
              @for (row of filtered(); track $index) {
                <tr [attr.data-href]="href()(row)" (click)="open(row)">
                  @for (col of cols(); track col; let i = $index) {
                    <td [attr.data-label]="col">
                      <ng-container *ngTemplateOutlet="cell.template; context: { $implicit: row, col: i }" />
                    </td>
                  }
                </tr>
              }
            </tbody>
          </table>
        }
      </div>
    </div>
  `,
})
export class ListingComponent<T> {
  readonly cols = input.required<string[]>();
  readonly rows = input.required<T[]>();
  readonly placeholder = input.required<string>();
  readonly empty = input.required<ListingEmpty>();
  // Texto sobre el que busca la persona en cada fila.
  readonly searchText = input.required<(row: T) => string>();
  // Ruta (sin '#') a la que lleva la fila al hacer clic; null si no es navegable.
  readonly href = input<(row: T) => string | null>(() => null);

  @ContentChild(CellDirective, { static: true }) cell!: CellDirective;

  private readonly router = inject(Router);
  protected readonly query = signal('');

  protected readonly filtered = computed(() => {
    const q = this.query().trim().toLowerCase();
    return this.rows().filter((row) => !q || this.searchText()(row).toLowerCase().includes(q));
  });

  protected readonly total = computed(() => {
    const n = this.filtered().length;
    return `${n} ${n === 1 ? 'resultado' : 'resultados'}`;
  });

  protected open(row: T): void {
    const target = this.href()(row);
    if (target) {
      void this.router.navigateByUrl(target);
    }
  }
}
