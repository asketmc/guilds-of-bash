#!/usr/bin/env bash
set -euo pipefail
v="$1"
mkdir -p dist
out="dist/build-info-${v}.txt"
cat > "$out" <<EOF
version: ${v}
commit: ${GITHUB_SHA:-}
ref: ${GITHUB_REF:-}
workflow: ${GITHUB_WORKFLOW:-}
run_id: ${GITHUB_RUN_ID:-}
run_number: ${GITHUB_RUN_NUMBER:-}
runner: ${RUNNER_OS:-}
EOF
cp -f gradle_shadowjar.log "dist/gradle_shadowjar.log" || true
