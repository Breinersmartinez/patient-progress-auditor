/*
 * JS de las pantallas de PGRP que no son el login (marcos "estado", "alta" y "otp" de template.ftl).
 * Reproduce el comportamiento de los prototipos de DOC-PGRP: íconos, arte, claro/oscuro, aviso
 * (toast), botones con proceso, y la lógica propia del alta y del código.
 * Los textos salen de #ppa-textos (messages_es.properties); aquí no hay texto visible fijo.
 */
(function () {
  'use strict';
  const $ = (q, r = document) => r.querySelector(q);
  const MARCO = document.body.dataset.marco;
  const T = (k) => ($('#ppa-textos') || { dataset: {} }).dataset[k] || '';

  /* Íconos de cada prototipo (copiados tal cual: los mismos nombres tienen trazos distintos). */
  const IC_ESTADO = (function () {
      return {
    buscar: '<circle cx="11" cy="11" r="6.5"/><path d="m16 16 4.5 4.5"/>',
    candado: '<rect x="5" y="11" width="14" height="9" rx="2"/><path d="M8 11V8a4 4 0 0 1 8 0v3"/>',
    reloj: '<circle cx="12" cy="12" r="8.5"/><path d="M12 7.5V12l3 2"/>',
    alerta: '<path d="M12 4 3 19h18z"/><path d="M12 10v4m0 3v.01"/>',
    llave: '<path d="M14.5 5.5a4.5 4.5 0 1 1-3.2 7.7L4 20.5V17h-2m0 0 3-3"/>',
    herramienta: '<path d="M14.5 6.5a4 4 0 0 0 5 5l-9.2 9.2a2.1 2.1 0 0 1-3-3z"/><path d="m14.5 6.5 2.5-2.5 3 3-2.5 2.5"/>',
    sinred: '<path d="M3 9a14 14 0 0 1 4-2.6M21 9a14 14 0 0 0-8-3.9M6 12.5a9 9 0 0 1 3-1.8M18 12.5a9 9 0 0 0-3.5-2M9 16a4.5 4.5 0 0 1 6 0"/><path d="M12 19.5v.01M4 4l16 16"/>',
    enlace: '<path d="M10 14a4 4 0 0 0 5.7 0l2.8-2.8a4 4 0 0 0-5.7-5.7L11.5 6.8"/><path d="M14 10a4 4 0 0 0-5.7 0l-2.8 2.8a4 4 0 0 0 5.7 5.7l1.3-1.3"/><path d="m4 4 3 3M20 20l-3-3"/>',
    escudo: '<path d="M12 3.5 5 6v5.5c0 4.2 2.9 7.3 7 9 4.1-1.7 7-4.8 7-9V6z"/><path d="M12 8.5v4m0 3v.01"/>',
    inicio: '<path d="m4 11 8-6.5 8 6.5"/><path d="M6 9.5V19a1 1 0 0 0 1 1h3.5v-5h3v5H17a1 1 0 0 0 1-1V9.5"/>',
    atras: '<path d="M19 12H5m6-6-6 6 6 6"/>',
    recargar: '<path d="M20 12a8 8 0 1 1-2.6-5.9"/><path d="M20 4.5V9h-4.5"/>',
    entrar: '<path d="M9 4H6a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h3"/><path d="m15 8 4 4-4 4M19 12H9"/>',
    correo: '<rect x="3.5" y="5.5" width="17" height="13" rx="2"/><path d="m4 7 8 6 8-6"/>',
    copiar: '<rect x="8.5" y="8.5" width="11" height="11" rx="2"/><path d="M15.5 8.5V6a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v7.5a2 2 0 0 0 2 2h2.5"/>',
    soporte: '<path d="M4 14v-2a8 8 0 0 1 16 0v2"/><rect x="3" y="13" width="4" height="6" rx="1.5"/><rect x="17" y="13" width="4" height="6" rx="1.5"/><path d="M19 19c0 1.5-2 2.5-5 2.5"/>',
    ok: '<circle cx="12" cy="12" r="9"/><path d="m8.5 12.5 2.5 2.5 4.5-5"/>',
    sol: '<circle cx="12" cy="12" r="4"/><path d="M12 3v2m0 14v2M3 12h2m14 0h2M5.6 5.6 7 7m10 10 1.4 1.4M5.6 18.4 7 17m10-10 1.4-1.4"/>',
    luna: '<path d="M20 14.5A8 8 0 0 1 9.5 4 8 8 0 1 0 20 14.5z"/>',
      };  })();
  const IC_ALTA = (function () {
      return {
    ok: '<circle cx="12" cy="12" r="9"/><path d="m8.5 12.5 2.5 2.5 4.5-5"/>', vacio: '<circle cx="12" cy="12" r="9"/>', check: '<path d="m5 12.5 4.5 4.5L19 7.500"/>',
    alerta: '<path d="M12 4 3 19h18z"/><path d="M12 10v4m0 3v.01"/>', candado: '<rect x="5" y="11" width="14" height="9" rx="2"/><path d="M8 11V8a4 4 0 0 1 8 0v3"/>',
    ojo: '<path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z"/><circle cx="12" cy="12" r="3"/>', ojox: '<path d="M2.5 12S6 5.5 12 5.5c2 0 3.7.7 5.1 1.7M21.5 12s-1.2 2.2-3.4 3.9M9.9 18.2c.7.2 1.4.3 2.1.3"/><circle cx="12" cy="12" r="3"/><path d="m4 4 16 16"/>',
    reloj: '<circle cx="12" cy="12" r="8.5"/><path d="M12 7.500V12l3 2"/>', enlace: '<path d="M10 14a4 4 0 0 0 5.700 0l2.800-2.800a4 4 0 0 0-5.700-5.700L11.500 6.800"/><path d="M14 10a4 4 0 0 0-5.700 0l-2.800 2.800a4 4 0 0 0 5.700 5.700l1.300-1.300"/>',
    correo: '<rect x="3.500" y="5.500" width="17" height="13" rx="2"/><path d="m4 7 8 6 8-6"/>', entrar: '<path d="M9 4H6a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h3"/><path d="m15 8 4 4-4 4M19 12H9"/>',
    guardar: '<path d="M5 3h11l5 5v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z"/><path d="M8 3v4h7"/><path class="dib" pathLength="1" d="M7 21v-6a1 1 0 0 1 1-1h8a1 1 0 0 1 1 1v6"/>',
    soporte: '<path d="M4 14v-2a8 8 0 0 1 16 0v2"/><rect x="3" y="13" width="4" height="6" rx="1.500"/><rect x="17" y="13" width="4" height="6" rx="1.500"/>',
    llave: '<circle cx="8" cy="15" r="4"/><path d="m11 12 8-8m-3 3 3 3"/>', recargar: '<path d="M20 12a8 8 0 1 1-2.600-5.900"/><path d="M20 4.500V9h-4.500"/>',
    sol: '<circle cx="12" cy="12" r="4"/><path d="M12 3v2m0 14v2M3 12h2m14 0h2M5.600 5.600 7 7m10 10 1.400 1.400M5.600 18.400 7 17m10-10 1.400-1.400"/>', luna: '<path d="M20 14.500A8 8 0 0 1 9.500 4 8 8 0 1 0 20 14.500z"/>',
      };  })();
  const IC_OTP = { luna: '<path d="M20 14.5A8 8 0 0 1 9.5 4 8 8 0 1 0 20 14.5z"/>', sol: '<circle cx="12" cy="12" r="4"/><path d="M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1.4 1.4m11.2 11.2L19 19M5 19l1.4-1.4m11.2-11.2L19 5"/>' };
  const IC = MARCO === 'alta' ? IC_ALTA : MARCO === 'otp' ? IC_OTP : IC_ESTADO;
  const ico = (n, c = '') => `<svg class="icon ${c}" viewBox="0 0 24 24" aria-hidden="true">${IC[n]}</svg>`;

  function pintarIconos(raiz = document) {
    raiz.querySelectorAll('i[data-icono]').forEach((i) => { i.outerHTML = ico(i.dataset.icono, i.dataset.clase || ''); });
  }

  /* Arte del diseño Contorno (ppa_pantallas_sistema.html, función arte). */
  function arte() {
    document.querySelectorAll('svg[data-arte]').forEach((s) => {
      s.innerHTML = `
  <path class="ln" d="M96 75H40"/><path class="ln" d="M224 75h56"/>
  <g class="flota"><circle class="nodo" cx="34" cy="75" r="10"/><circle cx="34" cy="75" r="3" fill="var(--vx-p1)"/></g>
  <g class="flota b"><circle class="nodo o" cx="286" cy="75" r="10"/><circle cx="286" cy="75" r="3" fill="var(--vx-p2)"/></g>
  <circle class="anillo" cx="160" cy="75" r="68"/>
  <circle class="pulso" cx="160" cy="75" r="46"/>
  <circle class="core" cx="160" cy="75" r="46"/>
  <g class="orb"><circle cx="160" cy="7" r="3.5"/></g>
  <g transform="translate(160 75) scale(2.1) translate(-12 -12)" class="glifo">${IC[s.dataset.arte]}</g>
`;
    });
  }

  /* Claro/oscuro: misma clave que el login y los prototipos. */
  let modo = (() => { try { const m = localStorage.getItem('ppa-tema'); if (m === 'light' || m === 'dark') return m; } catch (e) { /* sin almacenamiento */ } return matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'; })();
  function aplicar() {
    $('#frame').dataset.mode = modo;
    $('#tema').innerHTML = MARCO === 'otp' ? `<svg class="icon" viewBox="0 0 24 24">${IC[modo === 'dark' ? 'sol' : 'luna']}</svg>` : ico(modo === 'dark' ? 'sol' : 'luna');
    try { localStorage.setItem('ppa-tema', modo); } catch (e) { /* sin almacenamiento */ }
  }

  /* Aviso flotante del prototipo. */
  let toastT;
  function toast(m) {
    const t = $('#toast'); if (!t) return;
    t.innerHTML = ico('ok') + `<span>${m}</span>`; t.hidden = false;
    clearTimeout(toastT); toastT = setTimeout(() => { t.hidden = true; }, 2600);
  }

  document.addEventListener('click', (e) => {
    if (e.target.closest('#tema')) { modo = modo === 'dark' ? 'light' : 'dark'; aplicar(); return; }
    const enlace = e.target.closest('a[data-ocupado]');
    if (enlace) { enlace.setAttribute('aria-busy', 'true'); enlace.querySelector('span').textContent = enlace.dataset.ocupado; return; }
    const b = e.target.closest('[data-accion]');
    if (!b) return;
    if (b.dataset.accion === 'soporte') { toast(T('soporteNoDisponible')); return; }
    if (b.dataset.accion === 'recargar') {
      if (b.dataset.ocupado) { b.setAttribute('aria-busy', 'true'); b.querySelector('span').textContent = b.dataset.ocupado; }
      location.reload();
    }
  });
  // Al volver con "atrás", el navegador puede mostrar la página congelada con el botón en proceso.
  addEventListener('pageshow', () => document.querySelectorAll('[aria-busy="true"]').forEach((b) => b.removeAttribute('aria-busy')));


  /* ---- Crear contraseña (ppa_alta_propuestas.html, diseño A) ----
     Mismas reglas que el prototipo (REQ). Quien de verdad las exige es la política del realm. */
  const REQ = [(v) => v.length >= 8, (v) => /[a-z]/.test(v) && /[A-Z]/.test(v), (v) => /\d/.test(v), (v) => /[^A-Za-z0-9]/.test(v)];
  function iniciarAlta() {
    const form = $('#pform');
    if (!form) { iniciarCreada(); return; }
    const pw1 = $('#pw1'), pw2 = $('#pw2'), er = $('#ferr'), btn = $('#guardar');
    const marcar = () => { document.querySelectorAll('#req li').forEach((li) => li.classList.toggle('ok', REQ[+li.dataset.req](pw1.value))); };
    const reqVer = () => { const falta = !REQ.every((f) => f(pw1.value)); $('#reqw').classList.toggle('on', falta && (document.activeElement === pw1 || pw1.value.length > 0)); };
    pw1.addEventListener('input', () => { marcar(); reqVer(); });
    form.addEventListener('input', () => { er.hidden = true; document.querySelectorAll('.inp.bad').forEach((x) => x.classList.remove('bad')); });
    pw1.addEventListener('focus', reqVer); pw1.addEventListener('blur', () => setTimeout(reqVer, 0));
    document.addEventListener('click', (e) => {
      const ey = e.target.closest('[data-eye]'); if (!ey) return;
      const i = document.getElementById(ey.dataset.eye), v = i.type === 'password';
      i.type = v ? 'text' : 'password'; ey.innerHTML = ico(v ? 'ojox' : 'ojo');
      ey.setAttribute('aria-label', v ? T('ocultarClave') : T('mostrarClave'));
    });
    form.addEventListener('submit', (e) => {
      const a = pw1.value, b = pw2.value;
      const mal = (k, inp) => { e.preventDefault(); er.querySelector('span').textContent = er.dataset[k]; er.hidden = false; inp.closest('.inp').classList.add('bad'); inp.focus(); };
      if (!REQ.every((f) => f(a))) return mal('weak', pw1);
      if (a !== b) return mal('match', pw2);
      if (btn.getAttribute('aria-busy') === 'true') { e.preventDefault(); return; }
      btn.setAttribute('aria-busy', 'true'); btn.querySelector('span').textContent = btn.dataset.ocupado;
      try {
        // Recuperación: Keycloak inicia sesión directo, no hay "Contraseña creada". Alta: info.ftl
        // necesita el correo, que Keycloak no le pasa.
        if (codigoOk === null) sessionStorage.setItem('ppa-clave-creada', form.querySelector('[name=username]').value);
        sessionStorage.removeItem('ppa-codigo-ok');
      } catch (x) { /* sin almacenamiento */ }
    });
    // Viene del código de recuperación ("1" la primera vez, "visto" después de un error del servidor):
    // pasos de recuperación y, solo la primera vez, el check de éxito.
    let codigoOk = null;
    try { codigoOk = sessionStorage.getItem('ppa-codigo-ok'); if (codigoOk !== null) sessionStorage.setItem('ppa-codigo-ok', 'visto'); } catch (x) { /* sin almacenamiento */ }
    if (codigoOk !== null) {
      $('[data-paso="0"]').textContent = form.dataset.pasoRecuperar;
      if (codigoOk === '1' && er.hidden) exito(form);
    }
    if (!er.hidden) (er.textContent && $('.inp.bad input') || pw1).focus();
  }
  // Check verde "Verificado con éxito" (textos de ppa_otp.html) antes del formulario.
  function exito(form) {
    const cab = $('[data-cabeza]'), h1 = cab.querySelector('h1'), sub = cab.querySelector('.sub');
    const antes = [h1.textContent, sub.textContent, h1.style.marginTop];
    const caja = document.createElement('span'); caja.className = 'ibox ok'; caja.innerHTML = ico('ok');
    cab.prepend(caja); h1.style.marginTop = '12px'; h1.textContent = form.dataset.exitoTitulo; sub.textContent = form.dataset.exitoTexto;
    form.classList.add('ppa-espera');
    setTimeout(() => {
      caja.remove(); [h1.textContent, sub.textContent, h1.style.marginTop] = antes;
      form.classList.remove('ppa-espera'); $('#pw1').focus();
    }, matchMedia('(prefers-reduced-motion: reduce)').matches ? 600 : 1400);
  }
  // "Contraseña creada" (info.ftl): completa el correo guardado al crear la contraseña.
  function iniciarCreada() {
    const p = $('[data-creada-texto]'); if (!p) return;
    let correo = null;
    try { correo = sessionStorage.getItem('ppa-clave-creada'); sessionStorage.removeItem('ppa-clave-creada'); } catch (x) { /* sin almacenamiento */ }
    if (correo) p.textContent = p.dataset.creadaTexto.replace('{0}', correo);
  }

  /* ---- Código de recuperación (ppa_otp.html) ----
     La animación es la del prototipo (función tick), con los dígitos que escribe la persona.
     En reposo se dibuja el instante 0 (fila de cajas, la activa con cursor). Al enviar corre el tramo
     "verificando" (abrirse en rombo, girar y juntarse) y recién ahí se envía el formulario: el éxito
     lo decide el servidor, y el check se muestra en la pantalla siguiente (marca ppa-codigo-ok). */
  function iniciarOtp() {
    const form = $('#view'), input = $('#codigo'), btn = $('#btn');
    const boxes = [...document.querySelectorAll('.box')];
    const digs = boxes.map((b) => b.querySelector('.d'));
    const sparks = [...document.querySelectorAll('.core .spk')];
    const RM = matchMedia('(prefers-reduced-motion: reduce)').matches;
    // Constantes de ppa_otp.html (segundos y píxeles).
    const GAP = 10, T_SPREAD = [1.0, 1.4], T_ORBIT_START = 1.4, T_COLLAPSE = [2.8, 3.35], T_VERIFY = 3.5;
    const ORBIT_R = 58, ORBIT_PERIOD = 2.4;
    const clamp = (v, a = 0, b = 1) => Math.min(b, Math.max(a, v));
    const seg = (t, a, b) => clamp((t - a) / (b - a));
    const lerp = (a, b, u) => a + (b - a) * u;
    const inOut = (u) => (u < 0.5 ? 2 * u * u : 1 - 2 * (1 - u) * (1 - u));
    const outQ = (u) => 1 - (1 - u) * (1 - u);
    function bezier(x1, y1, x2, y2) {
      const cx = 3 * x1, bx = 3 * (x2 - x1) - cx, ax = 1 - cx - bx, cy = 3 * y1, by = 3 * (y2 - y1) - cy, ay = 1 - cy - by;
      const X = (s) => ((ax * s + bx) * s + cx) * s, Y = (s) => ((ay * s + by) * s + cy) * s;
      return (x) => { let s = x; for (let i = 0; i < 6; i++) { const d = (3 * ax * s + 2 * bx) * s + cx; if (Math.abs(d) < 1e-6) break; s -= (X(s) - x) / d; } return Y(clamp(s)); };
    }
    const bez = bezier(0.4, 0, 0.2, 1);
    let size = 44;
    const medir = () => { size = boxes[0].offsetWidth || 44; };
    const rowX = (i) => (i - (boxes.length - 1) / 2) * (size + GAP);
    const rombo = [[-1, 0], [-0.5, -0.866], [-0.5, 0.866], [0.5, -0.866], [0.5, 0.866], [1, 0]];
    const theta = (t) => {
      if (RM || t < T_ORBIT_START) return 0;
      const base = ((Math.min(t, T_COLLAPSE[0]) - T_ORBIT_START) / ORBIT_PERIOD) * 360;
      return base + 60 * outQ(seg(t, T_COLLAPSE[0], T_COLLAPSE[1]));
    };
    // Dibuja el instante t (0 = reposo; T_SPREAD[0]..T_COLLAPSE[1] = verificando).
    function tick(t) {
      medir();
      const v = input.value;
      const sp = RM ? 0 : bez(seg(t, T_SPREAD[0], T_SPREAD[1]));
      const col = RM ? 0 : inOut(seg(t, T_COLLAPSE[0], T_COLLAPSE[1]));
      const th = (theta(t) * Math.PI) / 180;
      const enviando = t > 0;
      boxes.forEach((b, i) => {
        let x, y;
        if (t < T_COLLAPSE[1]) {
          const R = ORBIT_R * (1 - col);
          const bx = lerp(rowX(i), rombo[i][0] * R, sp), by = lerp(0, rombo[i][1] * R, sp);
          const rx = bx * Math.cos(th) - by * Math.sin(th), ry = bx * Math.sin(th) + by * Math.cos(th);
          x = sp >= 1 ? rx : bx; y = sp >= 1 ? ry : by;
        } else { x = 0; y = 0; }
        const orbiting = !RM && t >= T_ORBIT_START && t < T_COLLAPSE[1];
        const phi = orbiting ? -theta(t) * 0.66 : 0;
        const breath = orbiting ? 1 + 0.05 * Math.sin(((t - T_ORBIT_START) / ORBIT_PERIOD) * Math.PI * 4 + i * 1.57) : 1;
        b.style.transform = `translate(${x.toFixed(2)}px, ${y.toFixed(2)}px) rotate(${phi.toFixed(2)}deg) scale(${breath.toFixed(3)})`;
        const fade = 1 - seg(t, T_COLLAPSE[0], T_COLLAPSE[1]);
        digs[i].textContent = v[i] || '';
        digs[i].style.opacity = v[i] ? fade.toFixed(3) : '0';
        digs[i].style.transform = 'scale(1)';
        b.classList.toggle('act', !enviando && document.activeElement === input && i === Math.min(v.length, boxes.length - 1) && v.length < boxes.length);
        b.classList.toggle('last', !enviando && i === boxes.length - 1 && v.length === boxes.length);
        b.classList.toggle('col', t >= T_COLLAPSE[0] + 0.25 && t < T_VERIFY + 0.15);
        const comet = RM ? 0 : clamp(seg(t, T_ORBIT_START, T_ORBIT_START + 0.3)) * (1 - seg(t, T_COLLAPSE[0], T_COLLAPSE[0] + 0.4));
        b.style.setProperty('--comet', comet.toFixed(3));
        b.style.setProperty('--ang', `${(theta(t) * 1.6 + i * 90).toFixed(1)}deg`);
      });
      sparks.forEach((sk) => { sk.style.opacity = '0'; });
      // Textos e ícono en el estado "por verificar" (f = 0 del prototipo): el de éxito queda oculto.
      const set = (id, op, tr) => { const el = $(id); el.style.opacity = op; el.style.transform = tr; };
      set('#tA', '1', 'translateY(0px)'); $('#tA').style.filter = RM ? '' : 'blur(0px)';
      set('#tB', '0', 'translateY(6px)'); set('#sA', '1', 'translateY(0px)'); set('#sB', '0', 'translateY(6px)');
      set('#icKey', '1', 'scale(1)'); set('#icOk', '0', 'scale(0.75)');
      btn.dataset.s = enviando ? 'verify' : 'idle';
      btn.classList.toggle('full', !enviando && v.length === boxes.length);
      btn.style.opacity = !enviando && v.length < boxes.length ? '0.7' : '';
    }

    // Solo dígitos, máximo 6.
    input.addEventListener('input', () => { input.value = input.value.replace(/\D/g, '').slice(0, 6); tick(0); });
    input.addEventListener('focus', () => tick(0)); input.addEventListener('blur', () => tick(0));
    addEventListener('resize', () => tick(0));

    let enviado = false;
    form.addEventListener('submit', (e) => {
      if (enviado) return;
      e.preventDefault();
      if (input.value.length !== boxes.length) { input.focus(); return; }
      enviado = true; input.readOnly = true;
      try { sessionStorage.setItem('ppa-codigo-ok', '1'); } catch (x) { /* sin almacenamiento */ }
      $('#sr').textContent = btn.querySelector('.l.v').textContent;
      const fin = () => { form.submit(); };
      if (RM) { tick(1); fin(); return; }
      // Tramo "verificando" del prototipo (1.0 s -> 3.35 s) a 1.5x de velocidad.
      const t0 = performance.now();
      const paso = (now) => {
        const t = T_SPREAD[0] + ((now - t0) / 1000) * 1.5;
        tick(Math.min(t, T_COLLAPSE[1]));
        if (t < T_COLLAPSE[1]) requestAnimationFrame(paso); else fin();
      };
      requestAnimationFrame(paso);
    });

    // Si el servidor devolvió un error, el código no sirvió: se borra la marca de éxito.
    if (form.hasAttribute('data-error')) { try { sessionStorage.removeItem('ppa-codigo-ok'); } catch (x) { /* sin almacenamiento */ } }

    // Cuenta regresiva de "Reenviar"; al llegar a 0 aparece el botón.
    const cnt = $('#cnt'), reenviar = $('#reenviar');
    let falta = parseInt(form.dataset.espera, 10) || 0;
    const fmt = (s) => `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;
    const contar = () => {
      if (falta > 0) { cnt.textContent = cnt.dataset.texto.replace('{0}', fmt(falta)); cnt.hidden = false; reenviar.hidden = true; }
      else { cnt.hidden = true; reenviar.hidden = false; clearInterval(reloj); }
    };
    const reloj = setInterval(() => { falta = Math.max(0, falta - 1); contar(); }, 1000);
    contar();

    input.focus(); tick(0);
  }

  function iniciar() {
    pintarIconos(); arte(); aplicar();
    if (MARCO === 'alta' && typeof iniciarAlta === 'function') iniciarAlta();
    if (MARCO === 'otp' && typeof iniciarOtp === 'function') iniciarOtp();
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', iniciar); else iniciar();
})();
