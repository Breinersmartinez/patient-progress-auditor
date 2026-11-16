/*
 * Comportamiento del login de PGRP. Sale del JS de docs/07-mockups/login-sanitas/auditor_login_sanitas.html:
 * íconos, arte del panel, modo claro/oscuro, ver contraseña, validación, botón en proceso y
 * pantalla "Revisa tu correo". Los textos visibles llegan desde #ppa-textos (messages_es).
 */
(() => {
  'use strict';

  // ---- Íconos (catálogo del prototipo) ----
  const ICONOS = {
    enviar: '<g class="i-vuela"><path d="M21 3 3 10.5l7.5 3 3 7.5z"/><path d="m10.5 13.5 5-5"/></g>',
    aprobar: '<circle cx="12" cy="12" r="9"/><path class="i-dibuja" pathLength="1" d="m8.5 12.5 2.5 2.5 4.5-5"/>',
    anillo: '<circle cx="12" cy="12" r="9" opacity=".25"/><path d="M21 12a9 9 0 0 0-9-9"/>',
    ia: '<path class="i-pulso" d="M11 4.5 12.6 9l4.4 1.5-4.4 1.5-1.6 4.5L9.4 12 5 10.5 9.4 9z"/><path class="i-pulso" d="M18 3v4m-2-2h4"/><path class="i-pulso" d="M5.5 16.5v3M4 18h3"/>',
    registro: '<rect x="5" y="4" width="14" height="17" rx="2"/><path d="M9 4V3h6v1M9 10h6M9 14h6M9 18h3"/>',
    revision: '<circle cx="10" cy="8" r="3.5"/><path d="M3.5 20a6.5 6.5 0 0 1 10-5.5"/><path d="m15 18 2 2 4-4.5"/>',
    traza: '<circle cx="6" cy="6" r="2"/><circle cx="18" cy="18" r="2"/><path d="M8 6h7a3 3 0 0 1 0 6H9a3 3 0 0 0 0 6h7"/>',
    pulso: '<path d="M3 12h4l2-5 4 10 2-5h6"/>',
    perfil: '<circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/>',
    flecha: '<path d="M5 12h14m-6-6 6 6-6 6"/>',
    correo: '<rect x="3" y="5" width="18" height="14" rx="2"/><path d="m3.5 7 8.5 6 8.5-6"/>',
    candado: '<rect x="5" y="11" width="14" height="9" rx="2"/><path d="M8 11V8a4 4 0 0 1 8 0v3"/>',
    ojo: '<path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z"/><circle cx="12" cy="12" r="3"/>',
    ojoNo: '<path d="M3 3l18 18"/><path d="M10.6 5.1A9.7 9.7 0 0 1 12 5c6.5 0 10 7 10 7a17 17 0 0 1-3.2 4"/><path d="M6.6 6.6C3.9 8.3 2 12 2 12s3.5 7 10 7c1.7 0 3.2-.4 4.5-1"/><path d="M9.9 9.9a3 3 0 0 0 4.2 4.2"/>',
    alerta: '<path d="M12 4 2.5 20h19z"/><path d="M12 10v4m0 3v.01"/>',
    info: '<circle cx="12" cy="12" r="9"/><path d="M12 11v5m0-8v.01"/>',
    llave: '<circle cx="8" cy="15" r="4"/><path d="m11 12 8-8m-3 3 3 3"/>',
    ingresar: '<path d="M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3"/><path d="m10 8 4 4-4 4M14 12H4"/>',
    vacante: '<rect x="3" y="7" width="18" height="13" rx="2"/><path d="M9 7V5a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2M3 13h18"/>',
    calendario: '<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M8 3v4m8-4v4M3 10h18"/>',
    documento: '<path d="M6 3h8l5 5v11a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z"/><path d="M14 3v5h5M8 13h8M8 17h5"/>',
    luna: '<path d="M20 14.5A8 8 0 0 1 9.5 4 8 8 0 1 0 20 14.5z"/>',
    sol: '<circle cx="12" cy="12" r="4"/><path d="M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1.4 1.4m11.2 11.2L19 19M5 19l1.4-1.4m11.2-11.2L19 5"/>',
    volver: '<path d="M19 12H5m6-6-6 6 6 6"/>',
    corazon: '<path d="M12 21C7 17 3 13.500 3 9.500A4.500 4.500 0 0 1 12 7a4.500 4.500 0 0 1 9 2.500C21 13.500 17 17 12 21z"/>',
    medalla: '<circle cx="12" cy="9" r="5"/><path d="m9 13.500-1.500 7 4.500-2.500 4.500 2.500-1.500-7"/>',
    bombilla: '<path d="M9 18h6M10 21h4"/><path d="M12 3a6 6 0 0 0-3.500 10.900c.6.500 1 1.200 1 2.100h5c0-.9.4-1.600 1-2.100A6 6 0 0 0 12 3z"/>',
    equipo: '<circle cx="9" cy="8" r="3"/><circle cx="17" cy="9" r="2.500"/><path d="M3 20a6 6 0 0 1 12 0M15 14.500a5 5 0 0 1 6 5"/>',
    objetivo: '<circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="5"/><circle cx="12" cy="12" r="1.500"/>',
  };
  const ICONOS_ANIMADOS = ['enviar', 'aprobar'];
  function icono(nombre, clase = '') {
    if (!(nombre in ICONOS)) console.warn('Falta el icono', nombre);
    return `<svg class="icon i-${nombre}${clase && ' ' + clase}" viewBox="0 0 24 24" aria-hidden="true">${ICONOS[nombre] || ''}</svg>`;
  }
  const $ = (s, r = document) => r.querySelector(s);
  const T = (k) => ($('#ppa-textos')?.dataset[k]) || '';

  // Reemplaza los marcadores <i data-icono="x"> que dejan las plantillas por el SVG del catálogo.
  function pintarIconos(raiz = document) {
    raiz.querySelectorAll('i[data-icono]').forEach((i) => { i.outerHTML = icono(i.dataset.icono); });
    raiz.querySelectorAll('[data-ic]').forEach((e) => { e.innerHTML = icono(e.dataset.ic); });
  }

  // ---- Arte del panel (ARTE[5] del prototipo) ----
  function arte() {
    const svg = $('#arte'); if (!svg) return;
    const SVGI = (x, y, w, ic) => `<svg class="icon" x="${x - w / 2}" y="${y - w / 2}" width="${w}" height="${w}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">${ICONOS[ic]}</svg>`;
    const R = (x, y, w, h, c = '', rx = 6, st = '') => `<rect class="${c}" x="${x}" y="${y}" width="${w}" height="${h}" rx="${rx}"${st && ` style="${st}"`}/>`;
    const TL = (x, y, w) => R(x, y, w, 6, 'tl', 3);
    const dl = (k) => `animation-delay:calc(var(--dur) * ${k})`;
    let s = R(130, 28, 220, 262, 'card');
    s += `<circle class="avc" cx="240" cy="80" r="32"/><g class="ic">${SVGI(240, 80, 34, 'perfil')}</g>` + TL(200, 124, 80) + TL(215, 138, 50);
    [[T('arteMovilidad'), 0.8], [T('arteApetito'), 0.6], [T('arteAnimo'), 0.7]].forEach(([n, w], i) => {
      const y = 172 + i * 32;
      s += `<text x="150" y="${y}" style="text-anchor:start;font-size:12px">${n}</text>` + R(150, y + 8, 180, 6, 'trk', 3) + R(150, y + 8, 180, 6, 'fb', 3, `--w:${w};${dl((i * 0.12).toFixed(2))}`);
    });
    s += `<g class="fin"><circle cx="318" cy="52" r="15"/>${SVGI(318, 52, 18, 'aprobar')}</g>`;
    [[56, 84, 'registro', T('arteRegistro')], [56, 214, 'calendario', T('arteSemana')], [424, 84, 'pulso', T('arteEvolucion')], [424, 214, 'documento', T('arteInforme')]].forEach(([x, y, ic, nm], i) => {
      s += `<line class="ln" x1="${x}" y1="${y}" x2="${x < 240 ? 130 : 350}" y2="${y + (y < 150 ? 20 : -10)}"/><g class="nd br${i % 2 ? ' o' : ''}" style="${dl((-i / 4).toFixed(2))}"><circle cx="${x}" cy="${y}" r="24"/>${SVGI(x, y, 24, ic)}<text x="${x}" y="${y + 40}">${nm}</text></g>`;
    });
    svg.innerHTML = s;
  }

  // ---- Modo claro/oscuro (se recuerda en localStorage, igual que el prototipo) ----
  let modo = (() => { try { const m = localStorage.getItem('ppa-tema'); if (m === 'light' || m === 'dark') return m; } catch (e) { /* sin almacenamiento */ } return matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'; })();
  function aplicar() {
    const f = $('#frame-1'); if (f) f.dataset.mode = modo;
    document.documentElement.dataset.theme = modo;
    // Para las pantallas heredadas: PatternFly 5 usa esta clase para su modo oscuro.
    document.documentElement.classList.toggle('pf-v5-theme-dark', modo === 'dark');
    document.querySelectorAll('[data-tema]').forEach((b) => { b.innerHTML = icono(modo === 'dark' ? 'sol' : 'luna'); });
    try { localStorage.setItem('ppa-tema', modo); } catch (e) { /* sin almacenamiento */ }
  }
  document.addEventListener('click', (ev) => { if (ev.target.closest('[data-tema]')) { modo = modo === 'dark' ? 'light' : 'dark'; aplicar(); } });

  // ---- Ver / ocultar contraseña (data-act="ver" del prototipo) ----
  document.addEventListener('click', (ev) => {
    const b = ev.target.closest('[data-act="ver"]'); if (!b) return;
    const clave = b.parentElement.querySelector('input'); if (!clave) return;
    const ver = clave.type === 'password';
    clave.type = ver ? 'text' : 'password';
    b.setAttribute('aria-label', ver ? T('ocultarClave') : T('mostrarClave'));
    b.setAttribute('aria-pressed', String(ver));
    b.innerHTML = icono(ver ? 'ojoNo' : 'ojo');
    clave.focus();
  });

  // ---- Botón con proceso (conProceso del prototipo) ----
  // Marca el botón como ocupado y deja que el formulario se envíe de verdad a Keycloak.
  // Ignora un segundo envío mientras el primero está en curso.
  function enProceso(form) {
    const boton = form.querySelector('.btn--primary'); if (!boton) return true;
    if (boton.getAttribute('aria-busy') === 'true') return false;
    boton.setAttribute('aria-busy', 'true');
    boton.setAttribute('aria-disabled', 'true');
    return true;
  }
  // Si el navegador vuelve a la página desde su caché (botón Atrás), el botón no queda ocupado.
  window.addEventListener('pageshow', () => {
    document.querySelectorAll('.btn[aria-busy]').forEach((b) => { b.removeAttribute('aria-busy'); b.removeAttribute('aria-disabled'); });
  });

  // ---- Validación en el navegador (validar() del prototipo) ----
  // data-form="equipo": correo y contraseña. data-form="recuperar": solo correo.
  function validar(form) {
    const e = {};
    const correo = form.querySelector('#correo'); const clave = form.querySelector('#clave');
    const v = (correo?.value || '').trim();
    if (correo) {
      if (!v) e.correo = T('errCorreoVacio');
      else if (!/^\S+@\S+\.\S+$/.test(v)) e.correo = T('errCorreoInvalido');
    }
    if (form.dataset.form === 'equipo' && clave && !clave.value) e.clave = T('errClaveVacia');
    return e;
  }
  function limpiarErrores(form) {
    form.querySelectorAll('.ferr').forEach((p) => p.remove());
    form.querySelectorAll('.input.err').forEach((d) => d.classList.remove('err'));
    form.querySelectorAll('[aria-invalid]').forEach((i) => { i.removeAttribute('aria-invalid'); i.removeAttribute('aria-describedby'); });
  }
  // Mismo marcado que campo() del prototipo: .input.err, aria-invalid y <p class="ferr">.
  function marcarError(input, texto) {
    const caja = input.closest('.input'); caja.classList.add('err');
    input.setAttribute('aria-invalid', 'true'); input.setAttribute('aria-describedby', `${input.id}-e`);
    const p = document.createElement('p'); p.className = 'ferr'; p.id = `${input.id}-e`;
    p.innerHTML = icono('alerta'); p.append(texto);
    caja.after(p);
  }

  document.addEventListener('submit', (ev) => {
    const form = ev.target;
    if (!form.matches('form[data-form]')) return;
    limpiarErrores(form);
    const errs = validar(form);
    if (Object.keys(errs).length) {
      ev.preventDefault();
      // Como el prototipo: un error de campo reemplaza al aviso del servidor.
      document.querySelectorAll('.fwrap > .aviso').forEach((a) => a.remove());
      Object.entries(errs).forEach(([id, texto]) => marcarError(form.querySelector('#' + id), texto));
      form.querySelector(errs.correo ? '#correo' : '#clave')?.focus();
      return;
    }
    if (!enProceso(form)) { ev.preventDefault(); return; }
  });

  // Expuesto para las partes del login (tareas siguientes).
  window.PPA = { ICONOS, ICONOS_ANIMADOS, icono, T, $, pintarIconos, enProceso };

  document.addEventListener('DOMContentLoaded', () => {
    arte();
    pintarIconos();
    aplicar();
    // Tras un error de credenciales el prototipo deja el foco en la contraseña.
    const foco = document.querySelector('form[data-foco]');
    if (foco) foco.querySelector('#' + foco.dataset.foco)?.focus();
    document.dispatchEvent(new Event('ppa:listo'));
  });
})();
