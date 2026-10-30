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
