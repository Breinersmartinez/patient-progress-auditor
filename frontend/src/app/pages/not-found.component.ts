import { Component, computed, inject } from '@angular/core';

import { roleHome } from '../core/roles';
import { SessionService } from '../core/session.service';
import { EmptyStateComponent } from '../ui/empty-state.component';

// noEncontrado() del mockup: «Volver al inicio» lleva al inicio del rol de la sesión, no siempre a enfermería.
@Component({
  selector: 'app-not-found',
  standalone: true,
  imports: [EmptyStateComponent],
  template: `
    <app-empty-state title="No encontramos lo que busca"
                     text="Puede que haya cambiado o que su rol no tenga acceso."
                     [action]="{ text: 'Volver al inicio', path: home() }" />
  `,
})
export class NotFoundComponent {
  private readonly session = inject(SessionService);
  protected readonly home = computed(() => roleHome(this.session.session()?.role));
}
