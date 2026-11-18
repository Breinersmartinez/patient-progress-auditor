#!/bin/sh
# Escribe /config.json con los datos publicos de Keycloak. No lleva secretos: el client es publico (PKCE).
cat > /usr/share/nginx/html/config.json <<JSON
{"url":"${KC_PUBLIC_URL:-http://localhost:7070}","realm":"${KC_REALM:-auditor}","clientId":"${KC_CLIENT_ID:-auditor-web}"}
JSON
