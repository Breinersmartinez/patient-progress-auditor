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

// Lo que propone el modelo. Todavía no es un evento: lo es cuando la persona lo confirma (RN-11).
export interface ProposedEvent {
  symptom: string;
  intensity: string;
  occurrences?: number;
}

export interface Proposal {
  events: ProposedEvent[];
  // Si la IA falló: el motivo, en lenguaje claro. Entonces `events` va vacío.
  failure?: string;
  provider?: string;
  model?: string;
  fallbackUsed: boolean;
  runId?: string;
}

// Un síntoma que la persona confirma al guardar. `origin`: IA tal cual, EDITADO si lo cambió, MANUAL si lo agregó.
export interface ConfirmedEvent {
  symptom: string;
  intensity: string;
  occurrences: number | null;
  origin: 'IA' | 'EDITADO' | 'MANUAL';
}

export interface NewEntry {
  entryDate: string;
  body: string;
  // Ausente: «guardar solo el texto» (la entrada queda sin estructurar).
  events?: ConfirmedEvent[];
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

  // Pide a la IA los síntomas del texto. No guarda nada (D27).
  propose(patientId: string, body: string): Promise<Proposal> {
    return firstValueFrom(this.http.post<Proposal>(`/api/patients/${encodeURIComponent(patientId)}/entries/propose`, { body }));
  }

  // Guarda la entrada con el texto tal como se escribió y, si vienen, los síntomas que la persona confirmó.
  create(patientId: string, entry: NewEntry): Promise<Entry> {
    return firstValueFrom(this.http.post<Entry>(`/api/patients/${encodeURIComponent(patientId)}/entries`, entry));
  }

  // Historial de todos los pacientes, del mas reciente al mas antiguo.
  history(): Promise<Entry[]> {
    return firstValueFrom(this.http.get<Entry[]>('/api/entries'));
  }
}
