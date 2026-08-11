#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

assert_next_version() {
  local current_version="$1"
  local expected_version="$2"
  local case_dir="$TMP_DIR/${current_version//./-}"
  local remote_dir="$case_dir/remote.git"
  local repo_dir="$case_dir/repo"

  git init --bare "$remote_dir" >/dev/null
  git init "$repo_dir" >/dev/null
  git -C "$repo_dir" config user.email 'release-test@example.invalid'
  git -C "$repo_dir" config user.name 'release-version-test'
  mkdir -p "$repo_dir/scripts" "$repo_dir/deploy"
  cp "$ROOT_DIR/scripts/release-acr.sh" "$repo_dir/scripts/release-acr.sh"
  cp "$ROOT_DIR/deploy/docker-compose.uat-acr.override.yml" "$repo_dir/deploy/docker-compose.uat-acr.override.yml"
  chmod +x "$repo_dir/scripts/release-acr.sh"
  git -C "$repo_dir" add scripts/release-acr.sh
  git -C "$repo_dir" commit -m 'test fixture' >/dev/null
  git -C "$repo_dir" remote add origin "$remote_dir"
  git -C "$repo_dir" tag "$current_version"
  git -C "$repo_dir" push origin HEAD --tags >/dev/null

  local release_output actual_version
  release_output="$(cd "$repo_dir" && ./scripts/release-acr.sh --dry-run --production)"
  actual_version="$(printf '%s\n' "$release_output" | awk '/^[[:space:]]+version:/ { print $2; exit }')"
  [[ "$actual_version" == "$expected_version" ]] || {
    echo "expected next version $expected_version after $current_version, got $actual_version" >&2
    exit 1
  }
}

assert_invalid_version_rejected() {
  local case_dir="$TMP_DIR/invalid"
  git init "$case_dir" >/dev/null
  git -C "$case_dir" config user.email 'release-test@example.invalid'
  git -C "$case_dir" config user.name 'release-version-test'
  mkdir -p "$case_dir/scripts"
  cp "$ROOT_DIR/scripts/release-acr.sh" "$case_dir/scripts/release-acr.sh"
  chmod +x "$case_dir/scripts/release-acr.sh"
  git -C "$case_dir" add scripts/release-acr.sh
  git -C "$case_dir" commit -m 'test fixture' >/dev/null

  if (cd "$case_dir" && ./scripts/release-acr.sh --dry-run --version 2.8.366 --production >/dev/null 2>&1); then
    echo '2.8.366 should be rejected' >&2
    exit 1
  fi
}

assert_next_test_version() {
  local current_version="$1"
  local existing_beta="$2"
  local expected_version="$3"
  local case_dir="$TMP_DIR/test-${current_version//./-}-${existing_beta:-none}"
  local remote_dir="$case_dir/remote.git"
  local repo_dir="$case_dir/repo"

  git init --bare "$remote_dir" >/dev/null
  git init "$repo_dir" >/dev/null
  git -C "$repo_dir" config user.email 'release-test@example.invalid'
  git -C "$repo_dir" config user.name 'release-version-test'
  mkdir -p "$repo_dir/scripts" "$repo_dir/deploy"
  cp "$ROOT_DIR/scripts/release-acr.sh" "$repo_dir/scripts/release-acr.sh"
  cp "$ROOT_DIR/deploy/docker-compose.uat-acr.override.yml" "$repo_dir/deploy/docker-compose.uat-acr.override.yml"
  chmod +x "$repo_dir/scripts/release-acr.sh"
  git -C "$repo_dir" add scripts/release-acr.sh
  git -C "$repo_dir" commit -m 'test fixture' >/dev/null
  git -C "$repo_dir" remote add origin "$remote_dir"
  git -C "$repo_dir" tag "$current_version"
  if [[ -n "$existing_beta" ]]; then
    git -C "$repo_dir" tag "$existing_beta"
  fi
  git -C "$repo_dir" push origin HEAD --tags >/dev/null

  local release_output actual_version
  release_output="$(cd "$repo_dir" && ./scripts/release-acr.sh --dry-run --test)"
  actual_version="$(printf '%s\n' "$release_output" | awk '/^[[:space:]]+version:/ { print $2; exit }')"
  [[ "$actual_version" == "$expected_version" ]] || {
    echo "expected next test version $expected_version after $current_version, got $actual_version" >&2
    exit 1
  }
}

assert_test_release_rejects_missing_ai_table_scopes() {
  local case_dir="$TMP_DIR/test-missing-ai-table-scopes"
  git init "$case_dir" >/dev/null
  git -C "$case_dir" config user.email 'release-test@example.invalid'
  git -C "$case_dir" config user.name 'release-version-test'
  mkdir -p "$case_dir/scripts" "$case_dir/deploy"
  cp "$ROOT_DIR/scripts/release-acr.sh" "$case_dir/scripts/release-acr.sh"
  cp "$ROOT_DIR/deploy/docker-compose.uat-acr.override.yml" "$case_dir/deploy/docker-compose.uat-acr.override.yml"
  chmod +x "$case_dir/scripts/release-acr.sh"
  sed -i.bak 's/metadata.read,//' "$case_dir/deploy/docker-compose.uat-acr.override.yml"
  rm "$case_dir/deploy/docker-compose.uat-acr.override.yml.bak"
  git -C "$case_dir" add scripts/release-acr.sh deploy/docker-compose.uat-acr.override.yml
  git -C "$case_dir" commit -m 'test fixture' >/dev/null

  if (cd "$case_dir" && ./scripts/release-acr.sh --dry-run --version 2.8.59-beta.1 --test >/dev/null 2>&1); then
    echo 'test release should reject UAT scopes without metadata.read' >&2
    exit 1
  fi
}

assert_next_version 2.8.364 2.8.365
assert_next_version 2.8.365 2.9.1
assert_next_version 2.12.365 3.0.1
assert_next_test_version 2.8.58 "" 2.8.59-beta.1
assert_next_test_version 2.8.58 2.8.59-beta.2 2.8.59-beta.3
assert_test_release_rejects_missing_ai_table_scopes
assert_invalid_version_rejected

echo 'release versioning tests passed'
