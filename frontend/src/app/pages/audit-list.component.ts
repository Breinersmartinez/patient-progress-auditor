import { Component, OnInit, computed, inject, signal } from '@angular/core';

import { apiFailure } from '../core/api-error';
import { AuditItem, AuditPage, AuditService } from '../core/audit.service';
import { dFull, fechaHora, periodoTxt, sumar } from '../core/date-es';
import { IconComponent } from '../core/icon.component';
import { roleLabel } from '../core/roles';
import { AppUser, UsersService } from '../core/users.service';
import { EmptyStateComponent } from '../ui/empty-state.component';
import { REPORT_STATUS_LABELS } from '../ui/status-chip.component';

// Qué hizo, en pasado, para la tabla; y en infinitivo, para el filtro. Mismos códigos que audit_logs.action (V13).
export const AUDIT_ACTIONS: { code: string; done: string; filter: string }[] = [
  { code: 'APROBAR_INFORME', done: 'Aprobó un informe', filter: 'Aprobar un informe' },
  { code: 'RECHAZAR_INFORME', done: 'Rechazó un informe', filter: 'Rechazar un informe' },
  { code: 'ENVIAR_INFORME', done: 'Envió un informe al médico', filter: 'Enviar un informe al médico' },
  { code: 'EDITAR_INFORME', done: 'Editó un informe', filter: 'Editar un informe' },
  { code: 'REGISTRAR_ENTRADA_MEDICO', done: 'Registró una entrada', filter: 'Registro del médico' },
  { code: 'CREAR_USUARIO', done: 'Creó un usuario', filter: 'Crear un usuario' },
  { code: 'ACTUALIZAR_USUARIO', done: 'Actualizó un usuario', filter: 'Actualizar un usuario' },
];

const PAGE_SIZE = 50;

const statusLabel = (value: unknown): string => REPORT_STATUS_LABELS[String(value)] ?? String(value);
const activeLabel = (value: unknown): string => (value ? 'Activo' : 'Inactivo');

// La bitácora de acciones del administrador (RF-35, docs/auditoria/): quién hizo cada acción sensible, qué, cuándo y
// sobre qué. Solo lectura, con el aspecto de «Usuarios». Filtros y paginación los resuelve el servidor (puede haber miles
// de filas), por eso no usa app-listing, que filtra en el navegador; sí usa sus mismas clases.
@Component({
  selector: 'app-audit-list',
  standalone: true,
  imports: [EmptyStateComponent, IconComponent],
  template: `
    <div class="cab">
      <p class="sec">Quién hizo cada acción sensible y cuándo. Es de solo lectura: nadie puede cambiarla ni borrarla.</p>
    </div>
    @if (error(); as message) {
      <div class="aviso"><svg appIcon="rechazar"></svg><span>{{ message }}</span></div>
    }
    <div class="barra">
      <select class="inp" id="aud-persona" aria-label="Filtrar por persona" (change)="setActor($any($event.target).value)">
        <option value="">Todas las personas</option>
        @for (user of users(); track user.id) {
          <option [value]="user.id" [selected]="user.id === actorId()">{{ user.name }} ({{ label(user.role) }})</option>
        }
      </select>
      <select class="inp" id="aud-accion" aria-label="Filtrar por tipo de acción" (change)="setAction($any($event.target).value)">
        <option value="">Todas las acciones</option>
        @for (action of actions; track action.code) {
          <option [value]="action.code" [selected]="action.code === selectedAction()">{{ action.filter }}</option>
        }
      </select>
      @if (result(); as r) {
        <span class="total">{{ r.total }} {{ r.total === 1 ? 'resultado' : 'resultados' }}</span>
      }
    </div>
    <div class="cont">
      <div class="tabla-wrap">
        @if (result(); as r) {
          @if (r.items.length === 0) {
            @if (filtered()) {
              <app-empty-state title="Sin resultados" text="Ninguna acción coincide con los filtros. Pruebe con otra persona o con otro tipo de acción." />
            } @else {
              <app-empty-state title="Aún no hay acciones registradas"
                text="Aparecen cuando alguien aprueba, rechaza, edita o envía un informe, cuando el médico registra una entrada o cuando se crea o cambia un usuario." />
            }
          } @else {
            <table class="tabla">
              <thead>
                <tr>@for (col of cols; track col) { <th scope="col">{{ col }}</th> }</tr>
              </thead>
              <tbody>
                @for (item of r.items; track item.id) {
                  <tr>
                    <td data-label="Cuándo">{{ when(item) }}</td>
                    <td data-label="Quién"><b>{{ item.actor.name }}</b></td>
                    <td data-label="Rol">{{ label(item.actor.role) }}</td>
                    <td data-label="Qué hizo">{{ done(item) }}</td>
                    <td data-label="Sobre qué">{{ subject(item) }}</td>
                    <td data-label="Cambio">{{ change(item) }}</td>
                  </tr>
                }
              </tbody>
            </table>
          }
        }
      </div>
      @if (pages() > 1) {
        <nav class="paginas" aria-label="Páginas de la bitácora">
          <button class="btn btn--sec" type="button" [disabled]="page() === 0 || loading()" (click)="go(page() - 1)">Anterior</button>
          <span>Página {{ page() + 1 }} de {{ pages() }}</span>
          <button class="btn btn--sec" type="button" [disabled]="page() + 1 >= pages() || loading()" (click)="go(page() + 1)">Siguiente</button>
        </nav>
      }
    </div>
  `,
})
export class AuditListComponent implements OnInit {
  private readonly service = inject(AuditService);
  private readonly usersService = inject(UsersService);

  protected readonly cols = ['Cuándo', 'Quién', 'Rol', 'Qué hizo', 'Sobre qué', 'Cambio'];
  protected readonly actions = AUDIT_ACTIONS;
  protected readonly label = roleLabel;

  protected readonly users = signal<AppUser[]>([]);
  protected readonly actorId = signal('');
  protected readonly selectedAction = signal('');
  protected readonly page = signal(0);
  protected readonly result = signal<AuditPage | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly filtered = computed(() => !!this.actorId() || !!this.selectedAction());
  protected readonly pages = computed(() => Math.ceil((this.result()?.total ?? 0) / PAGE_SIZE));

  // Cada carga lleva un número: si la persona cambia el filtro antes de que llegue la anterior, la vieja se descarta.
  private request = 0;

  async ngOnInit(): Promise<void> {
    // La lista de personas es solo para el filtro: si falla, la bitácora se muestra igual.
    this.usersService.list().then((users) => this.users.set(users), () => this.users.set([]));
    await this.load();
  }

  protected setActor(id: string): void {
    this.actorId.set(id);
    this.page.set(0);
    void this.load();
  }

  protected setAction(code: string): void {
    this.selectedAction.set(code);
    this.page.set(0);
    void this.load();
  }

  protected go(page: number): void {
    this.page.set(page);
    void this.load();
  }

  private async load(): Promise<void> {
    const mine = ++this.request;
    this.loading.set(true);
    try {
      const page = await this.service.list({ actorId: this.actorId() || undefined, action: this.selectedAction() || undefined, page: this.page() });
      if (mine === this.request) {
        this.result.set(page);
        this.error.set(null);
      }
    } catch (e) {
      if (mine === this.request) {
        this.error.set(apiFailure(e).message);
      }
    } finally {
      if (mine === this.request) {
        this.loading.set(false);
      }
    }
  }

  protected when(item: AuditItem): string {
    return fechaHora(item.occurredAt);
  }

  protected done(item: AuditItem): string {
    return AUDIT_ACTIONS.find((a) => a.code === item.action)?.done ?? item.action;
  }

  // Códigos y fechas, nunca texto clínico. Si el objeto ya no existe, solo su tipo.
  protected subject(item: AuditItem): string {
    const s = item.subject;
    if (item.entityType === 'REPORT') {
      if (!s.patientCode || !s.periodStart) {
        return 'Un informe que ya no existe';
      }
      const monthly = s.reportType === 'MENSUAL';
      const period = periodoTxt(s.periodStart, sumar(s.periodStart, monthly ? 27 : 6));
      return `${s.patientCode} · Informe ${monthly ? 'mensual' : 'semanal'} del ${period}`;
    }
    if (item.entityType === 'ENTRY') {
      return s.patientCode && s.entryDate ? `${s.patientCode} · Registro del ${dFull(s.entryDate)}` : 'Un registro que ya no existe';
    }
    if (item.entityType === 'USER') {
      return s.userName ? `Usuario ${s.userName}` : 'Un usuario que ya no existe';
    }
    return item.entityType;
  }

  // El antes y el después en una frase.
  protected change(item: AuditItem): string {
    const b = item.before ?? {};
    const a = item.after ?? {};
    switch (item.action) {
      case 'APROBAR_INFORME':
        return `${statusLabel(b['status'])} → ${statusLabel(a['status'])} (versión ${a['approvedVersionNo']})`;
      case 'RECHAZAR_INFORME':
        return `${statusLabel(b['status'])} → ${statusLabel(a['status'])} · motivo de ${a['reasonLength']} caracteres`;
      case 'ENVIAR_INFORME':
        return `${statusLabel(b['status'])} → ${statusLabel(a['status'])}`;
      case 'EDITAR_INFORME':
        return `Versión ${b['versionNo']} → ${a['versionNo']}`;
      case 'REGISTRAR_ENTRADA_MEDICO': {
        const events = Number(a['events'] ?? 0);
        const how = a['source'] === 'VOZ' ? 'dictado' : 'escrito';
        return a['structuringStatus'] === 'SIN_ESTRUCTURAR'
          ? `Registro ${how}, sin estructurar`
          : `Registro ${how}, ${events} ${events === 1 ? 'evento confirmado' : 'eventos confirmados'}`;
      }
      case 'CREAR_USUARIO':
        return `Rol ${roleLabel(String(a['role']))} · ${activeLabel(a['active'])}`;
      case 'ACTUALIZAR_USUARIO': {
        const parts: string[] = [];
        if (b['name'] !== a['name']) {
          parts.push(`Nombre: ${b['name']} → ${a['name']}`);
        }
        if (b['role'] !== a['role']) {
          parts.push(`Rol: ${roleLabel(String(b['role']))} → ${roleLabel(String(a['role']))}`);
        }
        if (b['active'] !== a['active']) {
          parts.push(`Estado: ${activeLabel(b['active'])} → ${activeLabel(a['active'])}`);
        }
        return parts.length ? parts.join(' · ') : 'Sin cambios';
      }
      default:
        return '';
    }
  }
}
