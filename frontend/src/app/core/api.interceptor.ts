import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';

import { SessionService } from './session.service';

// Manda el usuario elegido en cada peticion al API. No es autenticacion (D26): el servidor toma de el su rol.
export const demoUserInterceptor: HttpInterceptorFn = (request, next) => {
  const id = inject(SessionService).currentId();
  if (id && request.url.startsWith('/api/')) {
    return next(request.clone({ setHeaders: { 'X-Demo-User': id } }));
  }
  return next(request);
};
