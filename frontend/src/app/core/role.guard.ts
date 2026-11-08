import { inject } from '@angular/core';
import { CanMatchFn, Router } from '@angular/router';

import { roleHome } from './roles';
import { SessionService } from './session.service';

// Las pantallas de cada rol son las suyas (D26): si el usuario elegido no es del rol de la ruta, se lleva al
// inicio de su rol (`#/enf/pacientes` o `#/med/pacientes`), como el mockup, en el que el rol sale de la URL.
// No es la barrera de seguridad: lo que el rol no puede ver, el servidor lo responde con 404 o 403.
export const roleGuard = (role: string): CanMatchFn => () => {
  const current = inject(SessionService).session()?.role;
  return !current || current === role ? true : inject(Router).parseUrl(roleHome(current));
};
