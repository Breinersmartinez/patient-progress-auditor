import { Component, inject, signal, viewChild } from '@angular/core';
import { Router } from '@angular/router';

import { apiFailure } from '../core/api-error';
import { NewPatient, PatientsService } from '../core/patients.service';
import { SessionService } from '../core/session.service';
import { ToastService } from '../core/toast.service';
import { ProcessButtonComponent } from '../ui/process-button.component';

type Field = 'alias' | 'bed' | 'age' | 'admittedOn';

// Alta de paciente (RF-01), armada con el patron de vUsuForm del mockup (3.4).
@Component({
  selector: 'app-patient-form',
  standalone: true,
  imports: [ProcessButtonComponent],
  template: `
    <form class="chat-pag" novalidate (submit)="submit($event)">
      @if (failure(); as message) {
        <div class="aviso"><span>{{ message }}</span></div>
      }
      <div class="campo">
        <label for="pf-alias">Código del paciente</label>
        <input class="inp" id="pf-alias" maxlength="100" placeholder="Ej.: P-045" (input)="set('alias', $event)">
        <span class="error" data-e="alias">{{ errors().alias }}</span>
      </div>
      <div class="chat-datos">
        <div class="campo">
          <label for="pf-bed">Cama</label>
          <input class="inp" id="pf-bed" maxlength="10" placeholder="Ej.: 204-A" (input)="set('bed', $event)">
          <span class="error" data-e="bed">{{ errors().bed }}</span>
        </div>
        <div class="campo">
          <label for="pf-age">Edad</label>
          <input class="inp" id="pf-age" type="number" min="0" max="120" placeholder="Opcional" (input)="set('age', $event)">
          <span class="error" data-e="age">{{ errors().age }}</span>
        </div>
      </div>
      <div class="campo">
        <label for="pf-admitted">Fecha de ingreso</label>
        <input class="inp" id="pf-admitted" type="date" [max]="session.today()" (input)="set('admittedOn', $event)">
        <span class="error" data-e="admittedOn">{{ errors().admittedOn }}</span>
      </div>
      <div>
        <button appProcessButton label="Crear paciente" busyLabel="Creando" icon="guardar" type="submit"
                class="btn--primary"></button>
      </div>
    </form>
  `,
})
export class PatientFormComponent {
  protected readonly session = inject(SessionService);
  private readonly service = inject(PatientsService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly button = viewChild.required(ProcessButtonComponent);

  private readonly values: Record<Field, string> = { alias: '', bed: '', age: '', admittedOn: '' };
  protected readonly errors = signal<Partial<Record<Field, string>>>({});
  protected readonly failure = signal<string | null>(null);

  protected set(field: Field, event: Event): void {
    this.values[field] = (event.target as HTMLInputElement).value;
  }

  // Los mismos topes que el servidor; el servidor sigue siendo quien decide.
  private validate(): Partial<Record<Field, string>> {
    const errors: Partial<Record<Field, string>> = {};
    const age = this.values.age.trim();
    if (!this.values.alias.trim()) {
      errors.alias = 'Escriba el código del paciente.';
    }
    if (age && !(Number.isInteger(Number(age)) && Number(age) >= 0 && Number(age) <= 120)) {
      errors.age = 'La edad debe estar entre 0 y 120 años.';
    }
    const today = this.session.today();
    if (this.values.admittedOn && today && this.values.admittedOn > today) {
      errors.admittedOn = 'La fecha de ingreso no puede ser futura.';
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
    const patient: NewPatient = {
      alias: this.values.alias,
      bed: this.values.bed,
      age: this.values.age.trim() ? Number(this.values.age) : null,
      admittedOn: this.values.admittedOn || null,
    };
    void this.button().run(async () => {
      try {
        await this.service.create(patient);
      } catch (e) {
        const { message, fields } = apiFailure(e);
        this.errors.set(fields);
        this.failure.set(Object.keys(fields).length ? null : message);
        return;
      }
      this.toast.show('Paciente creado');
      await this.router.navigateByUrl('/enf/pacientes');
    });
  }
}
