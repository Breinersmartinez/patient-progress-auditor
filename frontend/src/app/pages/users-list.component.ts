import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { apiFailure } from '../core/api-error';
import { IconComponent } from '../core/icon.component';
import { roleLabel } from '../core/roles';
import { AppUser, UsersService } from '../core/users.service';
import { CellDirective, ListingComponent } from '../ui/listing.component';
import { StatusChipComponent } from '../ui/status-chip.component';

// vUsuLista() del mockup (RF-33): las personas con acceso al sistema, con búsqueda por nombre, correo o rol.
@Component({
  selector: 'app-users-list',
  standalone: true,
  imports: [ListingComponent, CellDirective, StatusChipComponent, IconComponent, RouterLink],
  template: `
    <div class="cab">
      <p class="sec">Personas con acceso al sistema.</p>
      <a class="btn btn--primary" routerLink="/adm/usuarios/nuevo"><svg appIcon="usuarios"></svg>Nuevo usuario</a>
    </div>
    @if (error(); as message) {
      <div class="aviso"><svg appIcon="rechazar"></svg><span>{{ message }}</span></div>
    }
    @if (users(); as rows) {
      <app-listing [cols]="cols" [rows]="rows" placeholder="Busque por nombre, correo o rol"
                   [searchText]="searchText" [href]="href"
                   [empty]="{ title: 'Aún no hay usuarios', text: 'Cree el primero.', action: { text: 'Nuevo usuario', path: '/adm/usuarios/nuevo' } }">
        <ng-template appCell let-u let-col="col">
          @switch (col) {
            @case (0) { <b>{{ u.name }}</b> }
            @case (1) { {{ u.email }} }
            @case (2) { {{ label(u.role) }} }
            @case (3) { <app-status-chip [name]="u.active ? 'Activo' : 'Inactivo'" [dense]="true" /> }
          }
        </ng-template>
      </app-listing>
    }
  `,
})
export class UsersListComponent implements OnInit {
  private readonly service = inject(UsersService);

  protected readonly cols = ['Nombre', 'Correo', 'Rol', 'Estado'];
  protected readonly users = signal<AppUser[] | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly label = roleLabel;

  protected readonly searchText = (u: AppUser) => `${u.name} ${u.email} ${roleLabel(u.role)}`;
  protected readonly href = (u: AppUser) => `/adm/usuarios/editar/${u.id}`;

  async ngOnInit(): Promise<void> {
    try {
      this.users.set(await this.service.list());
    } catch (e) {
      this.error.set(apiFailure(e).message);
    }
  }
}
