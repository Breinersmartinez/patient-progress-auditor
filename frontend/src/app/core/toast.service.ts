import { Injectable, signal } from '@angular/core';

export interface Toast {
  id: number;
  text: string;
  ok: boolean;
}

// Equivale a toast() del mockup: aviso corto que desaparece solo.
@Injectable({ providedIn: 'root' })
export class ToastService {
  private static readonly DURATION_MS = 3800;
  private nextId = 0;
  readonly toasts = signal<Toast[]>([]);

  show(text: string, ok = true): void {
    const toast = { id: this.nextId++, text, ok };
    this.toasts.update((list) => [...list, toast]);
    setTimeout(() => this.toasts.update((list) => list.filter((t) => t.id !== toast.id)), ToastService.DURATION_MS);
  }
}
