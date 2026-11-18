import { Injectable } from '@angular/core';
import Keycloak from 'keycloak-js';

interface AuthConfig {
  url: string;
  realm: string;
  clientId: string;
}

// Inicio de sesion con Keycloak (docs/login-keycloak). La app no tiene formulario de acceso: si no hay sesion,
// el navegador va al login del tema "auditor" y vuelve con un codigo que keycloak-js canjea (PKCE).
// El token vive solo en memoria: recargar la pagina lo renueva sin pedir la clave otra vez mientras dure la sesion SSO.
@Injectable({ providedIn: 'root' })
export class AuthService {
  private keycloak: Keycloak | null = null;

  // Lee /config.json (lo escribe nginx con las variables del compose), va al login si hace falta y deja el token listo.
  async init(): Promise<void> {
    const response = await fetch('/config.json', { cache: 'no-store' });
    if (!response.ok) {
      throw new Error('No se pudo leer /config.json');
    }
    const config = (await response.json()) as AuthConfig;
    this.keycloak = new Keycloak({ url: config.url, realm: config.realm, clientId: config.clientId });
    await this.keycloak.init({ onLoad: 'login-required', pkceMethod: 'S256', checkLoginIframe: false });
  }

  // Token vigente: si vence en menos de 30 s se renueva antes de devolverlo. Si la renovacion falla, vuelve al login.
  async token(): Promise<string | null> {
    if (!this.keycloak?.authenticated) {
      return null;
    }
    try {
      await this.keycloak.updateToken(30);
    } catch {
      await this.keycloak.login();
      return null;
    }
    return this.keycloak.token ?? null;
  }

  // Cierra la sesion en Keycloak y vuelve al inicio de la app (que a su vez pedira el login).
  logout(): Promise<void> {
    return this.keycloak ? this.keycloak.logout({ redirectUri: window.location.origin + '/' }) : Promise.resolve();
  }
}
