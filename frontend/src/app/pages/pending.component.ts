import { Component } from '@angular/core';

import { EmptyStateComponent } from '../ui/empty-state.component';

// Marcador temporal de las pantallas que se construyen en etapas siguientes (E6 en adelante).
// Cada ruta lo reemplaza por su componente real; no es parte del diseno final.
@Component({
  selector: 'app-pending',
  standalone: true,
  imports: [EmptyStateComponent],
  template: `<app-empty-state title="Esta pantalla aún no está lista" text="Se construye en una etapa siguiente de la entrega." />`,
})
export class PendingComponent {}
