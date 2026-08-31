#!/usr/bin/env bash
#
# Regenerate the slide deck from doc/slides/*.md with Marp.
#
# Usage:
#   ./scripts/build-slides.sh              # HTML (default)
#   ./scripts/build-slides.sh pdf          # PDF
#   ./scripts/build-slides.sh pptx         # PowerPoint
#   ./scripts/build-slides.sh png          # one PNG per slide
#   ./scripts/build-slides.sh watch        # rebuild HTML on every save
#   ./scripts/build-slides.sh serve        # live preview at http://localhost:8080
#
#   DECK=doc/slides/other.md ./scripts/build-slides.sh   # build a different deck
#
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DECK="${DECK:-doc/slides/pitch.md}"
MODE="${1:-html}"

cd "$ROOT"

[ -f "$DECK" ] || { echo "error: deck not found: $DECK" >&2; exit 1; }

# The system npm at /usr/local/bin is a dangling symlink on this machine, so
# fall back to the newest nvm-managed Node when the one on PATH does not run.
if ! npm --version >/dev/null 2>&1; then
  nvm_versions="${NVM_DIR:-$HOME/.nvm}/versions/node"
  if [ -d "$nvm_versions" ]; then
    newest="$(ls -1 "$nvm_versions" | sort -t. -k1.2,1n -k2,2n -k3,3n | tail -1)"
    [ -n "$newest" ] && PATH="$nvm_versions/$newest/bin:$PATH" && export PATH
  fi
fi

if ! npm --version >/dev/null 2>&1; then
  echo "error: no working npm found. Install Node.js (e.g. 'nvm install --lts')." >&2
  exit 1
fi

[ -d node_modules/@marp-team/marp-cli ] || npm install

base="${DECK%.md}"

case "$MODE" in
  html)  npx --no-install marp "$DECK" --html -o "$base.html" ;;
  pdf)   npx --no-install marp "$DECK" --html --pdf -o "$base.pdf" ;;
  pptx)  npx --no-install marp "$DECK" --html --pptx -o "$base.pptx" ;;
  png)   mkdir -p "$base-png"
         npx --no-install marp "$DECK" --html --images png -o "$base-png/slide.png" ;;
  watch) npx --no-install marp "$DECK" --html -o "$base.html" --watch ;;
  serve) npx --no-install marp "$(dirname "$DECK")" --html --server ;;
  *)     echo "error: unknown mode '$MODE' (html|pdf|pptx|png|watch|serve)" >&2; exit 1 ;;
esac

echo "OK: $MODE build of $DECK complete."
