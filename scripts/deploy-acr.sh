#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COMPOSE_FILE="${COMPOSE_FILE:-$ROOT_DIR/deploy/docker-compose.acr.yml}"
SSL_COMPOSE_FILE="${SSL_COMPOSE_FILE:-$ROOT_DIR/deploy/docker-compose.acr.ssl.yml}"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/deploy/acr.env}"
EXAMPLE_ENV_FILE="$ROOT_DIR/deploy/acr.env.example"

if [[ ! -f "$ENV_FILE" ]]; then
  cp "$EXAMPLE_ENV_FILE" "$ENV_FILE"
  echo "Created $ENV_FILE from deploy/acr.env.example"
  echo "Review it before production use, especially passwords, JWT secret, and model API key."
fi

set -a
# shellcheck disable=SC1090
source "$ENV_FILE"
set +a

ACR_REGISTRY="${ACR_REGISTRY:-op-registry.cloudcc.cn}"
COMPOSE_ARGS=(-f "$COMPOSE_FILE")

if [[ "${SSL_ENABLED:-false}" == "true" ]]; then
  COMPOSE_ARGS+=(-f "$SSL_COMPOSE_FILE")
fi

if [[ -n "${ACR_USERNAME:-}" && -n "${ACR_PASSWORD:-}" ]]; then
  echo "Logging in to $ACR_REGISTRY as $ACR_USERNAME"
  printf '%s' "$ACR_PASSWORD" | docker login "$ACR_REGISTRY" -u "$ACR_USERNAME" --password-stdin
else
  echo "ACR_USERNAME/ACR_PASSWORD not set; using existing docker login state for $ACR_REGISTRY"
fi

docker compose --env-file "$ENV_FILE" "${COMPOSE_ARGS[@]}" pull
docker compose --env-file "$ENV_FILE" "${COMPOSE_ARGS[@]}" up -d
docker compose --env-file "$ENV_FILE" "${COMPOSE_ARGS[@]}" ps

cat <<EOF

CICI ACR deployment started.

Frontend: http://127.0.0.1:${FRONTEND_PORT:-80}
Backend:  http://127.0.0.1:${BACKEND_PORT:-8080}/actuator/health
RabbitMQ: http://127.0.0.1:${RABBITMQ_MANAGEMENT_PORT:-15672}
Qdrant:   http://127.0.0.1:${QDRANT_PORT:-6333}

Useful commands:
  docker compose --env-file "$ENV_FILE" ${COMPOSE_ARGS[*]} logs -f
  docker compose --env-file "$ENV_FILE" ${COMPOSE_ARGS[*]} down
EOF
