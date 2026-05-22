#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/deploy/acr.env}"
DRY_RUN=false
PUSH_LATEST="${PUSH_LATEST:-true}"
PUSH_GIT_TAG="${PUSH_GIT_TAG:-true}"
ALLOW_DIRTY_RELEASE="${ALLOW_DIRTY_RELEASE:-false}"
RELEASE_TRAIN="${RELEASE_TRAIN:-2.0}"
CICI_RELEASE_VERSION="${CICI_RELEASE_VERSION:-${RELEASE_VERSION:-}}"

usage() {
  cat <<'EOF'
Usage: scripts/release-acr.sh [options]

Build and push AgentCiCi backend/frontend ACR images with one canonical version.

Options:
  --dry-run              Print the generated version and planned commands only.
  --version <version>    Use an explicit version instead of generating the next train tag.
  --train <train>        Release train for auto generation, default 2.0.
  --no-latest            Do not push the latest alias.
  --no-git-tag           Do not create or push the Git tag.
  -h, --help             Show this help.

Environment:
  ACR_IMAGE_PREFIX       Default op-registry.cloudcc.cn/cloudcc-ai-native
  ACR_REGISTRY           Default first path segment of ACR_IMAGE_PREFIX
  ACR_USERNAME           Optional docker login username
  ACR_PASSWORD           Optional docker login password
  CICI_PLATFORM          Default linux/amd64
  RELEASE_VERSION        Explicit version alias for CICI_RELEASE_VERSION
  CICI_RELEASE_VERSION   Explicit canonical release version
  PUSH_LATEST            true|false, default true
  PUSH_GIT_TAG           true|false, default true
  ALLOW_DIRTY_RELEASE    true|false, default false
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --dry-run)
      DRY_RUN=true
      shift
      ;;
    --version)
      CICI_RELEASE_VERSION="${2:-}"
      shift 2
      ;;
    --train)
      RELEASE_TRAIN="${2:-}"
      shift 2
      ;;
    --no-latest)
      PUSH_LATEST=false
      shift
      ;;
    --no-git-tag)
      PUSH_GIT_TAG=false
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown option: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ -f "$ENV_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
fi

ACR_IMAGE_PREFIX="${ACR_IMAGE_PREFIX:-op-registry.cloudcc.cn/cloudcc-ai-native}"
ACR_REGISTRY="${ACR_REGISTRY:-${ACR_IMAGE_PREFIX%%/*}}"
CICI_PLATFORM="${CICI_PLATFORM:-linux/amd64}"

run() {
  printf '+'
  printf ' %q' "$@"
  printf '\n'
  if [[ "$DRY_RUN" != "true" ]]; then
    "$@"
  fi
}

run_in_dir() {
  local dir="$1"
  shift
  printf '+ cd %q &&' "$dir"
  printf ' %q' "$@"
  printf '\n'
  if [[ "$DRY_RUN" != "true" ]]; then
    (cd "$dir" && "$@")
  fi
}

next_release_version() {
  local latest
  latest="$(
    git -C "$ROOT_DIR" tag --list "${RELEASE_TRAIN}.B*" |
      awk -v train="$RELEASE_TRAIN" '
        index($0, train ".B") == 1 {
          rest = substr($0, length(train) + 3)
          if (match(rest, /^[0-9]+/)) {
            print substr(rest, RSTART, RLENGTH)
          }
        }
      ' |
      sort -n |
      tail -1
  )"
  if [[ -z "$latest" ]]; then
    latest=0
  fi
  printf '%s.B%d' "$RELEASE_TRAIN" "$((latest + 1))"
}

if [[ -z "$CICI_RELEASE_VERSION" ]]; then
  CICI_RELEASE_VERSION="$(next_release_version)"
fi

if [[ ! "$CICI_RELEASE_VERSION" =~ ^[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$ ]]; then
  echo "Invalid release version for Docker tag: $CICI_RELEASE_VERSION" >&2
  exit 2
fi

GIT_COMMIT="$(git -C "$ROOT_DIR" rev-parse --short=12 HEAD)"
GIT_STATUS="$(git -C "$ROOT_DIR" status --porcelain)"

if [[ -n "$GIT_STATUS" && "$ALLOW_DIRTY_RELEASE" != "true" && "$DRY_RUN" != "true" ]]; then
  echo "Refusing release from a dirty worktree. Commit or stash changes, or set ALLOW_DIRTY_RELEASE=true." >&2
  git -C "$ROOT_DIR" status --short >&2
  exit 1
fi

if git -C "$ROOT_DIR" rev-parse -q --verify "refs/tags/$CICI_RELEASE_VERSION" >/dev/null; then
  echo "Git tag already exists: $CICI_RELEASE_VERSION" >&2
  exit 1
fi

BACKEND_IMAGE="$ACR_IMAGE_PREFIX/cici-backend:$CICI_RELEASE_VERSION"
FRONTEND_IMAGE="$ACR_IMAGE_PREFIX/cici-frontend:$CICI_RELEASE_VERSION"

BACKEND_TAGS=(-t "$BACKEND_IMAGE")
FRONTEND_TAGS=(-t "$FRONTEND_IMAGE")
if [[ "$PUSH_LATEST" == "true" ]]; then
  BACKEND_TAGS+=(-t "$ACR_IMAGE_PREFIX/cici-backend:latest")
  FRONTEND_TAGS+=(-t "$ACR_IMAGE_PREFIX/cici-frontend:latest")
fi

cat <<EOF
AgentCiCi release
  version:        $CICI_RELEASE_VERSION
  git commit:     $GIT_COMMIT
  image prefix:   $ACR_IMAGE_PREFIX
  platform:       $CICI_PLATFORM
  push latest:    $PUSH_LATEST
  push git tag:   $PUSH_GIT_TAG
  dry run:        $DRY_RUN
EOF

if [[ "$DRY_RUN" == "true" ]]; then
  echo
  echo "Dry run only; no build, push, or tag will be created."
fi

if [[ -n "${ACR_USERNAME:-}" && -n "${ACR_PASSWORD:-}" ]]; then
  run bash -lc "printf '%s' \"\$ACR_PASSWORD\" | docker login '$ACR_REGISTRY' -u \"\$ACR_USERNAME\" --password-stdin"
else
  echo "ACR_USERNAME/ACR_PASSWORD not set; using existing docker login state for $ACR_REGISTRY"
fi

run_in_dir "$ROOT_DIR/backend" mvn -q -Dmaven.repo.local=../.m2 -DskipTests package
run_in_dir "$ROOT_DIR/frontend" env VITE_CICI_APP_VERSION="$CICI_RELEASE_VERSION" npm run build

run docker buildx build \
  --platform "$CICI_PLATFORM" \
  -f "$ROOT_DIR/deploy/Dockerfile.backend" \
  --build-arg CICI_APP_VERSION="$CICI_RELEASE_VERSION" \
  --build-arg CICI_GIT_COMMIT="$GIT_COMMIT" \
  "${BACKEND_TAGS[@]}" \
  --push \
  "$ROOT_DIR"

run docker buildx build \
  --platform "$CICI_PLATFORM" \
  -f "$ROOT_DIR/deploy/Dockerfile.frontend" \
  --build-arg CICI_APP_VERSION="$CICI_RELEASE_VERSION" \
  --build-arg CICI_GIT_COMMIT="$GIT_COMMIT" \
  "${FRONTEND_TAGS[@]}" \
  --push \
  "$ROOT_DIR"

run docker buildx imagetools inspect "$BACKEND_IMAGE"
run docker buildx imagetools inspect "$FRONTEND_IMAGE"

if [[ "$PUSH_GIT_TAG" == "true" ]]; then
  run git -C "$ROOT_DIR" tag -a "$CICI_RELEASE_VERSION" -m "Release $CICI_RELEASE_VERSION"
  run git -C "$ROOT_DIR" push origin "$CICI_RELEASE_VERSION"
fi

cat <<EOF

Release version ready: $CICI_RELEASE_VERSION

Use this same value in deploy/acr.env:
  CICI_IMAGE_TAG=$CICI_RELEASE_VERSION
  CICI_APP_VERSION=$CICI_RELEASE_VERSION
EOF
