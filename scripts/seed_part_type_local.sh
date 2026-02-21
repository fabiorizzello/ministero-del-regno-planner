#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
sql_file="${script_dir}/seed_part_type.sql"

if ! command -v sqlite3 >/dev/null 2>&1; then
  echo "sqlite3 non trovato. Installa sqlite3 e riprova." >&2
  exit 1
fi

if [[ ! -f "${sql_file}" ]]; then
  echo "File SQL non trovato: ${sql_file}" >&2
  exit 1
fi

if [[ -n "${DB_PATH:-}" ]]; then
  db_path="${DB_PATH}"
else
  if [[ -n "${LOCALAPPDATA:-}" ]]; then
    db_path="${LOCALAPPDATA}/EfficaciNelMinistero/data/ministero.sqlite"
  else
    db_path="${HOME}/.EfficaciNelMinistero/data/ministero.sqlite"
  fi
fi

if [[ ! -f "${db_path}" ]]; then
  echo "DB non trovato: ${db_path}" >&2
  echo "Avvia l'app almeno una volta per creare il DB, oppure imposta DB_PATH." >&2
  exit 1
fi

sqlite3 "${db_path}" < "${sql_file}"
echo "Seed completato su: ${db_path}"
