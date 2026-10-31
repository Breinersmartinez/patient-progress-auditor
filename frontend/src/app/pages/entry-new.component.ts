import { Component, ElementRef, OnInit, computed, inject, signal, viewChild } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { apiFailure } from '../core/api-error';
import { ConfirmedEvent, Correction, EntriesService, ProposedEvent } from '../core/entries.service';
import { IconComponent } from '../core/icon.component';
import { LlmStatus, LlmStatusService } from '../core/llm-status.service';
import { Patient, PatientsService } from '../core/patients.service';
import { SessionService } from '../core/session.service';
import { INTENSITY_OPTIONS, SYMPTOM_CODES, SYMPTOM_LABELS, looksLikeSymptom, normalizeCode, symptomCode } from '../core/symptoms';
import { ToastService } from '../core/toast.service';
import { ProcessButtonComponent } from '../ui/process-button.component';

// Una fila de «Síntomas detectados» (filaSintoma del mockup). `original` es lo que propuso el modelo: sirve para saber
// si la persona lo cambió (origen EDITADO) o lo agregó ella (MANUAL).
interface Row {
  symptom: string;
  intensity: string;
  occurrences: string;
  original: ProposedEvent | null;
}

interface Turn {
  id: number;
  text: string;
  patientId: string;
  state: 'proposing' | 'preview' | 'saved' | 'discarded';
  rows: Row[];
  // «Texto corregido» (RF-42): lo que propuso el modelo y lo que hay ahora en el campo. Sin propuesta no hay campo.
  proposedText: string | null;
  correctedText: string;
  // Por qué la IA no pudo proponer (RF-41); la persona puede guardar solo el texto o agregar a mano.
  failure: string | null;
  error: string;
  savedAs: 'structured' | 'text' | null;
}

const WHAT_NEXT = ' Puede guardar solo el texto o agregar los síntomas a mano.';

// vRegNuevo(), filaSintoma() y vistaPrevia() del mockup, con el «texto corregido» editable (RF-42, E19).
@Component({
  selector: 'app-entry-new',
  standalone: true,
  imports: [RouterLink, IconComponent, ProcessButtonComponent],
  template: `
    @if (llm()?.warning; as warning) {
      <div class="aviso"><svg appIcon="ia"></svg><span>{{ warning }}</span></div>
    }
    <div class="chat-pag">
      <div class="chat-datos">
        <div class="campo">
          <label for="rn-pac">Paciente</label>
          <select class="inp" id="rn-pac" (change)="patientId.set($any($event.target).value)">
            @for (p of patients(); track p.id) {
              <option [value]="p.id" [selected]="p.id === patientId()">{{ p.alias }}{{ p.bed ? ' (cama ' + p.bed + ')' : '' }}</option>
            }
          </select>
        </div>
        <div class="campo">
          <label for="rn-fecha">Fecha del registro</label>
          <input class="inp" type="date" id="rn-fecha" [value]="date()" [max]="session.today()"
                 (input)="date.set($any($event.target).value)">
          <span class="error" id="rn-err">{{ dateError() }}</span>
        </div>
      </div>
      <div class="chat" id="rn-chat">
        @if (visibleTurns().length === 0) {
          <p class="ayuda">Escriba cómo estuvo el paciente hoy, como en un chat.<br>La IA corrige la redacción y detecta los síntomas; usted revisa y aprueba antes de guardar.</p>
        }
        @for (turn of visibleTurns(); track turn.id) {
          <div class="burbuja">{{ turn.text }}</div>
          @if (turn.state === 'preview') {
            <section class="card card--quieta vista-ia">
              <h3><svg appIcon="ia"></svg>Así quedaría el registro</h3>
              @if (turn.failure; as reason) {
                <div class="aviso"><svg appIcon="rechazar"></svg><span>{{ reason }}</span></div>
              }
              @if (turn.proposedText !== null) {
                <div class="campo">
                  <label [attr.for]="'rn-texto-' + turn.id">Texto corregido</label>
                  <textarea class="inp" data-f="texto" rows="4" [id]="'rn-texto-' + turn.id" [value]="turn.correctedText"
                            (input)="turn.correctedText = $any($event.target).value"></textarea>
                  <span class="sec">Tal como se escribió se guarda también el original; los informes citan el original.</span>
                </div>
              }
              <div class="campo" [style.margin-top]="turn.proposedText !== null ? '12px' : null">
                <label>Síntomas detectados</label>
                <div class="sint-filas">
                  @for (row of turn.rows; track row) {
                    <div class="fila-sint">
                      <div class="campo"><label>Síntoma</label>
                        <input class="inp" data-s="n" list="catalogo" placeholder="Ej.: Diarrea, o escriba otro síntoma" [value]="row.symptom"
                               (input)="row.symptom = $any($event.target).value">
                      </div>
                      <div class="campo"><label>Intensidad</label>
                        <select class="inp" data-s="intensidad" (change)="row.intensity = $any($event.target).value">
                          @for (option of intensities; track option.code) {
                            <option [value]="option.code" [selected]="option.code === row.intensity">{{ option.label }}</option>
                          }
                        </select>
                      </div>
                      <div class="campo"><label>Veces</label>
                        <input class="inp" data-s="veces" type="number" min="0" max="20" placeholder="Opcional"
                               [value]="row.occurrences" (input)="row.occurrences = $any($event.target).value">
                      </div>
                      <button class="btn btn--sec" type="button" data-a="quitar" aria-label="Quitar síntoma" (click)="removeRow(turn, row)">
                        <svg appIcon="eliminar"></svg>Quitar
                      </button>
                    </div>
                  }
                </div>
                <span class="error" data-e="sint">{{ turn.error }}</span>
              </div>
              <div class="botones" style="margin-top:12px">
                <button class="btn btn--sec" type="button" data-a="agregar" (click)="addRow(turn)">Agregar síntoma</button>
              </div>
              <div class="botones">
                <button class="btn btn--sec" type="button" data-a="descartar" (click)="discard(turn)">Descartar</button>
                @if (turn.failure) {
                  <button appProcessButton #textOnly="processButton" label="Guardar solo el texto" busyLabel="Guardando" icon="guardar"
                          data-a="guardar-texto" (click)="save(turn, textOnly, false)"></button>
                }
                @if (!turn.failure || turn.rows.length) {
                  <button appProcessButton #approve="processButton" label="Aprobar y guardar" busyLabel="Guardando" icon="guardar"
                          class="btn--primary" data-a="guardar" (click)="save(turn, approve, true)"></button>
                }
              </div>
            </section>
          }
          @if (turn.state === 'saved') {
            <div class="aviso">
              <svg appIcon="aprobar"></svg>
              <span>{{ turn.savedAs === 'text' ? 'Registro guardado solo con el texto; quedó sin estructurar.' : 'Registro guardado.' }}
                <a [routerLink]="['/enf/pacientes/ficha', turn.patientId]">Ver ficha del paciente</a></span>
            </div>
          }
        }
      </div>
      <form class="entrada" autocomplete="off" (submit)="send($event, sendButton)">
        <input class="inp" id="rn-txt" #draftInput aria-label="Registro del día">
        <button appProcessButton #sendButton="processButton" label="Enviar" busyLabel="Enviando" icon="enviar" type="submit"
                class="btn--primary"></button>
      </form>
      <datalist id="catalogo">
        @for (code of catalog; track code) { <option [value]="labels[code]"></option> }
      </datalist>
    </div>
  `,
})
export class EntryNewComponent implements OnInit {
  protected readonly session = inject(SessionService);
  private readonly route = inject(ActivatedRoute);
  private readonly patientsService = inject(PatientsService);
  private readonly entries = inject(EntriesService);
  private readonly llmStatus = inject(LlmStatusService);
  private readonly toast = inject(ToastService);

  protected readonly catalog = SYMPTOM_CODES;
  protected readonly labels = SYMPTOM_LABELS;
  protected readonly intensities = INTENSITY_OPTIONS;

  protected readonly patients = signal<Patient[]>([]);
  protected readonly patientId = signal('');
  // La fecha de hoy la da el servidor (F7); por defecto y máximo.
  protected readonly date = signal('');
  protected readonly dateError = signal('');
  private readonly draftInput = viewChild.required<ElementRef<HTMLInputElement>>('draftInput');
  protected readonly turns = signal<Turn[]>([]);
  protected readonly llm = signal<LlmStatus | null>(null);
  protected readonly visibleTurns = computed(() => this.turns().filter((turn) => turn.state !== 'discarded'));

  private nextTurn = 0;

  async ngOnInit(): Promise<void> {
    this.date.set(this.session.today() ?? '');
    this.llmStatus.get().then((status) => this.llm.set(status)).catch(() => undefined);
    try {
      const list = await this.patientsService.list();
      this.patients.set(list);
      const wanted = this.route.snapshot.paramMap.get('id');
      this.patientId.set(list.find((p) => p.id === wanted)?.id ?? list[0]?.id ?? '');
    } catch (e) {
      this.toast.show(apiFailure(e).message, false);
    }
  }

  private patch(id: number, change: (turn: Turn) => void): void {
    this.turns.update((list) => list.map((turn) => {
      if (turn.id === id) {
        change(turn);
      }
      return { ...turn };
    }));
  }

  protected async send(event: Event, button: ProcessButtonComponent): Promise<void> {
    event.preventDefault();
    // Se lee y se limpia el propio campo, como el mockup: no depende de que la vista ya se haya actualizado.
    const input = this.draftInput().nativeElement;
    const text = input.value;
    if (!text.trim() || !this.patientId()) {
      return;
    }
    const date = this.date();
    this.dateError.set(this.checkDate(date));
    if (this.dateError()) {
      return;
    }
    const turn: Turn = { id: this.nextTurn++, text, patientId: this.patientId(), state: 'proposing', rows: [], proposedText: null, correctedText: '', failure: null, error: '', savedAs: null };
    this.turns.update((list) => [...list, turn]);
    input.value = '';
    // El texto de la burbuja es el que se guarda: se fija aquí y no se vuelve a leer de la caja (E9, riesgo de texto distinto).
    await button.run(() => this.propose(turn));
  }

  private async propose(turn: Turn): Promise<void> {
    try {
      const proposal = await this.entries.propose(turn.patientId, turn.text);
      this.patch(turn.id, (t) => {
        t.state = 'preview';
        t.failure = proposal.failure ?? null;
        t.proposedText = proposal.correctedText ?? null;
        t.correctedText = proposal.correctedText ?? '';
        t.rows = proposal.events.map((e) => ({
          symptom: SYMPTOM_LABELS[e.symptom] ?? e.label ?? e.symptom,
          intensity: e.intensity,
          occurrences: e.occurrences ? String(e.occurrences) : '',
          original: e,
        }));
      });
    } catch (e) {
      this.patch(turn.id, (t) => {
        t.state = 'preview';
        t.failure = apiFailure(e).message + WHAT_NEXT;
        t.rows = [];
      });
    }
  }

  private checkDate(date: string): string {
    if (!date) {
      return 'Elija la fecha del registro.';
    }
    const today = this.session.today();
    return today && date > today ? 'La fecha del registro no puede ser futura.' : '';
  }

  protected addRow(turn: Turn): void {
    this.patch(turn.id, (t) => t.rows.push({ symptom: '', intensity: 'SIN_INDICAR', occurrences: '', original: null }));
  }

  protected removeRow(turn: Turn, row: Row): void {
    this.patch(turn.id, (t) => (t.rows = t.rows.filter((r) => r !== row)));
  }

  // «Descartar» cierra la vista previa y no guarda nada (decisión del equipo, 28-oct).
  protected discard(turn: Turn): void {
    this.patch(turn.id, (t) => (t.state = 'discarded'));
  }

  protected async save(turn: Turn, button: ProcessButtonComponent, withEvents: boolean): Promise<void> {
    const date = this.date();
    this.dateError.set(this.checkDate(date));
    let events: ConfirmedEvent[] | undefined;
    let error = '';
    if (withEvents) {
      const parsed = this.parseRows(turn);
      events = parsed.events;
      error = parsed.error;
    }
    this.patch(turn.id, (t) => (t.error = error));
    if (error || this.dateError()) {
      return;
    }
    await button.run(async () => {
      try {
        const correction = events ? this.correctionOf(turn) : undefined;
        await this.entries.create(turn.patientId, { entryDate: date, body: turn.text, ...(events ? { events } : {}),
          ...(correction ? { correction } : {}) });
      } catch (e) {
        const failure = apiFailure(e);
        if (failure.fields['entryDate']) {
          this.dateError.set(failure.fields['entryDate']);
        } else if (failure.fields['events']) {
          this.patch(turn.id, (t) => (t.error = failure.fields['events']));
        } else {
          this.toast.show(failure.message, false);
        }
        return;
      }
      this.patch(turn.id, (t) => {
        t.state = 'saved';
        t.savedAs = events ? 'structured' : 'text';
      });
    });
  }

  // El texto corregido que se guarda: el del campo si hay y es distinto del original (si es igual no hay nada que versionar).
  private correctionOf(turn: Turn): Correction | undefined {
    const body = turn.correctedText.trim();
    if (turn.proposedText === null || !body || body === turn.text) {
      return undefined;
    }
    return { body, origin: body === turn.proposedText ? 'IA_ACEPTADA' : 'IA_EDITADA' };
  }

  // Valida lo que la persona dejó en las filas con las mismas reglas que el servidor (que sigue decidiendo).
  private parseRows(turn: Turn): { events: ConfirmedEvent[]; error: string } {
    const events: ConfirmedEvent[] = [];
    const seen = new Set<string>();
    for (const row of turn.rows) {
      const typed = row.symptom.trim();
      if (!typed) {
        continue; // una fila vacía se ignora, como en el mockup
      }
      // Uno del catálogo va por su código; cualquier otro, por el nombre que se escribió (el servidor calcula su código, E18).
      const catalogCode = symptomCode(typed);
      if (!catalogCode && !looksLikeSymptom(typed)) {
        return { events: [], error: 'Escriba el nombre del síntoma con letras (hasta 60 caracteres).' };
      }
      const code = catalogCode ?? normalizeCode(typed);
      if (seen.has(code)) {
        return { events: [], error: 'Hay un síntoma repetido. Déjelo una sola vez.' };
      }
      seen.add(code);
      const times = row.occurrences.trim() === '' ? 0 : Number(row.occurrences);
      if (!Number.isInteger(times) || times < 0 || times > 20) {
        return { events: [], error: 'Las veces deben estar entre 1 y 20.' };
      }
      const occurrences = times === 0 ? null : times;
      const original = row.original;
      const unchanged = original !== null && original.symptom === code && original.intensity === row.intensity
        && (original.occurrences ?? null) === occurrences;
      events.push({ symptom: code, ...(catalogCode ? {} : { label: typed.replace(/\s+/g, ' ') }), intensity: row.intensity, occurrences,
        origin: original === null ? 'MANUAL' : unchanged ? 'IA' : 'EDITADO' });
    }
    return { events, error: '' };
  }
}
