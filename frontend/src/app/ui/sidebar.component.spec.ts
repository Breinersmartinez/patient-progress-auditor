import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { NURSING_MENU } from '../core/menu';
import { SidebarComponent } from './sidebar.component';

@Component({
  standalone: true,
  imports: [SidebarComponent],
  template: `<nav appSidebar id="sb" [modules]="menu" [user]="user" [currentPath]="path()"></nav>`,
})
class HostComponent {
  menu = NURSING_MENU;
  user = { name: 'Marcela Rojas', role: 'Enfermería' };
  path = signal('/enf/pacientes');
}

describe('SidebarComponent (menuLateral del mockup)', () => {
  function setup() {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    return fixture;
  }

  const nav = (fixture: ReturnType<typeof setup>): HTMLElement => fixture.nativeElement.querySelector('nav');

  it('es el propio nav.sb, con aria-label y las tres opciones', () => {
    const element = nav(setup());

    expect(element.classList).toContain('sb');
    expect(element.getAttribute('aria-label')).toBe('Menú principal');
    expect(element.querySelectorAll('.sb-list > .sb-item').length).toBe(3);
  });

  it('marca la opción actual con aria-current y despliega su grupo', () => {
    const fixture = setup();
    fixture.componentInstance.path.set('/enf/informes/ver/abc');
    fixture.detectChanges();
    TestBed.flushEffects();
    fixture.detectChanges();

    // Sin coincidencia exacta no hay enlace marcado; con la ruta de la opcion si.
    expect(nav(fixture).querySelector('[aria-current]')).toBeNull();

    fixture.componentInstance.path.set('/enf/informes/revision');
    fixture.detectChanges();
    TestBed.flushEffects();
    fixture.detectChanges();

    const current = nav(fixture).querySelector('[aria-current="page"]');
    expect(current?.textContent).toBe('En revisión');
    expect(nav(fixture).querySelectorAll('.sb-grupo')[1].getAttribute('aria-expanded')).toBe('true');
  });

  it('elige el enlace más específico cuando la ruta tiene prefijo', () => {
    const fixture = setup();
    fixture.componentInstance.path.set('/enf/pacientes/ficha/P-014');
    fixture.detectChanges();

    expect(nav(fixture).querySelector('[aria-current="page"] .sb-text')?.textContent).toBe('Pacientes');
  });

  it('el botón contrae y expande el menú y cambia su etiqueta', () => {
    const fixture = setup();
    const toggle = nav(fixture).querySelector('.sb-toggle') as HTMLButtonElement;
    const before = nav(fixture).getAttribute('data-collapsed');

    toggle.click();
    fixture.detectChanges();

    expect(nav(fixture).getAttribute('data-collapsed')).not.toBe(before);
    expect(toggle.getAttribute('aria-label')).toBe(before === 'true' ? 'Contraer menú' : 'Expandir menú');
  });

  it('contraído, un grupo lleva a su primer submódulo', () => {
    const fixture = setup();
    const navigate = spyOn(TestBed.inject(Router), 'navigateByUrl').and.resolveTo(true);
    const element = nav(fixture);
    if (element.getAttribute('data-collapsed') !== 'true') {
      (element.querySelector('.sb-toggle') as HTMLButtonElement).click();
      fixture.detectChanges();
    }
    (element.querySelectorAll('.sb-grupo')[0] as HTMLButtonElement).click();

    expect(navigate).toHaveBeenCalledWith('/enf/registro/nuevo');
  });

  it('la tarjeta del usuario se abre, solo muestra lo que hay y se cierra con Escape', () => {
    const fixture = setup();
    const element = nav(fixture);
    const button = element.querySelector('.sb-user__btn') as HTMLButtonElement;
    const card = element.querySelector('.sb-card') as HTMLElement;

    expect(card.hidden).toBeTrue();
    button.click();
    fixture.detectChanges();

    expect(card.hidden).toBeFalse();
    expect(button.getAttribute('aria-expanded')).toBe('true');
    expect(card.querySelector('.sb-avatar--lg')?.textContent).toBe('MR');
    expect(card.querySelector('.sb-card__role')?.textContent).toBe('Enfermería');
    expect(card.querySelector('.sb-card__rows')).toBeNull();
    expect(card.querySelector('.sb-opt')).toBeNull(); // sin Mi perfil, Preferencias ni Cerrar sesión

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();

    expect(card.hidden).toBeTrue();
  });
});
