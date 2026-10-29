import { Component } from '@angular/core';

import { EmptyStateComponent } from '../ui/empty-state.component';

// noEncontrado() del mockup.
@Component({
  selector: 'app-not-found',
  standalone: true,
  imports: [EmptyStateComponent],
  template: `
    <app-empty-state title="No encontramos lo que busca"
                     text="Puede que haya cambiado o que su rol no tenga acceso."
                     [action]="{ text: 'Volver al inicio', path: '/enf/pacientes' }" />
  `,
})
export class NotFoundComponent {}
