# Stage 1: Build the application
FROM node:20-alpine AS builder

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# Stage 2: Serve it with nginx
FROM nginx:1.28-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
# Al arrancar, escribe /config.json con la direccion de Keycloak (variables KC_* del compose): la app la lee en el navegador.
COPY config-json.sh /docker-entrypoint.d/40-config-json.sh
RUN chmod +x /docker-entrypoint.d/40-config-json.sh
COPY --from=builder /app/dist/patient-progress-auditor-frontend/browser /usr/share/nginx/html

EXPOSE 80
