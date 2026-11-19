import { Component, OnInit, computed, inject, signal, viewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';

import { apiFailure } from '../core/api-error';
import { ROLE_LABELS } from '../core/roles';
import { SessionService } from '../core/session.service';
import { ToastService } from '../core/toast.service';
import { AppUser, UsersService } from '../core/users.service';
import { ProcessButtonComponent } from '../ui/process-button.component';

type Field = 'name' | 'email' | 'role' | 'active';

// vUsuForm() del mockup (RF-33): nuevo usuario y editar usuario. Al editar, el correo no se puede cambiar; y quien se
// edita a sí mismo no puede cambiar su rol ni desactivarse (el servidor también lo impide, 409).
@Component({
  selector: 'app-user-form',
  standalone: true,
  imports: [ProcessButtonComponent],
  template: `
    @if (loadError(); as message) {
      <div class="aviso"><span>{{ message }}</span></div>
    } @else {
      <form class="chat-pag" novalidate (submit)="submit($event)">
        @if (failure(); as message) {
          <div class="aviso"><span>{{ message }}</span></div>
        }
        <div class="campo">
          <label for="uf-nombre">Nombre completo</label>
          <input class="inp" id="uf-nombre" maxlength="100" [value]="name()" (input)="name.set($any($event.target).value)">
          <span class="error" data-e="nombre">{{ errors().name }}</span>
        </div>
        <div class="campo">
          <label for="uf-correo">Correo</label>
          <input class="inp" id="uf-correo" type="email" placeholder="nombre@institucion.org" [value]="email()"
                 [disabled]="editing()" (input)="email.set($any($event.target).value)">
          <span class="error" data-e="correo">{{ errors().email }}</span>
        </div>
        <div class="chat-datos">
          <div class="campo">
            <label for="uf-rol">Rol</label>
            <select class="inp" id="uf-rol" [disabled]="isSelf()" (change)="role.set($any($event.target).value)">
              @for (option of roleOptions; track option.code) {
                <option [value]="option.code" [selected]="option.code === role()">{{ option.label }}</option>
              }
            </select>
            <span class="error" data-e="rol">{{ errors().role }}</span>
          </div>
          <div class="campo">
            <label for="uf-estado">Estado</label>
            <select class="inp" id="uf-estado" [disabled]="isSelf()" (change)="active.set($any($event.target).value === 'Activo')">
              <option [selected]="active()">Activo</option>
              <option [selected]="!active()">Inactivo</option>
            </select>
            <span class="error" data-e="estado">{{ errors().active }}</span>
          </div>
        </div>
        @if (isSelf()) {
          <p class="sec">Es su propia cuenta: no puede cambiar su rol ni desactivarla.</p>
        }
        <div class="acc">
          <button appProcessButton [label]="editing() ? 'Guardar cambios' : 'Crear usuario'"
                  [busyLabel]="editing() ? 'Guardando' : 'Creando'" icon="guardar" type="submit" class="btn--primary"></button>
          @if (editing() && active()) {
            <button appProcessButton #resend="processButton" label="Reenviar invitación" busyLabel="Reenviando" icon="enviar"
                    type="button" class="btn--sec" (click)="resendInvitation(resend)"></button>
          }
        </div>
      </form>
    }
  `,
})
export class UserFormComponent implements OnInit {
  private readonly service = inject(UsersService);
  private readonly session = inject(SessionService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly buttons = viewChild.required(ProcessButtonComponent);

  protected readonly roleOptions = ['ENFERMERIA', 'MEDICO', 'ADMIN'].map((code) => ({ code, label: ROLE_LABELS[code] }));
  protected readonly id = signal<string | null>(null);
  protected readonly name = signal('');
  protected readonly email = signal('');
  protected readonly role = signal('ENFERMERIA');
  protected readonly active = signal(true);
  protected readonly errors = signal<Partial<Record<Field, string>>>({});
  protected readonly failure = signal<string | null>(null);
  protected readonly loadError = signal<string | null>(null);
  protected readonly editing = computed(() => this.id() !== null);
  protected readonly isSelf = computed(() => this.editing() && this.session.session()?.id === this.id());

  async ngOnInit(): Promise<void> {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      return;
    }
    this.id.set(id);
    try {
      const user = (await this.service.list()).find((u) => u.id === id);
      if (!user) {
        this.toast.show('No encontramos a ese usuario.', false);
        await this.router.navigateByUrl('/adm/usuarios');
        return;
      }
      this.fill(user);
    } catch (e) {
      this.loadError.set(apiFailure(e).message);
    }
  }

  private fill(user: AppUser): void {
    this.name.set(user.name);
    this.email.set(user.email);
    this.role.set(user.role);
    this.active.set(user.active);
  }

  // Los mismos topes que el servidor; el servidor sigue siendo quien decide.
  private validate(): Partial<Record<Field, string>> {
    const errors: Partial<Record<Field, string>> = {};
    if (!this.name().trim()) {
      errors.name = 'Escriba el nombre.';
    }
    if (!this.editing() && !/^\S+@\S+\.\S+$/.test(this.email().trim())) {
      errors.email = 'Escriba un correo válido.';
    }
    return errors;
  }

  protected submit(event: Event): void {
    event.preventDefault();
    this.failure.set(null);
    const errors = this.validate();
    this.errors.set(errors);
    if (Object.keys(errors).length) {
      return;
    }
    void this.buttons().run(async () => {
      try {
        const id = this.id();
        if (id) {
          await this.service.update(id, { name: this.name().trim(), role: this.role(), active: this.active() });
          this.toast.show('Cambios guardados');
          await this.router.navigateByUrl('/adm/usuarios');
          return;
        }
        const created = await this.service.create({ name: this.name().trim(), email: this.email().trim(), role: this.role(), active: this.active() });
        if (!this.active()) {
          // Inactivo no recibe invitación: al activarlo se reenvía desde su edición.
          this.toast.show('Usuario creado como inactivo. Al activarlo, reenvíe la invitación.');
          await this.router.navigateByUrl('/adm/usuarios');
        } else if (created.invitationSent) {
          this.toast.show('Usuario creado');
          await this.router.navigateByUrl('/adm/usuarios');
        } else {
          // La cuenta existe pero el correo no salió: se abre su edición, donde está «Reenviar invitación».
          this.toast.show('Usuario creado, pero no se pudo enviar la invitación. Reenvíela desde aquí.', false);
          await this.router.navigateByUrl(`/adm/usuarios/editar/${created.user.id}`);
        }
      } catch (e) {
        const { message, fields } = apiFailure(e);
        this.errors.set(fields);
        this.failure.set(Object.keys(fields).length ? null : message);
      }
    });
  }

  protected async resendInvitation(button: ProcessButtonComponent): Promise<void> {
    const id = this.id();
    if (!id) {
      return;
    }
    this.failure.set(null);
    await button.run(async () => {
      try {
        await this.service.resendInvitation(id);
        this.toast.show('Invitación reenviada');
      } catch (e) {
        this.failure.set(apiFailure(e).message);
      }
    });
  }
}
