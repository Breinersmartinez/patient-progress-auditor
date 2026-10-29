import { Component, input, signal } from '@angular/core';

import { IconComponent } from '../core/icon.component';
import { ICONOS_ANIMADOS } from '../core/icons';

// Equivale a btnProceso() y conProceso() del mockup (Regla 11): ignora clics mientras dura la accion y
// muestra el texto en gerundio. Un icono del catalogo se anima; con uno propio, el anillo toma su lugar.
@Component({
  selector: 'button[appProcessButton]',
  standalone: true,
  imports: [IconComponent],
  host: {
    class: 'btn',
    '[attr.type]': 'type()',
    '[attr.aria-busy]': 'busy() ? "true" : null',
    '[attr.aria-disabled]': 'busy() ? "true" : null',
  },
  template: `
    @if (!icon()) {
      <span class="btn__text btn__text--centro">
        <span class="btn__idle">{{ label() }}</span>
        <span class="btn__busy"><svg appIcon="anillo"></svg>{{ busyLabel() }}</span>
      </span>
    } @else if (animated()) {
      <svg [appIcon]="icon()"></svg>
      <span class="btn__text">
        <span class="btn__idle">{{ label() }}</span><span class="btn__busy">{{ busyLabel() }}</span>
      </span>
    } @else {
      <span class="btn__icon"><svg [appIcon]="icon()"></svg><svg appIcon="anillo"></svg></span>
      <span class="btn__text">
        <span class="btn__idle">{{ label() }}</span><span class="btn__busy">{{ busyLabel() }}</span>
      </span>
    }
  `,
})
export class ProcessButtonComponent {
  readonly label = input.required<string>();
  readonly busyLabel = input.required<string>();
  readonly icon = input<string>('');
  readonly type = input<'button' | 'submit'>('button');

  protected readonly busy = signal(false);

  protected animated(): boolean {
    return ICONOS_ANIMADOS.includes(this.icon());
  }

  // Envuelve cualquier accion que tarda; si ya hay una en curso, no hace nada.
  async run<T>(action: () => Promise<T>): Promise<T | undefined> {
    if (this.busy()) {
      return undefined;
    }
    this.busy.set(true);
    try {
      return await action();
    } finally {
      this.busy.set(false);
    }
  }
}
