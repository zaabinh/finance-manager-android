#!/usr/bin/env bash
# Deploy one backend image to the staging stack in /srv/dong. Called by GitHub Actions as:
#   sudo /usr/local/sbin/dong-deploy-staging <40-char commit SHA>
# Installed root-owned (0755) and allowed via sudoers for the deploy user only; see docs/deployment/CD_STAGING.md.
# Pulls ghcr.io/<repo>:<SHA>, retags it to the local name compose.yaml expects, switches BACKEND_TAG,
# recreates only the backend and waits for readiness. Failures after switching attempt image rollback.
set -euo pipefail
umask 077

SHA="${1:-}"
[[ $# == 1 && "$SHA" =~ ^[0-9a-f]{40}$ ]] || { echo "usage: deploy.sh <40-char lowercase commit SHA>" >&2; exit 2; }

DIR=/srv/dong
REGISTRY_IMAGE=ghcr.io/zaabinh/finance-manager-backend
LOCAL_IMAGE=finance-manager-backend
READINESS_TIMEOUT=180   # polling sleep budget in seconds, excluding request execution time
LOG="$DIR/releases/deploy.log"
cd "$DIR"
exec 9>"$DIR/releases/.deploy.lock"
flock -n 9 || { echo "another deployment is running" >&2; exit 3; }

log() { printf '%s %s\n' "$(date -u +%FT%TZ)" "$*" | tee -a "$LOG"; }
compose() { docker compose --env-file .env -f compose.yaml "$@"; }
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

# Refuse ambiguous dotenv syntax rather than changing one value while Compose uses another.
assignments="$(grep -Ec '^[[:space:]]*(export[[:space:]]+)?BACKEND_TAG([[:space:]]*=|[[:space:]]*(#.*)?$)' .env || true)"
PREVIOUS="$(sed -n 's/^BACKEND_TAG=//p' .env)"
[[ "$assignments" == 1 && "$PREVIOUS" =~ ^[a-zA-Z0-9_][a-zA-Z0-9_.-]{0,127}$ ]] || {
    log "abort: .env must contain exactly one BACKEND_TAG=<valid Docker tag> assignment"; exit 2;
}
# Legacy local tags are supported for the first CD rollback; new deployments always use full SHAs.
docker image inspect "$LOCAL_IMAGE:$PREVIOUS" >/dev/null
log "deploy start sha=$SHA previous=$PREVIOUS"

docker pull -q "$REGISTRY_IMAGE:$SHA"
revision="$(docker image inspect -f '{{ index .Config.Labels "org.opencontainers.image.revision" }}' "$REGISTRY_IMAGE:$SHA")"
[[ "$revision" == "$SHA" ]] || { log "abort: image revision label does not match requested SHA"; exit 4; }
docker tag "$REGISTRY_IMAGE:$SHA" "$LOCAL_IMAGE:$SHA"

switched=0
finish() {
    local status=$?
    trap - EXIT
    if (( status != 0 && switched == 1 )); then
        log "deploy failed for $SHA; rolling back to $PREVIOUS" || true
        compose logs --no-color --tail 80 backend >> "$LOG" 2>&1 || true
        if set_tag "$PREVIOUS" && compose up -d --no-deps --no-build --pull never backend && wait_ready; then
            log "rollback ok tag=$PREVIOUS" || true
        else
            log "rollback failed tag=$PREVIOUS - manual action needed" || true
        fi
    fi
    exit "$status"
}
trap finish EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

# Arm before mutation so a failing write cannot bypass the rollback attempt.
switched=1
set_tag "$SHA"
compose config --quiet
compose up -d --no-deps --no-build --pull never backend
if wait_ready; then
    log "deploy ok sha=$SHA image=$(docker image inspect -f '{{.Id}}' "$LOCAL_IMAGE:$SHA")"
    exit 0
fi

log "readiness failed for $SHA"
exit 1
