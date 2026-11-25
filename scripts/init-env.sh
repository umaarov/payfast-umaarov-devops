#!/bin/sh
set -eu

root="$(cd "$(dirname "$0")/.." && pwd)"
target="$root/.env"

if [ -e "$target" ] && [ "${1:-}" != "--force" ]; then
    echo ".env already exists. Run with --force to replace it."
    exit 0
fi

secret() {
    head -c 24 /dev/urandom | base64 | tr -d '+/=\n'
}

umask 077
{
    printf 'POSTGRES_PASSWORD=%s\n' "$(secret)"
    printf 'ORDER_DB_PASSWORD=%s\n' "$(secret)"
    printf 'WAREHOUSE_DB_PASSWORD=%s\n' "$(secret)"
    printf 'DELIVERY_DB_PASSWORD=%s\n' "$(secret)"
    printf 'TRACKING_DB_PASSWORD=%s\n' "$(secret)"
    printf 'GATEWAY_API_KEYS=%s\n' "$(secret)"
} > "$target"

echo "Created $target with new random secrets. The API key for requests is GATEWAY_API_KEYS in that file."
