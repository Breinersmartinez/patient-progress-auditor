import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

// Un usuario de la gestión de administración (RF-33). El rol es el código del servidor (ENFERMERIA, MEDICO, ADMIN).
export interface AppUser {
  id: string;
  name: string;
  email: string;
  role: string;
  active: boolean;
}

export interface NewUser {
  name: string;
  email: string;
  role: string;
  active: boolean;
}

// El correo no se edita: es el enlace con Keycloak y con el autor de las acciones históricas (D12).
export interface UserChanges {
  name: string;
  role: string;
  active: boolean;
}

// `invitationSent` es falso si la cuenta se creó pero el correo no salió.
export interface CreatedUser {
  user: AppUser;
  invitationSent: boolean;
}

@Injectable({ providedIn: 'root' })
export class UsersService {
  private readonly http = inject(HttpClient);

  list(): Promise<AppUser[]> {
    return firstValueFrom(this.http.get<AppUser[]>('/api/admin/users'));
  }

  create(user: NewUser): Promise<CreatedUser> {
    return firstValueFrom(this.http.post<CreatedUser>('/api/admin/users', user));
  }

  update(id: string, changes: UserChanges): Promise<AppUser> {
    return firstValueFrom(this.http.put<AppUser>(`/api/admin/users/${id}`, changes));
  }

  resendInvitation(id: string): Promise<void> {
    return firstValueFrom(this.http.post<void>(`/api/admin/users/${id}/invitation`, {}));
  }
}
