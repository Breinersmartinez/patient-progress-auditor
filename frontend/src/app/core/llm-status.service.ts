import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

export interface LlmProviderStatus {
  provider: string;
  displayName: string;
  configured: boolean;
}

export interface LlmStatus {
  available: boolean;
  primary: LlmProviderStatus;
  fallback?: LlmProviderStatus;
  // Qué falta, en lenguaje claro, o ausente si todo está bien (D22, RNF-13).
  warning?: string;
}

@Injectable({ providedIn: 'root' })
export class LlmStatusService {
  private readonly http = inject(HttpClient);

  get(): Promise<LlmStatus> {
    return firstValueFrom(this.http.get<LlmStatus>('/api/llm/status'));
  }
}
