import { HttpErrorResponse } from '@angular/common/http';

export interface ApiFailure {
  message: string;
  // Mensajes por campo del formulario, cuando el servidor los trae.
  fields: Record<string, string>;
}

// Convierte un fallo de HTTP en lo que se le muestra a la persona: el mensaje del servidor o uno honesto, nunca el
// error crudo (RNF-04).
export function apiFailure(error: unknown): ApiFailure {
  if (error instanceof HttpErrorResponse) {
    if (error.status === 0) {
      return { message: 'No se pudo conectar con el servidor. Revise que la aplicación esté arriba e intente de nuevo.', fields: {} };
    }
    const body = error.error as { message?: string; fields?: Record<string, string> } | null;
    if (body && typeof body.message === 'string') {
      return { message: body.message, fields: body.fields ?? {} };
    }
  }
  return { message: 'No se pudo completar la acción. Intente de nuevo.', fields: {} };
}
