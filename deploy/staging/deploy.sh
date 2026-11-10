#!/usr/bin/env bash
# Deploy one backend image to the staging stack in /srv/dong. Called by GitHub Actions as:
#   sudo /srv/dong/deploy.sh <40-char commit SHA>
# Installed root-owned (0755) and allowed via sudoers for the deploy user only; see docs/deployment/CD_STAGING.md.
# Pulls ghcr.io/<repo>:<SHA>, retags it to the local name compose.yaml expects, switches BACKEND_TAG,
# recreates only the backend and waits for readiness. Any failure restores the previous tag.
set -euo pipefail

SHA="${1:-}"
[[ "$SHA" =~ ^[0-9a-f]{40}$ ]] || { echo "usage: deploy.sh <40-char lowercase commit SHA>" >&2; exit 2; }

DIR=/srv/dong
REGISTRY_IMAGE=ghcr.io/zaabinh/finance-manager-backend
LOCAL_IMAGE=finance-manager-backend
READINESS_TIMEOUT=180   # seconds; startup was ~41 s on this VPS
LOG="$DIR/releases/deploy.log"
cd "$DIR"
exec 9>"$DIR/releases/.deploy.lock"
flock -n 9 || { echo "another deployment is running" >&2; exit 3; }

log() { printf '%s %s\n' "$(date -u +%FT%TZ)" "$*" | tee -a "$LOG"; }
compose() { docker compose --env-file .env -f compose.yaml "$@"; }
current_tag() { sed -n 's/^BACKEND_TAG=//p' .env | tail -n1; }
set_tag() { sed -i "s/^BACKEND_TAG=.*/BACKEND_TAG=$1/" .env; }
ready() {
    compose exec -T caddy wget -q -T 5 -O- http://backend:8080/actuator/health/readiness 2>/dev/null | grep -q '"status":"UP"'
}
wait_ready() {
    local waited=0
    until ready; do
        (( waited >= READINESS_TIMEOUT )) && return 1
        sleep 5; waited=$(( waited + 5 ))
    done
}

PREVIOUS="$(current_tag)"
log "deploy start sha=$SHA previous=${PREVIOUS:-none}"

docker pull -q "$REGISTRY_IMAGE:$SHA"
revision="$(docker image inspect -f '{{ index .Config.Labels "org.opencontainers.image.revision" }}' "$REGISTRY_IMAGE:$SHA")"
[[ "$revision" == "$SHA" ]] || { log "abort: image revision label '$revision' != $SHA"; exit 4; }
docker tag "$REGISTRY_IMAGE:$SHA" "$LOCAL_IMAGE:$SHA"

set_tag "$SHA"
if ! compose config --quiet; then
    set_tag "$PREVIOUS"; log "abort: compose config invalid, kept $PREVIOUS"; exit 5
fi

compose up -d --no-deps --no-build --pull never backend
if wait_ready; then
    log "deploy ok sha=$SHA image=$(docker image inspect -f '{{.Id}}' "$LOCAL_IMAGE:$SHA")"
    exit 0
fi

log "readiness failed for $SHA; rolling back to ${PREVIOUS:-none}"
compose logs --no-color --tail 80 backend >> "$LOG" 2>&1 || true
if [[ -n "$PREVIOUS" ]]; then
    set_tag "$PREVIOUS"
    compose up -d --no-deps --no-build --pull never backend
    wait_ready && log "rollback ok sha=$PREVIOUS" || log "rollback NOT ready sha=$PREVIOUS - manual action needed"
fi
exit 1
