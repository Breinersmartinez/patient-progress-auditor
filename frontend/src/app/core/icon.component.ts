import { Component, computed, inject, input } from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';

import { ICONOS } from './icons';

// Equivale a icono() del mockup: el <svg> es el propio elemento, asi la estructura del DOM no cambia.
@Component({
  selector: 'svg[appIcon]',
  standalone: true,
  template: '',
  host: {
    '[class]': 'classes()',
    '[innerHTML]': 'markup()',
    viewBox: '0 0 24 24',
    'aria-hidden': 'true',
  },
})
export class IconComponent {
  readonly name = input.required<string>({ alias: 'appIcon' });
  private readonly sanitizer = inject(DomSanitizer);

  protected readonly classes = computed(() => `icon i-${this.name()}`);
  // El catalogo es una constante del propio codigo, no entrada de la persona: se marca como confiable.
  protected readonly markup = computed(() => {
    const name = this.name();
    if (!(name in ICONOS)) {
      console.warn('Falta el icono', name);
    }
    return this.sanitizer.bypassSecurityTrustHtml(ICONOS[name] ?? '');
  });
}
