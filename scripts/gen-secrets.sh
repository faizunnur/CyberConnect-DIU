#!/bin/sh
# Creates .env from .env.example with freshly generated secrets (requires openssl).
set -eu
root="$(cd "$(dirname "$0")/.." && pwd)"
env_file="$root/.env"
if [ -f "$env_file" ]; then
  echo ".env already exists at $env_file - not overwriting. Delete it first to regenerate." >&2
  exit 1
fi
secret() { openssl rand -base64 24 | tr -d '+/='; }
aes_key() { openssl rand -base64 32; }
sed \
  -e "s|DB_MIGRATOR_PASSWORD=CHANGE_ME|DB_MIGRATOR_PASSWORD=$(secret)|" \
  -e "s|DB_APP_PASSWORD=CHANGE_ME|DB_APP_PASSWORD=$(secret)|" \
  -e "s|REDIS_PASSWORD=CHANGE_ME|REDIS_PASSWORD=$(secret)|" \
  -e "s|STORAGE_SECRET_KEY=CHANGE_ME|STORAGE_SECRET_KEY=$(secret)|" \
  -e "s|DOCUMENT_ENCRYPTION_KEY=CHANGE_ME_BASE64_32_BYTES|DOCUMENT_ENCRYPTION_KEY=$(aes_key)|" \
  "$root/.env.example" > "$env_file"
echo "Wrote $env_file with generated secrets."
