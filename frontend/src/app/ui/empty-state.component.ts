import { Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { IconComponent } from '../core/icon.component';

export interface EmptyAction {
  text: string;
  path: string;
}

// Equivale a vacioHTML() del mockup.
@Component({
  selector: 'app-empty-state',
  standalone: true,
  imports: [IconComponent, RouterLink],
  host: { style: 'display: contents' },
  template: `
    <div class="vacio">
      <svg appIcon="buscar"></svg>
      <h3>{{ title() }}</h3>
      <p>{{ text() }}</p>
      @if (action(); as action) {
        <a class="btn btn--primary" [routerLink]="action.path">{{ action.text }}</a>
      }
    </div>
  `,
})
export class EmptyStateComponent {
  readonly title = input.required<string>();
  readonly text = input.required<string>();
  readonly action = input<EmptyAction | null>(null);
}
