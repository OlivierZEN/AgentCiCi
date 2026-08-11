#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/deploy/acr.env}"
DRY_RUN=false
PUSH_LATEST="${PUSH_LATEST:-true}"
PUSH_GIT_TAG="${PUSH_GIT_TAG:-true}"
ALLOW_DIRTY_RELEASE="${ALLOW_DIRTY_RELEASE:-false}"
RELEASE_CHANNEL="${RELEASE_CHANNEL:-production}"
CICI_RELEASE_VERSION="${CICI_RELEASE_VERSION:-${RELEASE_VERSION:-}}"
EXPLICIT_VERSION=false
CHANNEL_EXPLICIT=false
INITIAL_PRODUCTION_VERSION="${INITIAL_PRODUCTION_VERSION:-2.0.1}"
MAX_MAJOR_VERSION=12
MAX_MINOR_VERSION=12
MAX_PATCH_VERSION=365

usage() {
  cat <<'EOF'
Usage: scripts/release-acr.sh [options]

Build and push AgentCiCi backend/frontend ACR images with one canonical version.

Options:
  --dry-run              Print the generated version and planned commands only.
  --version <version>    Use an explicit version instead of generating the next tag.
  --channel <channel>    Version channel: production or test, default production.
  --production           Generate the next production version.
  --test, --beta         Generate the next test beta version.
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
  RELEASE_CHANNEL        production|test, default production
  RELEASE_PRODUCTION_BASE
                         Approved current production version for a test release
                         when the production deployment/tag sync is pending;
                         the beta targets its next production version
  INITIAL_PRODUCTION_VERSION
                         First production version if no production tag exists, default 2.0.1
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
      EXPLICIT_VERSION=true
      shift 2
      ;;
    --channel)
      RELEASE_CHANNEL="${2:-}"
      CHANNEL_EXPLICIT=true
      shift 2
      ;;
    --production)
      RELEASE_CHANNEL=production
      CHANNEL_EXPLICIT=true
      shift
      ;;
    --test|--beta)
      RELEASE_CHANNEL=test
      CHANNEL_EXPLICIT=true
      shift
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

normalize_release_channel() {
  case "$1" in
    production|prod)
      printf 'production'
      ;;
    test|beta)
      printf 'test'
      ;;
    *)
      echo "Invalid release channel: $1. Expected production or test." >&2
      exit 2
      ;;
  esac
}

scope_list_contains() {
  local scope_list=",$1,"
  [[ "$scope_list" == *",$2,"* ]]
}

validate_uat_release_config() {
  local override_file="$ROOT_DIR/deploy/docker-compose.uat-acr.override.yml"
  local scope_line default_scopes
  if [[ ! -f "$override_file" ]]; then
    echo "UAT release override is missing: $override_file" >&2
    exit 1
  fi
  scope_line="$(grep -E '^[[:space:]]+APP_AUTH_OFFICIAL_ACCESS_SEMATTICE_SCOPES:' "$override_file" | head -1 || true)"
  default_scopes="${scope_line#*:-}"
  default_scopes="${default_scopes%%\}*}"
  if [[ -z "$scope_line" ]] || ! scope_list_contains "$default_scopes" "metadata.read" || ! scope_list_contains "$default_scopes" "runtime.record.read"; then
    echo "UAT Semattice HUMAN scopes must include metadata.read and runtime.record.read for AI table access." >&2
    exit 1
  fi
}

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

validate_production_version() {
  local version="$1"
  if [[ ! "$version" =~ ^([0-9]+)\.([0-9]+)\.([0-9]+)$ ]]; then
    return 1
  fi
  local major="$((10#${BASH_REMATCH[1]}))"
  local minor="$((10#${BASH_REMATCH[2]}))"
  local patch="$((10#${BASH_REMATCH[3]}))"
  [[ "$major" -ge 0 && "$major" -le "$MAX_MAJOR_VERSION" && "$minor" -ge 0 && "$minor" -le "$MAX_MINOR_VERSION" && "$patch" -ge 1 && "$patch" -le "$MAX_PATCH_VERSION" ]]
}

validate_release_version() {
  local version="$1"
  if [[ "$version" =~ ^([0-9]+\.[0-9]+\.[0-9]+)-beta\.([0-9]+)$ ]]; then
    local beta_number="$((10#${BASH_REMATCH[2]}))"
    validate_production_version "${BASH_REMATCH[1]}" && [[ "$beta_number" -ge 1 ]]
    return
  fi
  validate_production_version "$version"
}

release_channel_for_version() {
  if [[ "$1" == *-beta.* ]]; then
    printf 'test'
  else
    printf 'production'
  fi
}

latest_production_version() {
  git -C "$ROOT_DIR" ls-remote --tags origin |
    awk -F'\t' '{
      ref = $2
      sub(/^refs\/tags\//, "", ref)
      if (ref !~ /\^{}$/ && ref ~ /^[0-9]+\.[0-9]+\.[0-9]+$/) {
        split(ref, v, ".")
        printf "%04d.%04d.%04d %s\n", v[1], v[2], v[3], ref
      }
    }' |
    sort |
    tail -1 |
    awk '{print $2}'
}

increment_production_version() {
  local version="$1"
  IFS=. read -r major minor patch <<<"$version"
  major="$((10#$major))"
  minor="$((10#$minor))"
  patch="$((10#$patch))"

  if (( patch < MAX_PATCH_VERSION )); then
    patch=$((patch + 1))
  elif (( minor < MAX_MINOR_VERSION )); then
    minor=$((minor + 1))
    patch=1
  elif (( major < MAX_MAJOR_VERSION )); then
    major=$((major + 1))
    minor=0
    patch=1
  else
    echo "Cannot increment production version $version: all numeric segments are already at 12." >&2
    exit 1
  fi

  printf '%d.%d.%d' "$major" "$minor" "$patch"
}

next_production_version() {
  local latest
  latest="$(latest_production_version)"
  if [[ -z "$latest" ]]; then
    printf '%s' "$INITIAL_PRODUCTION_VERSION"
    return
  fi
  increment_production_version "$latest"
}

next_test_version() {
  local current_production target_production latest latest_beta
  current_production="${RELEASE_PRODUCTION_BASE:-}"
  if [[ -n "$current_production" ]]; then
    if ! validate_production_version "$current_production"; then
      echo "Invalid RELEASE_PRODUCTION_BASE: $current_production" >&2
      exit 2
    fi
    local latest_tagged_production
    latest_tagged_production="$(latest_production_version)"
    if [[ -n "$latest_tagged_production" && "$(printf '%s\n%s\n' "$latest_tagged_production" "$current_production" | sort -V | tail -1)" != "$current_production" ]]; then
      echo "RELEASE_PRODUCTION_BASE must not precede the latest production Git tag: $latest_tagged_production" >&2
      exit 2
    fi
  else
    current_production="$(latest_production_version)"
  fi
  if [[ -z "$current_production" ]]; then
    target_production="$INITIAL_PRODUCTION_VERSION"
  else
    target_production="$(increment_production_version "$current_production")"
  fi
  latest="$(
    git -C "$ROOT_DIR" tag --list "${target_production}-beta.*" |
      awk -v base="$target_production" '
        index($0, base "-beta.") == 1 {
          rest = substr($0, length(base) + 7)
          if (match(rest, /^[0-9]+$/)) {
            print rest
          }
        }
      ' |
      sort -n |
      tail -1
  )"
  if [[ -z "$latest" ]]; then
    latest=0
  fi
  latest_beta="$((latest + 1))"
  printf '%s-beta.%d' "$target_production" "$latest_beta"
}

next_release_version() {
  case "$RELEASE_CHANNEL" in
    production)
      next_production_version
      ;;
    test)
      next_test_version
      ;;
  esac
}

RELEASE_CHANNEL="$(normalize_release_channel "$RELEASE_CHANNEL")"

if [[ "$RELEASE_CHANNEL" == "test" ]]; then
  validate_uat_release_config
fi

if [[ -z "$CICI_RELEASE_VERSION" ]]; then
  CICI_RELEASE_VERSION="$(next_release_version)"
fi

if ! validate_release_version "$CICI_RELEASE_VERSION"; then
  echo "Invalid release version: $CICI_RELEASE_VERSION" >&2
  echo "Expected production version N.N.N with major/minor 0-12 and patch 1-365, or test version N.N.N-beta.N." >&2
  exit 2
fi

if [[ "$EXPLICIT_VERSION" == "true" ]]; then
  explicit_channel="$(release_channel_for_version "$CICI_RELEASE_VERSION")"
  if [[ "$CHANNEL_EXPLICIT" == "true" && "$explicit_channel" != "$RELEASE_CHANNEL" ]]; then
    echo "Version $CICI_RELEASE_VERSION does not match requested channel $RELEASE_CHANNEL." >&2
    exit 2
  fi
  RELEASE_CHANNEL="$explicit_channel"
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
  channel:        $RELEASE_CHANNEL
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
