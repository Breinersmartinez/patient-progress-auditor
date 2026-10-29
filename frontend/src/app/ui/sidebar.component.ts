import { Component, ElementRef, HostListener, computed, effect, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { IconComponent } from '../core/icon.component';
import { MenuModule } from '../core/menu';

export interface SidebarUser {
  name: string;
  role: string;
  email?: string;
  area?: string;
  position?: string;
  lastAccess?: string;
}

// Equivale a menuLateral() del mockup (Regla 13): contraible, submodulos con guia de arbol, flotantes al
// contraer, tarjeta de usuario, teclado y aria. El elemento es el propio <nav class="sb">.
// Sin "Mi perfil", "Preferencias" ni "Cerrar sesion": no hay perfil ni sesion hasta el Momento 3 (tabla 3.3).
@Component({
  selector: 'nav[appSidebar]',
  standalone: true,
  imports: [IconComponent, RouterLink],
  host: {
    class: 'sb',
    'aria-label': 'Menú principal',
    '[attr.data-collapsed]': 'collapsed()',
  },
  template: `
    <button class="sb-toggle" type="button" [attr.aria-label]="(collapsed() ? 'Expandir' : 'Contraer') + ' menú'"
            [attr.aria-expanded]="!collapsed()" (click)="toggleMenu()">
      <svg appIcon="contraer"></svg>
    </button>
    <ul class="sb-list">
      @for (module of modules(); track module.text; let i = $index) {
        @if (module.sub; as sub) {
          <li class="sb-item">
            <button class="sb-mod sb-grupo" type="button" [attr.aria-expanded]="!!expanded()[i]"
                    [attr.aria-controls]="'sb-sub-' + i" (click)="toggleGroup(i, sub[0].path)">
              <span class="sb-box"><svg [appIcon]="module.icon"></svg></span>
              <span class="sb-label"><span class="sb-text">{{ module.text }}</span><svg appIcon="desplegar" class="sb-chev"></svg></span>
            </button>
            <div class="sb-pop">
              <ul class="sb-sub" [id]="'sb-sub-' + i">
                <li class="sb-sub__title" aria-hidden="true">{{ module.text }}</li>
                @for (link of sub; track link.path) {
                  <li><a [routerLink]="link.path" [attr.aria-current]="link.path === activePath() ? 'page' : null">{{ link.text }}</a></li>
                }
              </ul>
            </div>
          </li>
        } @else {
          <li class="sb-item">
            <a class="sb-mod" [routerLink]="module.path" [attr.aria-current]="module.path === activePath() ? 'page' : null">
              <span class="sb-box"><svg [appIcon]="module.icon"></svg></span>
              <span class="sb-label"><span class="sb-text">{{ module.text }}</span></span>
            </a>
          </li>
        }
      }
    </ul>
    <div class="sb-user" [class.is-open]="cardOpen()">
      <button class="sb-mod sb-user__btn" type="button" aria-haspopup="dialog" [attr.aria-expanded]="cardOpen()"
              aria-controls="sb-card" (click)="cardOpen.set(!cardOpen())">
        <span class="sb-avatar">{{ initials() }}</span>
        <span class="sb-label"><span class="sb-text"><b>{{ user().name }}</b><small>{{ user().role }}</small></span></span>
      </button>
      <div class="sb-fly"><div class="sb-panel"><p class="sb-panel__title">{{ user().name }}</p></div></div>
      <div class="sb-card sb-panel" id="sb-card" role="dialog" aria-label="Cuenta" [hidden]="!cardOpen()">
        <div class="sb-card__head">
          <span class="sb-avatar sb-avatar--lg">{{ initials() }}</span>
          <div>
            <p class="sb-card__name">{{ user().name }}</p>
            @if (user().email) { <p class="sb-card__mail">{{ user().email }}</p> }
            <p class="sb-card__role">{{ user().role }}</p>
          </div>
        </div>
        @if (rows().length) {
          <dl class="sb-card__rows">
            @for (row of rows(); track row[0]) { <div><dt>{{ row[0] }}</dt><dd>{{ row[1] }}</dd></div> }
          </dl>
        }
      </div>
    </div>
  `,
})
export class SidebarComponent {
  readonly modules = input.required<MenuModule[]>();
  readonly user = input.required<SidebarUser>();
  // Ruta actual sin '#'. `menuPath` la reemplaza cuando la pantalla pertenece a otra opcion (ficha de paciente).
  readonly currentPath = input.required<string>();

  private readonly router = inject(Router);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  // Contraido por defecto bajo 760 px, como el mockup.
  protected readonly collapsed = signal(typeof innerWidth === 'number' && innerWidth < 760);
  protected readonly cardOpen = signal(false);
  protected readonly expanded = signal<Record<number, boolean>>({});

  protected readonly initials = computed(() =>
    this.user().name.split(/\s+/).slice(0, 2).map((part) => part[0]).join('').toUpperCase());

  // Las filas Area, Cargo y Ultimo acceso solo aparecen si hay dato (tabla 3.3).
  protected readonly rows = computed(() => {
    const user = this.user();
    const rows: [string, string][] = [];
    if (user.area) { rows.push(['Área', user.area]); }
    if (user.position) { rows.push(['Cargo', user.position]); }
    if (user.lastAccess) { rows.push(['Último acceso', user.lastAccess]); }
    return rows;
  });

  // marcar() del mockup: el enlace cuyo destino es igual a la ruta o su prefijo, el mas especifico.
  protected readonly activePath = computed(() => {
    const path = this.currentPath();
    const links = this.modules().flatMap((module) => module.sub?.map((link) => link.path) ?? (module.path ? [module.path] : []));
    return links
      .filter((link) => path === link || path.startsWith(link + '/'))
      .sort((a, b) => b.length - a.length)[0] ?? null;
  });

  constructor() {
    // El grupo de la opcion activa queda desplegado.
    effect(() => {
      const active = this.activePath();
      const index = this.modules().findIndex((module) => module.sub?.some((link) => link.path === active));
      if (index >= 0) {
        this.expanded.update((current) => ({ ...current, [index]: true }));
      }
    });
  }

  protected toggleMenu(): void {
    this.collapsed.update((value) => !value);
  }

  protected toggleGroup(index: number, firstPath: string): void {
    if (this.collapsed()) {
      void this.router.navigateByUrl(firstPath); // contraido: va al primer submodulo
      return;
    }
    this.expanded.update((current) => ({ ...current, [index]: !current[index] }));
  }

  @HostListener('document:click', ['$event'])
  protected onDocumentClick(event: Event): void {
    const user = this.host.nativeElement.querySelector('.sb-user');
    if (user && !user.contains(event.target as Node)) {
      this.cardOpen.set(false);
    }
  }

  @HostListener('document:keydown.escape')
  protected onEscape(): void {
    if (this.cardOpen()) {
      this.cardOpen.set(false);
      this.host.nativeElement.querySelector<HTMLElement>('.sb-user__btn')?.focus();
    }
  }
}
