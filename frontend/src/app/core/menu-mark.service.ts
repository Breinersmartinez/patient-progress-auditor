import { Injectable, signal } from '@angular/core';

// La opcion del menu que marca una pantalla cuando depende de los datos y no de la ruta (marcar del mockup): un
// informe aprobado marca «Aprobados» y uno en revision, «En revisión». Se limpia al salir de la pantalla.
@Injectable({ providedIn: 'root' })
export class MenuMarkService {
  readonly path = signal<string | null>(null);
}
