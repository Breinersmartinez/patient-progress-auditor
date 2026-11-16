import { inject } from '@angular/core';
import { CanMatchFn, Router } from '@angular/router';

import { roleHome } from './roles';
import { SessionService } from './session.service';

// Las pantallas de cada rol son las suyas (D26): si el usuario elegido no es del rol de la ruta, se lleva al
// inicio de su rol (`#/enf/pacientes` o `#/med/pacientes`), como el mockup, en el que el rol sale de la URL.
// Un rol sin pantallas (administracion, por ahora) no se redirige: iria al inicio de enfermeria y la guarda lo
// devolveria, en bucle. La pantalla le muestra el aviso de que su rol aun no tiene pantallas.
// No es la barrera de seguridad: lo que el rol no puede ver, el servidor lo responde con 404 o 403.
const ROLES_WITH_SCREENS = ['ENFERMERIA', 'MEDICO'];

export const roleGuard = (role: string): CanMatchFn => () => {
  const current = inject(SessionService).session()?.role;
  return !current || current === role || !ROLES_WITH_SCREENS.includes(current) ? true : inject(Router).parseUrl(roleHome(current));
};
