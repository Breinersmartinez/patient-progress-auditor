import { Injectable, signal } from '@angular/core';

// El titulo del encabezado: lo fija la ruta (data.title) y una pantalla puede cambiarlo cuando depende de un dato.
@Injectable({ providedIn: 'root' })
export class PageTitleService {
  readonly title = signal('');

  set(title: string): void {
    this.title.set(title);
  }
}
