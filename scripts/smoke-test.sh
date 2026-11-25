#!/bin/sh
set -eu

root="$(cd "$(dirname "$0")/.." && pwd)"
base="${GATEWAY_URL:-http://localhost:8080}"
key="$(grep '^GATEWAY_API_KEYS=' "$root/.env" | cut -d= -f2-)"
failures=0

status() {
    curl -s -o /dev/null -w '%{http_code}' "$@"
}

check() {
    description="$1"
    expected="$2"
    actual="$3"
    if [ "$expected" = "$actual" ]; then
        echo "ok   $description"
    else
        echo "FAIL $description (expected $expected, got $actual)"
        failures=$((failures + 1))
    fi
}

check "health is public" 200 "$(status "$base/actuator/health")"
check "products need an API key" 401 "$(status "$base/api/warehouse/products")"
check "a wrong API key is refused" 401 "$(status -H "X-API-Key: wrong-key-0123456789" "$base/api/warehouse/products")"
check "products are listed with a key" 200 "$(status -H "X-API-Key: $key" "$base/api/warehouse/products")"
check "orders are listed with a key" 200 "$(status -H "X-API-Key: $key" "$base/api/orders")"
check "couriers are listed with a key" 200 "$(status -H "X-API-Key: $key" "$base/api/delivery/couriers")"
check "an unknown parcel is not found" 404 "$(status "$base/api/tracking/LGAAAAAAAAAA")"
check "internal paths are not routed" 404 "$(status -H "X-API-Key: $key" "$base/internal/reservations")"
check "path traversal is refused" 400 "$(status --path-as-is -H "X-API-Key: $key" "$base/api/orders/../internal/reservations")"

if [ "$failures" -gt 0 ]; then
    echo "$failures check(s) failed"
    exit 1
fi
echo "All checks passed"
