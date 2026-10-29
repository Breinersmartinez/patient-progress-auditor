import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

export interface DemoUser {
  id: string;
  displayName: string;
  role: string;
}

export interface Session extends DemoUser {
  permissions: string[];
}

interface SystemStatus {
  status: string;
  database: string;
  today?: string;
}

const STORAGE_KEY = 'ppa.demoUser';

// Sesion de demostracion (D26): no hay inicio de sesion; se elige con que usuario sembrado se trabaja.
// El elegido se recuerda en el navegador y viaja en cada peticion (ver api.interceptor.ts).
@Injectable({ providedIn: 'root' })
export class SessionService {
  private readonly http = inject(HttpClient);

  readonly demoUsers = signal<DemoUser[]>([]);
  readonly session = signal<Session | null>(null);
  readonly currentId = signal<string | null>(null);
  // Fecha de hoy segun el servidor, en America/Bogota (F7): el navegador no decide que dia es.
  readonly today = signal<string | null>(null);
  readonly error = signal<string | null>(null);

  // Hasta que existan las pantallas del medico (E12c), "Ver como" solo ofrece enfermeras.
  readonly selectable = computed(() => this.demoUsers().filter((user) => user.role === 'ENFERMERIA'));

  async init(): Promise<void> {
    try {
      const status = await firstValueFrom(this.http.get<SystemStatus>('/api/system/status'));
      this.today.set(status.today ?? null);
      const users = await firstValueFrom(this.http.get<DemoUser[]>('/api/demo-users'));
      this.demoUsers.set(users);
      const stored = this.readStored();
      const choice = this.selectable().find((user) => user.id === stored) ?? this.selectable()[0];
      if (!choice) {
        this.error.set('No hay usuarios de demostración. Reinicie la aplicación con la semilla activa.');
        return;
      }
      await this.select(choice.id);
    } catch {
      this.error.set('No se pudo conectar con el servidor. Revise que la aplicación esté arriba e intente de nuevo.');
    }
  }

  async select(id: string): Promise<void> {
    this.currentId.set(id);
    const session = await firstValueFrom(this.http.get<Session>('/api/session'));
    this.session.set(session);
    this.error.set(null);
    this.store(id);
  }

  private readStored(): string | null {
    try {
      return localStorage.getItem(STORAGE_KEY);
    } catch {
      return null;
    }
  }

  private store(id: string): void {
    try {
      localStorage.setItem(STORAGE_KEY, id);
    } catch {
      // Sin almacenamiento (ventana privada): se pierde la preferencia, nada mas.
    }
  }
}
