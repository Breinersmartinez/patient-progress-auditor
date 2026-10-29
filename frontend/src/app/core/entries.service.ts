import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { EntrySymptom } from './symptoms';

export interface Entry {
  id: string;
  patientId: string;
  patientAlias: string;
  entryDate: string;
  // El texto tal como se escribio: la fuente que citan los informes (RN-05, RN-07).
  body: string;
  authorName: string;
  structuringStatus: 'ESTRUCTURADA' | 'SIN_ESTRUCTURAR';
  createdAt: string;
  symptoms: EntrySymptom[];
}

export interface EntryQuery {
  from?: string;
  to?: string;
  order?: 'asc' | 'desc';
  limit?: number;
}

@Injectable({ providedIn: 'root' })
export class EntriesService {
  private readonly http = inject(HttpClient);

  forPatient(patientId: string, query: EntryQuery = {}): Promise<Entry[]> {
    let params = new HttpParams();
    for (const [key, value] of Object.entries(query)) {
      if (value !== undefined) {
        params = params.set(key, String(value));
      }
    }
    return firstValueFrom(this.http.get<Entry[]>(`/api/patients/${encodeURIComponent(patientId)}/entries`, { params }));
  }

  // Historial de todos los pacientes, del mas reciente al mas antiguo.
  history(): Promise<Entry[]> {
    return firstValueFrom(this.http.get<Entry[]>('/api/entries'));
  }
}
