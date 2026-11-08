export interface MenuLink {
  text: string;
  path: string;
}

export interface MenuModule {
  text: string;
  icon: string;
  path?: string;
  sub?: MenuLink[];
}

// ROLES.enf del mockup. El menu del administrador no se construye en esta entrega.
export const NURSING_MENU: MenuModule[] = [
  { text: 'Pacientes', icon: 'pacientes', path: '/enf/pacientes' },
  {
    text: 'Registro diario',
    icon: 'registro',
    sub: [
      { text: 'Nuevo registro', path: '/enf/registro/nuevo' },
      { text: 'Historial', path: '/enf/registro/historial' },
    ],
  },
  {
    text: 'Informes',
    icon: 'informes',
    sub: [
      { text: 'Generar informe', path: '/enf/informes/generar' },
      { text: 'En revisión', path: '/enf/informes/revision' },
      { text: 'Aprobados', path: '/enf/informes/aprobados' },
    ],
  },
];

// ROLES.med del mockup, sin «Armar informe»: no aparece hasta que exista esa pantalla (Pieza 6 de docs/PLAN_M3.md).
export const DOCTOR_MENU: MenuModule[] = [
  { text: 'Mis pacientes', icon: 'pacientes', path: '/med/pacientes' },
  {
    text: 'Informes',
    icon: 'informes',
    sub: [{ text: 'Recibidos', path: '/med/informes/recibidos' }],
  },
];
