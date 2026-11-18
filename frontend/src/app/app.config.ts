import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, inject, provideAppInitializer, provideZoneChangeDetection } from '@angular/core';
import { provideRouter, withHashLocation } from '@angular/router';

import { bearerInterceptor } from './core/api.interceptor';
import { SessionService } from './core/session.service';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    // Rutas con hash (#/enf/pacientes): son las del mockup y evitan configurar nginx para las rutas de la SPA.
    provideRouter(routes, withHashLocation()),
    provideHttpClient(withInterceptors([bearerInterceptor])),
    // El inicio de sesion (Keycloak) y la sesion se resuelven antes de pintar la primera pantalla.
    provideAppInitializer(() => inject(SessionService).init()),
  ],
};
