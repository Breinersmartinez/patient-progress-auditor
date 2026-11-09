import { Component, ElementRef, HostListener, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

import { dLargo, horaCorta } from '../core/date-es';
import { IconComponent } from '../core/icon.component';
import { NotificationsService, NotificationView } from '../core/notifications.service';

// pintarCampana() del mockup: el panel se abre con la campana y se cierra al elegir una notificacion o al hacer
// clic afuera. Solo se usa para el medico (D30): quien la coloca decide eso, no este componente.
@Component({
  selector: 'app-bell',
  standalone: true,
  imports: [IconComponent],
  host: { class: 'campana' },
  template: `
    <button class="icbtn" type="button" [attr.aria-label]="'Notificaciones' + (notifications.hasUnread() ? ', hay sin leer' : '')"
            [attr.aria-expanded]="open()" (click)="toggle($event)">
      <svg appIcon="campana"></svg>
      @if (notifications.hasUnread()) { <i class="punto-notif"></i> }
    </button>
    @if (open()) {
      <div class="campana-panel">
        <h3>Notificaciones</h3>
        @if (notifications.notifications().length) {
          @for (n of notifications.notifications(); track n.id) {
            <button class="notif" type="button" (click)="select(n)">
              <i class="pt" [style.background]="n.read ? 'transparent' : 'var(--color-primary)'"></i>
              <span>{{ text(n) }}, paciente {{ n.patientAlias }}<small>{{ horaCorta(n.createdAt) }}</small></span>
            </button>
          }
        } @else {
          <p class="sec" style="padding:8px 10px">Sin notificaciones.</p>
        }
      </div>
    }
  `,
})
export class BellComponent {
  protected readonly notifications = inject(NotificationsService);
  private readonly router = inject(Router);
  private readonly host = inject(ElementRef<HTMLElement>);

  protected readonly open = signal(false);
  protected readonly horaCorta = horaCorta;

  protected toggle(event: Event): void {
    event.stopPropagation();
    this.open.update((value) => !value);
  }

  protected async select(n: NotificationView): Promise<void> {
    this.open.set(false);
    const target = n.type === 'ENTRY' ? `/med/pacientes/ficha/${n.patientId}` : `/med/informes/ver/${n.reportId}`;
    if (!n.read) {
      await this.notifications.markRead(n.id);
    }
    await this.router.navigateByUrl(target);
  }

  protected text(n: NotificationView): string {
    if (n.type === 'ENTRY') {
      return `Nuevo registro del ${n.entryDate ? dLargo(n.entryDate) : ''}`;
    }
    return n.reportType === 'MENSUAL' ? 'Informe mensual recibido' : 'Informe semanal recibido';
  }

  @HostListener('document:click', ['$event'])
  protected onDocumentClick(event: MouseEvent): void {
    if (this.open() && !this.host.nativeElement.contains(event.target as Node)) {
      this.open.set(false);
    }
  }
}
