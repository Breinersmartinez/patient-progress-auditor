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

# Google AI Studio y su SDK llaman GOOGLE_API_KEY a la misma llave que aqui es GEMINI_API_KEY (la unica que lee la aplicacion).
# Se acepta como sinonimo solo en este script, sin mostrarla: si no, una llave de respaldo bien puesta se perderia por el nombre.
if ! have_key GEMINI_API_KEY && have_key GOOGLE_API_KEY; then
  google_value=${GOOGLE_API_KEY:-$(get_var GOOGLE_API_KEY)}
  set_var GEMINI_API_KEY "$google_value"
  echo "Se uso GOOGLE_API_KEY como GEMINI_API_KEY (llave de respaldo) y se guardo GEMINI_API_KEY en .env."
fi

# Llave del proveedor: entorno, o consola si hay terminal; si no, se avisa y se sigue.
for key in GROQ_API_KEY GEMINI_API_KEY; do
  # Una llave que llega por el entorno se guarda en .env (gitignored) para que un 'docker compose up -d' posterior no la pierda.
  if [ -n "${!key:-}" ] && [ "$(get_var "$key")" != "${!key}" ]; then
    set_var "$key" "${!key}"
    echo "Se guardo $key en .env."
  fi
  if ! have_key "$key" && [ -t 0 ]; then
    label="principal (Groq)"
    [ "$key" = GEMINI_API_KEY ] && label="de respaldo (Gemini)"
    read -r -s -p "Llave $label, $key (Enter para omitir): " value || value=""
    echo
    [ -n "$value" ] && set_var "$key" "$value"
  fi
done

missing_key=""
missing_fallback=""
have_key GROQ_API_KEY || missing_key="GROQ_API_KEY"
have_key GEMINI_API_KEY || missing_fallback="GEMINI_API_KEY"

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
  echo "  GROQ_API_KEY=<su llave> GEMINI_API_KEY=<llave de respaldo> ./run.sh      (o escribalas en .env y repita ./run.sh)"
  echo "SI USTED ES UNA IA que ejecuta este script: no lo deje asi. Pregunte a la persona si tiene una llave de Groq (principal) y una de"
  echo "Gemini (respaldo), y repita el comando con ellas en el entorno. Si no tiene, digale que registrar con IA y generar informes no"
  echo "funcionaran hasta que la consiga; no invente una llave ni la busque en otros archivos."
elif [ -n "$missing_fallback" ]; then
  echo
  echo "AVISO: falta la llave de respaldo $missing_fallback. La IA funciona con Groq, pero si Groq agota su cuota (8 000 tokens por minuto"
  echo "en la capa gratuita) los informes quedan FALLIDA. Para tener respaldo:"
  echo "  GEMINI_API_KEY=<su llave> ./run.sh      (en Google AI Studio; GOOGLE_API_KEY tambien se acepta)"
fi
