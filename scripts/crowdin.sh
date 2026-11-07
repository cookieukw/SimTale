#!/usr/bin/env bash
#
# Small wrapper around the Crowdin CLI for this repository.
#
# The source of truth for English strings is en-US. Crowdin stores the
# translations for pt-BR and es-ES. Use these commands when you want to push
# the current repository state to Crowdin instead of waiting for the GitHub
# Action.
#
# Setup:
#   export CROWDIN_PROJECT_ID="<numeric project id>"
#   export CROWDIN_PERSONAL_TOKEN="<personal token>"
#
# Examples:
#   ./scripts/crowdin.sh upload-sources
#   ./scripts/crowdin.sh upload-translations
#   ./scripts/crowdin.sh upload-translations pt-BR
#   ./scripts/crowdin.sh download
#   ./scripts/crowdin.sh sync
#
# Set CROWDIN_DRY_RUN=1 to print what would happen without calling Crowdin.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

DEFAULT_LANGUAGES=(pt-BR es-ES)

usage() {
  cat <<'EOF'
Usage: ./scripts/crowdin.sh <command> [options]

Commands:
  upload-sources                 Upload en-US source files to Crowdin.
  upload-translations [locale]   Upload repository translations to Crowdin.
                                 Defaults to pt-BR and es-ES.
  download                       Download translations, then restore keys that
                                 Crowdin omitted because they are untranslated.
  sync [locale]                  upload-sources + upload-translations + download.
  help                           Show this help.

Environment:
  CROWDIN_PROJECT_ID             Numeric Crowdin project id.
  CROWDIN_PERSONAL_TOKEN         Crowdin personal access token.
  CROWDIN_BIN                    Optional path to the crowdin executable.
  CROWDIN_DRY_RUN=1              Print the commands without executing them.

WARNING: uploading translations sends the repository text to Crowdin. If a
translator has newer, unapproved text in Crowdin, this can overwrite it. Use
upload-translations with a single locale when you only want to update one.
EOF
}

find_crowdin() {
  if [[ -n "${CROWDIN_BIN:-}" ]]; then
    if [[ -x "$CROWDIN_BIN" ]] || command -v "$CROWDIN_BIN" >/dev/null 2>&1; then
      CROWDIN=("$CROWDIN_BIN")
      return
    fi
    echo "ERROR: CROWDIN_BIN is set but not executable: $CROWDIN_BIN" >&2
    exit 1
  fi

  if command -v crowdin >/dev/null 2>&1; then
    CROWDIN=(crowdin)
    return
  fi

  # The local Node installation is often managed by nvm and not on PATH.
  shopt -s nullglob
  for candidate in "$HOME"/.nvm/versions/node/*/bin/crowdin; do
    if [[ -x "$candidate" ]]; then
      CROWDIN=("$candidate")
      # The Crowdin launcher uses `#!/usr/bin/env node`; put the matching Node
      # installation on PATH so running it outside an nvm shell still works.
      export PATH="$(dirname "$candidate"):$PATH"
      return
    fi
  done
  shopt -u nullglob

  if command -v npx >/dev/null 2>&1; then
    CROWDIN=(npx --yes @crowdin/cli@5)
    return
  fi

  echo "ERROR: Crowdin CLI not found." >&2
  echo "Install it with: npm install -g @crowdin/cli@5" >&2
  echo "Or set CROWDIN_BIN=/path/to/crowdin" >&2
  exit 1
}

require_credentials() {
  if [[ -z "${CROWDIN_PROJECT_ID:-}" ]]; then
    echo "ERROR: CROWDIN_PROJECT_ID is not set." >&2
    exit 1
  fi
  if [[ -z "${CROWDIN_PERSONAL_TOKEN:-}" ]]; then
    echo "ERROR: CROWDIN_PERSONAL_TOKEN is not set." >&2
    exit 1
  fi
}

dry_run_args() {
  if [[ "${CROWDIN_DRY_RUN:-0}" == "1" ]]; then
    printf '%s\n' --dryrun
  fi
}

run_crowdin() {
  local -a args=()
  while IFS= read -r arg; do
    args+=("$arg")
  done < <(dry_run_args)
  echo "+ ${CROWDIN[*]} $* ${args[*]}"
  "${CROWDIN[@]}" "$@" "${args[@]}"
}

upload_sources() {
  run_crowdin upload sources
}

upload_translations() {
  local -a languages=("$@")
  if ((${#languages[@]} == 0)); then
    languages=("${DEFAULT_LANGUAGES[@]}")
  fi

  for language in "${languages[@]}"; do
    echo "Uploading $language translations..."
    run_crowdin upload translations --language "$language"
  done
}

download_translations() {
  run_crowdin download translations --skip-untranslated-strings

  if [[ "${CROWDIN_DRY_RUN:-0}" == "1" ]]; then
    echo "Dry run: skipping the local merge step."
    return
  fi

  # Crowdin omits untranslated strings. Restore keys that already exist in the
  # current commit so a download cannot silently delete fallback translations.
  python3 tools/merge_crowdin_keys.py --base HEAD
  echo
  echo "Review the result with:"
  echo "  git diff -- src/main/resources/Server/Languages"
}

command="${1:-help}"
shift || true

case "$command" in
  upload-sources|sources)
    require_credentials
    find_crowdin
    upload_sources
    ;;
  upload-translations|translations)
    require_credentials
    find_crowdin
    upload_translations "$@"
    ;;
  download)
    require_credentials
    find_crowdin
    download_translations
    ;;
  sync)
    require_credentials
    find_crowdin
    upload_sources
    upload_translations "$@"
    download_translations
    ;;
  help|-h|--help)
    usage
    ;;
  *)
    echo "Unknown command: $command" >&2
    echo
    usage >&2
    exit 1
    ;;
esac
