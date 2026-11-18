import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

export interface LastReport {
  id: string;
  type: 'SEMANAL' | 'MENSUAL';
  periodStart: string;
  periodEnd: string;
  status: string;
}

// Alias, cama, edad e ingreso son de la pantalla: nunca salen al proveedor del LLM (RNF-02).
export interface Patient {
  id: string;
  alias: string;
  bed?: string;
  age?: number;
  admittedOn?: string;
  lastEntryDate?: string;
  lastReport?: LastReport;
  // El punto de "Mis pacientes" (D30, RF-45): el servidor lo calcula; para enfermeria siempre llega en falso.
  // Opcional aqui porque las pruebas existentes construyen un `Patient` sin este campo.
  hasUnreadNotifications?: boolean;
}

export interface NewPatient {
  alias: string;
  bed: string;
  age: number | null;
  admittedOn: string | null;
}

@Injectable({ providedIn: 'root' })
export class PatientsService {
  private readonly http = inject(HttpClient);

  list(): Promise<Patient[]> {
    return firstValueFrom(this.http.get<Patient[]>('/api/patients'));
  }

  get(id: string): Promise<Patient> {
    return firstValueFrom(this.http.get<Patient>(`/api/patients/${encodeURIComponent(id)}`));
  }

  create(patient: NewPatient): Promise<Patient> {
    return firstValueFrom(this.http.post<Patient>('/api/patients', patient));
  }
}
