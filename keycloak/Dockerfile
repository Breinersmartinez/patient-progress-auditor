# Imagen de Keycloak para el auditor (solo desarrollo local: HTTP, sin certificados).
# Etapa 0: compila la extensión del código de recuperación con Maven; no hace falta Java ni Maven en la máquina.
FROM maven:3.9.11-eclipse-temurin-21 AS extensiones
WORKDIR /src
COPY extensiones/codigo-recuperacion/ .
RUN --mount=type=cache,target=/root/.m2 mvn -q -B package

# Etapa 1: compila Keycloak para Postgres con la extensión y el tema dentro.
FROM quay.io/keycloak/keycloak:26.8.2 AS builder
ENV KC_DB=postgres \
    KC_HEALTH_ENABLED=true
COPY --from=extensiones /src/target/ppa-codigo-recuperacion.jar /opt/keycloak/providers/
COPY themes/ /opt/keycloak/themes/
RUN /opt/keycloak/bin/kc.sh build

# Etapa 2: imagen final. El realm se importa al arrancar solo si todavía no existe (ver README).
FROM quay.io/keycloak/keycloak:26.8.2
COPY --from=builder /opt/keycloak/ /opt/keycloak/
COPY realm/ /opt/keycloak/data/import/
ENTRYPOINT ["/opt/keycloak/bin/kc.sh"]
CMD ["start", "--optimized", "--import-realm"]
