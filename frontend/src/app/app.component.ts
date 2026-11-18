import { Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterOutlet } from '@angular/router';
import { filter, map, startWith } from 'rxjs';

import { IconComponent } from './core/icon.component';
import { DOCTOR_MENU, NURSING_MENU } from './core/menu';
import { MenuMarkService } from './core/menu-mark.service';
import { NotificationsService } from './core/notifications.service';
import { PageTitleService } from './core/page-title.service';
import { roleHome, roleLabel } from './core/roles';
import { SessionService } from './core/session.service';
import { ToastService } from './core/toast.service';
import { BellComponent } from './ui/bell.component';
import { SidebarComponent, SidebarUser } from './ui/sidebar.component';

// El armazon del mockup: menu lateral, encabezado con "Ver como" y la zona de la vista.
@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, SidebarComponent, IconComponent, BellComponent],
  templateUrl: './app.component.html',
})
export class AppComponent {
  protected readonly session = inject(SessionService);
  protected readonly toasts = inject(ToastService);
  protected readonly pageTitle = inject(PageTitleService);
  private readonly notifications = inject(NotificationsService);
  private readonly router = inject(Router);
  private readonly menuMark = inject(MenuMarkService);

  // El menu de cada rol (D26): el del medico aun no tiene «Armar informe» (Pieza 6).
  protected readonly menu = computed(() => (this.session.session()?.role === 'MEDICO' ? DOCTOR_MENU : NURSING_MENU));

  // Ruta actual y opcion del menu que le corresponde.
  private readonly navigation = toSignal(
    this.router.events.pipe(
      filter((event) => event instanceof NavigationEnd),
      map(() => this.deepestData()),
      startWith(this.deepestData()),
    ),
    { initialValue: {} as Record<string, unknown> },
  );

  protected readonly currentPath = computed(() => {
    this.navigation();
    return this.router.url.split('?')[0];
  });

  protected readonly menuPath = computed(() => this.menuMark.path() ?? (this.navigation()['menu'] as string | undefined) ?? this.currentPath());

  protected readonly sidebarUser = computed<SidebarUser>(() => {
    const current = this.session.session();
    return { name: current?.displayName ?? '', role: current ? roleLabel(current.role) : '' };
  });

  constructor() {
    this.router.events.pipe(filter((event) => event instanceof NavigationEnd)).subscribe(() => {
      const title = this.deepestData()['title'];
      this.pageTitle.set(typeof title === 'string' ? title : '');
      window.scrollTo(0, 0);
      // La campana se actualiza al navegar, como el mockup (D30): sin sondeo ni SSE.
      void this.notifications.refresh();
    });
  }

  protected async choose(event: Event): Promise<void> {
    await this.session.select((event.target as HTMLSelectElement).value);
    await this.router.navigateByUrl(roleHome(this.session.session()?.role)); // como el mockup, vuelve al inicio del rol
  }

  private deepestData(): Record<string, unknown> {
    let route = this.router.routerState.snapshot.root;
    while (route.firstChild) {
      route = route.firstChild;
    }
    return route.data;
  }
}
