#!/usr/bin/env bash
# Falla si una variable usada en los yml o en docker-compose.yml no esta en .env.example.
set -euo pipefail
cd "$(dirname "$0")/.."

used=$(grep -rhoP '(?<!\$)\$\{\K[A-Z][A-Z0-9_]*' docker-compose.yml backend/src/main/resources/*.yml | sort -u)
documented=$(grep -oE '^[A-Z][A-Z0-9_]*=' .env.example | tr -d '=' | sort -u)

missing=$(comm -23 <(echo "$used") <(echo "$documented"))
if [ -n "$missing" ]; then
  echo "Variables sin documentar en .env.example:" >&2
  echo "$missing" >&2
  exit 1
fi
echo "Todas las variables estan en .env.example ($(echo "$used" | wc -l) revisadas)."
