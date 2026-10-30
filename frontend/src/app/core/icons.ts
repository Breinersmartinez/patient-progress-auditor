// Catalogo de iconos copiado tal cual del mockup (lineas 198 a 214 y 502 a 510 de auditor.html).
// Los iconos nuevos usan el mismo trazo (stroke-width 1.8, round) y se agregan aqui (regla F2).
export const ICONOS: Record<string, string> = {
  descargar: '<path d="M4 15v3a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-3"/><g class="i-mueve"><path d="M12 4v11"/><path d="m7.5 10.5 4.5 4.5 4.5-4.5"/></g>',
  subir: '<path d="M4 15v3a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-3"/><g class="i-mueve"><path d="M12 15V4"/><path d="m7.5 8.5 4.5-4.5 4.5 4.5"/></g>',
  guardar: '<path d="M5 3h11l5 5v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z"/><path d="M8 3v4h7"/><path class="i-dibuja" pathLength="1" d="M7 21v-6a1 1 0 0 1 1-1h8a1 1 0 0 1 1 1v6"/>',
  eliminar: '<g class="i-tapa"><path d="M4 7h16"/><path d="M9 7V5a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/></g><path d="m6 7 1 12a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2l1-12"/><path d="M10 11v6m4-6v6"/>',
  aprobar: '<circle cx="12" cy="12" r="9"/><path class="i-dibuja" pathLength="1" d="m8.5 12.5 2.5 2.5 4.5-5"/>',
  rechazar: '<circle cx="12" cy="12" r="9"/><path class="i-gira" d="m9 9 6 6m0-6-6 6"/>',
  publicar: '<path d="M4 9.5h3l7-4v13l-7-4H4a1 1 0 0 1-1-1v-3a1 1 0 0 1 1-1z"/><path d="M7 14.5V19a1.5 1.5 0 0 0 3 0v-2.8"/><path class="i-onda" d="M17 9.5a3.5 3.5 0 0 1 0 5"/><path class="i-onda" d="M19.5 7a7 7 0 0 1 0 10"/>',
  enviar: '<g class="i-vuela"><path d="M21 3 3 10.5l7.5 3 3 7.5z"/><path d="m10.5 13.5 5-5"/></g>',
  ia: '<path class="i-pulso" d="M11 4.5 12.6 9l4.4 1.5-4.4 1.5-1.6 4.5L9.4 12 5 10.5 9.4 9z"/><path class="i-pulso" d="M18 3v4m-2-2h4"/><path class="i-pulso" d="M5.5 16.5v3M4 18h3"/>',
  anillo: '<circle cx="12" cy="12" r="9" opacity=".25"/><path d="M21 12a9 9 0 0 0-9-9"/>',
  contraer: '<path d="m14.5 7-5 5 5 5"/>',
  desplegar: '<path d="m7 10 5 5 5-5"/>',
  perfil: '<circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/>',
  preferencias: '<path d="M4 6h9m4 0h3M4 12h3m4 0h9M4 18h11m4 0h1"/><circle cx="15" cy="6" r="2"/><circle cx="9" cy="12" r="2"/><circle cx="17" cy="18" r="2"/>',
  salir: '<path d="M9 4H6a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h3"/><path d="m15 8 4 4-4 4M19 12H9"/>',
  pacientes: '<circle cx="9" cy="8" r="3.5"/><path d="M2.5 20a6.5 6.5 0 0 1 13 0"/><path d="M16 4.6a3.5 3.5 0 0 1 0 6.8M18 14.2a6.5 6.5 0 0 1 3.5 5.8"/>',
  registro: '<path d="M5 3h10l4 4v14H5z"/><path d="M14 3v5h5"/><path d="M8.5 13h7M8.5 17h5"/>',
  informes: '<path d="M5 20V4"/><path d="M5 20h15"/><path d="M9 16v-4m4 4V8m4 8v-6"/>',
  usuarios: '<circle cx="12" cy="8" r="3.5"/><path d="M5 20a7 7 0 0 1 14 0"/><path d="M19 3v4m-2-2h4"/>',
  permisos: '<path d="M12 3 4.5 6v5.5c0 4.5 3 8 7.5 9.5 4.5-1.5 7.5-5 7.5-9.5V6z"/><path d="m9 12 2 2 4-4"/>',
  trazas: '<path d="M3 12h4l2.5-6 4 12 2.5-6H21"/>',
  campana: '<path d="M6 16v-5a6 6 0 0 1 12 0v5l1.5 2h-15z"/><path d="M10 21h4"/>',
  buscar: '<circle cx="11" cy="11" r="6.5"/><path d="m16 16 4.5 4.5"/>',
};

// Los que se animan cuando el boton esta ocupado (btnProceso del mockup).
export const ICONOS_ANIMADOS = ['descargar', 'subir', 'guardar', 'eliminar', 'aprobar', 'rechazar', 'publicar', 'enviar', 'ia'];
