import { Component, computed, input } from '@angular/core';

import { dLargo } from '../core/date-es';
import { Entry } from '../core/entries.service';
import { symptomText } from '../core/symptoms';

// regCard() del mockup: un registro con su fecha, su texto y sus sintomas confirmados.
@Component({
  selector: 'app-entry-card',
  standalone: true,
  host: { style: 'display: contents' },
  template: `
    <article class="card">
      <b>{{ date() }}</b>
      <p>{{ entry().body }}</p>
      @if (symptoms().length) {
        <ul class="sint">
          @for (text of symptoms(); track $index) { <li>{{ text }}</li> }
        </ul>
      } @else if (entry().structuringStatus === 'SIN_ESTRUCTURAR') {
        <p class="sec">Sin estructurar: solo se guardó el texto.</p>
      } @else {
        <p class="sec">Sin síntomas registrados.</p>
      }
    </article>
  `,
})
export class EntryCardComponent {
  readonly entry = input.required<Entry>();

  protected readonly date = computed(() => dLargo(this.entry().entryDate));
  protected readonly symptoms = computed(() => this.entry().symptoms.map(symptomText));
}
