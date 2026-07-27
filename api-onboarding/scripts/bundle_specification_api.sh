#!/usr/bin/env bash
#
# Bundle + overlay pipeline for API contracts.
#
# Steps:
#   1. redocly bundle   : resolve multi-file $refs into a single document (non-dereferenced)
#   2. zuplo openapi overlay : apply OAI Overlay 1.0.0 document(s) onto the bundled spec
#   3. redocly lint     : validate the resolved output (catches broken overlay results)
#   4. assertions       : fail loudly if expected content did not land (silent JSONPath misses)
#
# Usage:
#   ./script.sh <output-dir>
#
#   Invoked from Maven (exec-maven-plugin, phase generate-test-resources) as:
#     ./scripts/script.sh ${project.build.testOutputDirectory}/api/strapi
#
# Environment (all optional, defaults below):
#   SPEC_DIR          root of contract sources        (default: src/main/resources/api/strapi)
#   OVERLAY_DIR       directory of overlay documents  (default: src/test/resources/api/strapi)
#   REDOCLY_VERSION   pinned @redocly/cli version     (default: 2)
#   ZUPLO_VERSION     pinned zuplo CLI version        (default: latest)
#   SKIP_LINT         set to "true" to skip step 3

set -euo pipefail

# --- arguments & configuration -----------------------------------------------

SPEC_FILE="${1:?usage: script.sh <spec-file> <output-dir> <output-file>}"
OUTPUT_DIR="${2:?usage: script.sh <spec-file> <output-dir> <output-file>}"

OUTPUT_FILE="${OUTPUT_FILE:${SPEC_FILE}}"
SPEC_DIR="${SPEC_DIR:-src/main/resources/api/strapi}"
OVERLAY_DIR="${OVERLAY_DIR:-src/test/resources/api/strapi}"

REDOCLY_VERSION="${REDOCLY_VERSION:-2}"
ZUPLO_VERSION="${ZUPLO_VERSION:-latest}"
SKIP_LINT="${SKIP_LINT:-true}"

REDOCLY="npx --yes @redocly/cli@${REDOCLY_VERSION}"
ZUPLO="npx --yes zuplo@${ZUPLO_VERSION}"

BUNDLED="${OUTPUT_DIR}/bundled.${SPEC_FILE}"
RESOLVED="${OUTPUT_DIR}/resolved.${OUTPUT_FILE}"

log()  { printf '\033[1;34m[api-pipeline]\033[0m %s\n' "$*"; }
fail() { printf '\033[1;31m[api-pipeline] ERROR:\033[0m %s\n' "$*" >&2; exit 1; }

# --- node toolchain: prefer latest LTS via nvm when available ----------------
#
# nvm is a shell function, not a binary — it must be sourced explicitly in
# non-interactive shells (Maven exec, CI). If nvm is not installed we fall
# back to whatever Node is on PATH (e.g. provided by frontend-maven-plugin).
# Disable with USE_NVM=false.

USE_NVM="${USE_NVM:-true}"
NVM_DIR="${NVM_DIR:-${HOME}/.nvm}"

if [[ "${USE_NVM}" == "true" && -s "${NVM_DIR}/nvm.sh" ]]; then
  # nvm.sh is not `set -u`-clean; relax nounset around sourcing/use
  set +u
  # shellcheck source=/dev/null
  . "${NVM_DIR}/nvm.sh"
  nvm use --lts >/dev/null 2>&1 || nvm install --lts >/dev/null
  set -u
  log "using Node $(node --version) (nvm LTS)"
else
  log "nvm not available — using Node from PATH ($(command -v node || echo 'none'))"
fi

# --- preconditions -----------------------------------------------------------

command -v npx >/dev/null 2>&1 || fail "npx not found on PATH (Node.js is required)"
[[ -f "${SPEC_DIR}/${SPEC_FILE}" ]]  || fail "spec entrypoint not found: ${SPEC_DIR}/${SPEC_FILE}"

mkdir -p "${OUTPUT_DIR}"

# --- step 1: bundle (resolve $refs, keep components — do NOT dereference) ----

log "bundling ${SPEC_DIR}/${SPEC_FILE} -> ${BUNDLED}"
${REDOCLY} bundle "${SPEC_DIR}/${SPEC_FILE}" --output "${BUNDLED}"

[[ -s "${BUNDLED}" ]] || fail "bundle produced no output: ${BUNDLED}"

# --- step 2: apply overlays in deterministic (lexicographic) order -----------

cp "${BUNDLED}" "${RESOLVED}"

shopt -s nullglob
overlays=( "${OVERLAY_DIR}"/*.overlay.yaml "${OVERLAY_DIR}"/*.overlay.json )
shopt -u nullglob

if (( ${#overlays[@]} == 0 )); then
  log "no overlays found in ${OVERLAY_DIR} — resolved spec is the bundle as-is"
else
  for overlay in "${overlays[@]}"; do
    log "applying overlay: ${overlay}"
    tmp="$(mktemp "${OUTPUT_DIR}/.resolved.XXXXXX.yaml")"
    ${ZUPLO} openapi overlay \
      --input   "${RESOLVED}" \
      --overlay "${overlay}" \
      --output  "${tmp}" \
      --format  yaml
    [[ -s "${tmp}" ]] || fail "overlay application produced empty output: ${overlay}"
    mv "${tmp}" "${RESOLVED}"
  done
fi

# --- step 3: lint the resolved spec ------------------------------------------

if [[ "${SKIP_LINT}" != "false" ]]; then
  log "linting ${RESOLVED}"
  ${REDOCLY} lint --generate-ignore-file "${RESOLVED}"
else
  log "lint skipped (SKIP_LINT=true)"
fi

# --- step 4: assertions — guard against silent JSONPath non-matches ----------
#
# Each overlay may declare its own expectations in a sibling file:
#   <name>.overlay.yaml  ->  <name>.expect
# containing one fixed string per line that MUST appear in the resolved spec
# (e.g. an example name like `valid_openapi_contract:`).

for overlay in "${overlays[@]:-}"; do
  [[ -n "${overlay}" ]] || continue
  expect_file="${overlay%.overlay.*}.expect"
  [[ -f "${expect_file}" ]] || continue
  while IFS= read -r expected; do
    [[ -z "${expected}" || "${expected}" == \#* ]] && continue
    grep -qF -- "${expected}" "${RESOLVED}" \
      || fail "expected content '${expected}' (from ${expect_file}) not found in ${RESOLVED} — overlay target likely did not match"
  done < "${expect_file}"
  log "assertions passed: ${expect_file}"
done

# --- done --------------------------------------------------------------------

rm -f "${BUNDLED}"   # keep only the final artifact on the test classpath; comment out to debug
log "resolved contract ready: ${RESOLVED}"