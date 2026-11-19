import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { AuthService } from './auth.service';
import { rolePrefix } from './roles';

export interface Session {
  id: string | null;
  displayName: string;
  role: string;
  permissions: string[];
}

interface SystemStatus {
  status: string;
  database: string;
  today?: string;
}

// Sesion de la persona que inicio sesion en Keycloak. El servidor devuelve su nombre, rol y permisos (GET /api/session).
// Cada rol tiene sus pantallas (`enf`, `med`, `adm`).
@Injectable({ providedIn: 'root' })
export class SessionService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  readonly session = signal<Session | null>(null);
  // Fecha de hoy segun el servidor, en America/Bogota (F7): el navegador no decide que dia es.
  readonly today = signal<string | null>(null);
  readonly error = signal<string | null>(null);
  // Los tres roles tienen pantallas: enfermería y médico las clínicas, administración la gestión de usuarios (RF-33).
  readonly hasScreens = computed(() => ['ENFERMERIA', 'MEDICO', 'ADMIN'].includes(this.session()?.role ?? ''));

  // El prefijo de ruta del rol de la sesión actual (`enf` o `med`), para los enlaces internos de las pantallas compartidas.
  readonly prefix = computed(() => rolePrefix(this.session()?.role));

  async init(): Promise<void> {
    try {
      await this.auth.init();
    } catch {
      this.error.set('No se pudo conectar con el inicio de sesión. Revise que la aplicación esté arriba e intente de nuevo.');
      return;
    }
    try {
      const status = await firstValueFrom(this.http.get<SystemStatus>('/api/system/status'));
      this.today.set(status.today ?? null);
      this.session.set(await firstValueFrom(this.http.get<Session>('/api/session')));
    } catch (failure) {
      const status = (failure as { status?: number }).status;
      this.error.set(
        status === 403
          ? 'Su usuario no está registrado en el sistema. Pida a administración que lo registre.'
          : 'No se pudo conectar con el servidor. Revise que la aplicación esté arriba e intente de nuevo.',
      );
    }
  }

  logout(): Promise<void> {
    return this.auth.logout();
  }
}
