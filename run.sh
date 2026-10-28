#!/usr/bin/env bash
# Arranque en frio: verifica Docker, prepara .env y levanta base, backend y frontend.
# Nunca se queda esperando entrada si no hay terminal.
set -euo pipefail
cd "$(dirname "$0")"

fail() {
  echo "ERROR: $*" >&2
  exit 1
}

# Escribe KEY=valor en .env, reemplazando la linea si ya existe.
set_var() {
  local tmp
  tmp=$(mktemp)
  K="$1" V="$2" awk 'BEGIN { FS = OFS = "=" } $1 == ENVIRON["K"] { print ENVIRON["K"] "=" ENVIRON["V"]; found = 1; next } { print } END { if (!found) print ENVIRON["K"] "=" ENVIRON["V"] }' .env > "$tmp"
  cat "$tmp" > .env
  rm -f "$tmp"
}

get_var() {
  grep -E "^$1=" .env | tail -1 | cut -d= -f2- || true
}

# La llave puede venir del entorno o de .env.
have_key() {
  [ -n "${!1:-}" ] || [ -n "$(get_var "$1")" ]
}

port_in_use() {
  (exec 3<>"/dev/tcp/127.0.0.1/$1") 2>/dev/null
}

free_port_from() {
  local port=$1
  while port_in_use "$port"; do port=$((port + 1)); done
  echo "$port"
}

command -v docker > /dev/null 2>&1 || fail "Docker no esta instalado. Instale Docker con Compose v2."
docker compose version > /dev/null 2>&1 || fail "Falta Docker Compose v2 (el comando 'docker compose')."
docker info > /dev/null 2>&1 || fail "El servicio de Docker no responde. Inicielo e intente de nuevo."

if [ ! -f .env ]; then
  cp .env.example .env
  echo "Se creo .env desde .env.example."
fi

if [ -z "$(get_var DB_PASSWORD)" ]; then
  if docker volume ls -q | grep -qx "${COMPOSE_PROJECT_NAME:-patient-progress-auditor}_pgdata"; then
    fail "Existe un volumen de base de datos creado con otra contrasena. Si no necesita esos datos, ejecute 'docker compose down -v' y repita."
  fi
  set_var DB_PASSWORD "$(od -An -N24 -tx1 /dev/urandom | tr -d ' \n')"
  echo "Se genero DB_PASSWORD en .env."
fi

# Si el proyecto aun no corre y el puerto esta ocupado, se usa el siguiente libre.
if [ -z "$(docker compose ps -q 2> /dev/null)" ]; then
  for var in BACKEND_PORT FRONTEND_PORT; do
    current=$(get_var "$var")
    current=${current:-$([ "$var" = BACKEND_PORT ] && echo 8080 || echo 4200)}
    free=$(free_port_from "$current")
    if [ "$free" != "$current" ]; then
      echo "El puerto $current esta ocupado; $var pasa a $free."
      set_var "$var" "$free"
    fi
  done
fi

# Llave del proveedor: entorno, o consola si hay terminal; si no, se avisa y se sigue.
for key in GEMINI_API_KEY GROQ_API_KEY; do
  if ! have_key "$key" && [ -t 0 ]; then
    label="principal (Gemini)"
    [ "$key" = GROQ_API_KEY ] && label="de respaldo (Groq)"
    read -r -s -p "Llave $label, $key (Enter para omitir): " value || value=""
    echo
    [ -n "$value" ] && set_var "$key" "$value"
  fi
done

missing_key=""
have_key GEMINI_API_KEY || missing_key="GEMINI_API_KEY"

if ! docker compose up --build -d --wait --wait-timeout 300; then
  docker compose ps || true
  docker compose logs --tail 40 || true
  fail "Los servicios no quedaron sanos. Revise los registros de arriba."
fi

frontend_port=$(get_var FRONTEND_PORT)
backend_port=$(get_var BACKEND_PORT)
echo
echo "La aplicacion esta arriba."
echo "  Interfaz:  http://localhost:${frontend_port:-4200}"
echo "  API:       http://localhost:${backend_port:-8080}/swagger-ui.html"
echo "  Detener:   docker compose down"
if [ -n "$missing_key" ]; then
  echo
  echo "AVISO: falta la llave $missing_key. La aplicacion funciona, pero las funciones de IA no hasta que la defina:"
  echo "  GEMINI_API_KEY=<su llave> ./run.sh      (o escribala en .env y repita ./run.sh)"
fi
