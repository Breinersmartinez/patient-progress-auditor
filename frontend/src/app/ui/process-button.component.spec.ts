import { Component, viewChild } from '@angular/core';
import { TestBed } from '@angular/core/testing';

import { ProcessButtonComponent } from './process-button.component';

@Component({
  standalone: true,
  imports: [ProcessButtonComponent],
  template: `<button appProcessButton class="btn--primary" label="Guardar" busyLabel="Guardando" icon="guardar"></button>
             <button appProcessButton id="sin-icono" label="Aprobar" busyLabel="Aprobando"></button>`,
})
class HostComponent {
  readonly first = viewChild.required(ProcessButtonComponent);
}

describe('ProcessButtonComponent (btnProceso del mockup)', () => {
  function setup() {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    return fixture;
  }

  it('conserva las clases y la estructura del mockup', () => {
    const button: HTMLElement = setup().nativeElement.querySelector('button');

    expect(button.classList).toContain('btn');
    expect(button.classList).toContain('btn--primary');
    expect(button.querySelector('.btn__idle')?.textContent).toBe('Guardar');
    expect(button.querySelector('.btn__busy')?.textContent).toBe('Guardando');
    expect(button.querySelector('svg.icon.i-guardar')).not.toBeNull();
    expect(button.getAttribute('type')).toBe('button');
  });

  it('sin icono, el anillo va dentro del texto ocupado', () => {
    const button: HTMLElement = setup().nativeElement.querySelector('#sin-icono');

    expect(button.querySelector('.btn__text--centro .btn__busy svg.i-anillo')).not.toBeNull();
  });

  it('mientras dura la acción marca aria-busy y aria-disabled, y los quita al terminar', async () => {
    const fixture = setup();
    const button: HTMLElement = fixture.nativeElement.querySelector('button');
    let release!: () => void;
    const pending = fixture.componentInstance.first().run(() => new Promise<void>((resolve) => (release = resolve)));
    fixture.detectChanges();

    expect(button.getAttribute('aria-busy')).toBe('true');
    expect(button.getAttribute('aria-disabled')).toBe('true');

    release();
    await pending;
    fixture.detectChanges();

    expect(button.hasAttribute('aria-busy')).toBeFalse();
    expect(button.hasAttribute('aria-disabled')).toBeFalse();
  });

  it('ignora una segunda acción mientras hay una en curso', async () => {
    const fixture = setup();
    const component = fixture.componentInstance.first();
    let calls = 0;
    let release!: () => void;
    const first = component.run(() => { calls++; return new Promise<void>((resolve) => (release = resolve)); });
    const second = await component.run(async () => { calls++; });

    expect(second).toBeUndefined();
    expect(calls).toBe(1);
    release();
    await first;
  });
});
