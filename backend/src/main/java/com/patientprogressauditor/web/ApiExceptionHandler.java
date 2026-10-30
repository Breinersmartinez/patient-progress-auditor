package com.patientprogressauditor.web;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.patientprogressauditor.session.ApiError;

// Todo error de la API llega a la pantalla como { message, fields? }: sin stack trace ni texto del framework (RNF-04).
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalidBody(MethodArgumentNotValidException e) {
        Map<String, String> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(new ApiError("Revise los datos indicados.", fields));
    }

    @ExceptionHandler(FieldException.class)
    ResponseEntity<ApiError> invalidField(FieldException e) {
        return ResponseEntity.status(e.status()).contentType(MediaType.APPLICATION_JSON).body(new ApiError(e.getMessage(), e.fields()));
    }

    @ExceptionHandler(BusyException.class)
    ResponseEntity<ApiError> busy(BusyException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).header("Retry-After", "30").contentType(MediaType.APPLICATION_JSON).body(new ApiError(e.getMessage()));
    }

    @ExceptionHandler(NotFoundException.class)
    ResponseEntity<ApiError> notFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON).body(new ApiError(e.getMessage()));
    }

    @ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class })
    ResponseEntity<ApiError> unreadable(Exception e) {
        return ResponseEntity.badRequest().contentType(MediaType.APPLICATION_JSON).body(new ApiError("No se pudo leer lo enviado. Revise los datos e intente de nuevo."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception e) throws Exception {
        // 401 y 403 los resuelve la cadena de seguridad con sus propios mensajes.
        if (e instanceof AccessDeniedException || e instanceof AuthenticationException) {
            throw e;
        }
        // Errores del propio Spring MVC (ruta o metodo inexistente, tipo de contenido): su estado, con un mensaje nuestro.
        if (e instanceof ErrorResponse mvc) {
            String message = mvc.getStatusCode().value() == 404 ? "No encontramos lo que busca." : "No se pudo completar la solicitud.";
            return ResponseEntity.status(mvc.getStatusCode()).contentType(MediaType.APPLICATION_JSON).body(new ApiError(message));
        }
        log.error("Error no previsto en la API", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.APPLICATION_JSON)
                .body(new ApiError("Ocurrió un error inesperado. Intente de nuevo en unos minutos."));
    }
}
