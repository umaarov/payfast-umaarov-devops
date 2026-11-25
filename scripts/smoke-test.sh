#!/bin/sh
set -eu

root="$(cd "$(dirname "$0")/.." && pwd)"
base="${GATEWAY_URL:-http://localhost:8080}"
key="$(grep '^GATEWAY_API_KEYS=' "$root/.env" | cut -d= -f2- | cut -d, -f1)"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
body_file="$work/body"
suffix="$(date +%s)$$"
sku="SMOKE-$suffix"
courier_phone="$(printf '+99890%07d' $(( ($(date +%s) + $$) % 10000000 )))"
failures=0

status() {
    curl -s -o /dev/null -w '%{http_code}' "$@"
}

call() {
    method="$1"
    path="$2"
    payload="${3:-}"
    idempotency="${4:-}"
    set -- -s -o "$body_file" -w '%{http_code}' -X "$method" -H "X-API-Key: $key"
    if [ -n "$payload" ]; then
        set -- "$@" -H 'Content-Type: application/json' -d "$payload"
    fi
    if [ -n "$idempotency" ]; then
        set -- "$@" -H "Idempotency-Key: $idempotency"
    fi
    curl "$@" "$base$path" || true
}

fetch_public() {
    curl -s -o "$body_file" -w '%{http_code}' "$base$1" || true
}

text_field() {
    grep -o "\"$1\":\"[^\"]*\"" "$body_file" | head -1 | cut -d'"' -f4 || true
}

number_field() {
    grep -o "\"$1\":[0-9-]*" "$body_file" | head -1 | cut -d: -f2 || true
}

report() {
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

body_has() {
    grep -q "$1" "$body_file" && echo yes || echo no
}

wait_until() {
    attempts=45
    while [ "$attempts" -gt 0 ]; do
        if "$@"; then
            return 0
        fi
        attempts=$((attempts - 1))
        sleep 1
    done
    return 1
}

eventually() {
    description="$1"
    shift
    if wait_until "$@"; then
        echo "ok   $description"
    else
        echo "FAIL $description (timed out)"
        failures=$((failures + 1))
    fi
}

order_status_is() {
    call GET "/api/orders/$order_id" > /dev/null
    [ "$(text_field status)" = "$1" ]
}

order_delivery_status_is() {
    call GET "/api/orders/$order_id" > /dev/null
    [ "$(text_field deliveryStatus)" = "$1" ]
}

order_status_of() {
    call GET "/api/orders/$1" > /dev/null
    [ "$(text_field status)" = "$2" ]
}

timeline_has() {
    fetch_public "/api/tracking/$1" > /dev/null
    grep -q "\"status\":\"$2\"" "$body_file"
}

stock_is() {
    call GET "/api/warehouse/products/$sku" > /dev/null
    [ "$(number_field quantityOnHand)" = "$1" ] && [ "$(number_field quantityReserved)" = "$2" ]
}

move_delivery() {
    if [ -n "${2:-}" ]; then
        call POST "/api/delivery/deliveries/$delivery_id/status" "{\"status\":\"$1\",\"note\":\"$2\"}"
    else
        call POST "/api/delivery/deliveries/$delivery_id/status" "{\"status\":\"$1\"}"
    fi
}

order_payload() {
    printf '{"customerName":"Smoke Customer","customerPhone":"+998901234567","city":"Tashkent","addressLine":"Smoke Street 1","items":[%s]}' "$1"
}

echo "== Gateway protection"
report "health is public" 200 "$(status "$base/actuator/health")"
report "liveness probe is public" 200 "$(status "$base/actuator/health/liveness")"
report "readiness probe is public" 200 "$(status "$base/actuator/health/readiness")"
report "products need an API key" 401 "$(status "$base/api/warehouse/products")"
report "a wrong API key is refused" 401 "$(status -H "X-API-Key: wrong-key-0123456789" "$base/api/warehouse/products")"
report "an unknown parcel is not found" 404 "$(status "$base/api/tracking/LGAAAAAAAAAA")"
report "internal paths are not routed" 404 "$(status -H "X-API-Key: $key" "$base/internal/reservations")"
report "path traversal is refused" 400 "$(status --path-as-is -H "X-API-Key: $key" "$base/api/orders/../internal/reservations")"

echo "== Warehouse and delivery setup"
report "product is created" 201 "$(call POST /api/warehouse/products "{\"sku\":\"$sku\",\"name\":\"Smoke product\",\"initialQuantity\":10}")"
report "new product is fully available" 10 "$(number_field available)"
report "duplicate sku is refused" 409 "$(call POST /api/warehouse/products "{\"sku\":\"$sku\",\"name\":\"Smoke product\",\"initialQuantity\":10}")"
report "courier is created" 201 "$(call POST /api/delivery/couriers "{\"name\":\"Smoke Courier $suffix\",\"phone\":\"$courier_phone\",\"maxActiveDeliveries\":5}")"
report "duplicate courier phone is refused" 409 "$(call POST /api/delivery/couriers "{\"name\":\"Other Courier $suffix\",\"phone\":\"$courier_phone\",\"maxActiveDeliveries\":5}")"

echo "== Order placed and confirmed"
report "an empty order is refused" 400 "$(call POST /api/orders "$(order_payload '')")"
report "order is placed" 201 "$(call POST /api/orders "$(order_payload "{\"sku\":\"$sku\",\"quantity\":2}")" "smoke-$suffix")"
order_id="$(text_field id)"
tracking_number="$(text_field trackingNumber)"
report "order is confirmed" CONFIRMED "$(text_field status)"
report "order has a tracking number" yes "$([ -n "$tracking_number" ] && echo yes || echo no)"
report "the same Idempotency-Key returns the same order" 200 "$(call POST /api/orders "$(order_payload "{\"sku\":\"$sku\",\"quantity\":2}")" "smoke-$suffix")"
report "the repeated request returns the original order" "$order_id" "$(text_field id)"
report "stock is reserved, not removed" yes "$(stock_is 10 2 && echo yes || echo no)"

echo "== Public tracking"
report "timeline is public" 200 "$(fetch_public "/api/tracking/$tracking_number")"
report "timeline contains no customer name" no "$(body_has 'Smoke Customer')"
report "timeline contains no address" no "$(body_has 'Smoke Street')"
eventually "timeline shows CREATED" timeline_has "$tracking_number" CREATED
eventually "timeline shows COURIER_ASSIGNED" timeline_has "$tracking_number" COURIER_ASSIGNED

echo "== Delivery lifecycle"
call GET "/api/delivery/deliveries?size=200" > /dev/null
delivery_id="$(grep -o "\"id\":\"[^\"]*\",\"orderId\":\"$order_id\"" "$body_file" | head -1 | cut -d'"' -f4 || true)"
report "delivery exists for the order" yes "$([ -n "$delivery_id" ] && echo yes || echo no)"
report "a step cannot be skipped" 409 "$(move_delivery DELIVERED)"
report "pick up" 200 "$(move_delivery PICKED_UP)"
eventually "order becomes IN_DELIVERY" order_status_is IN_DELIVERY
eventually "stock leaves the warehouse" stock_is 8 0
report "an order in delivery cannot be cancelled" 409 "$(call POST "/api/orders/$order_id/cancel")"
report "in transit" 200 "$(move_delivery IN_TRANSIT)"
report "a failed attempt needs a note" 400 "$(move_delivery FAILED)"
report "failed attempt" 200 "$(move_delivery FAILED "Recipient not at home")"
eventually "order shows the failed attempt" order_delivery_status_is FAILED
report "retry" 200 "$(move_delivery IN_TRANSIT)"
report "delivered" 200 "$(move_delivery DELIVERED)"
eventually "order becomes DELIVERED" order_status_is DELIVERED
eventually "timeline shows DELIVERED" timeline_has "$tracking_number" DELIVERED
eventually "timeline shows DELIVERY_FAILED" timeline_has "$tracking_number" DELIVERY_FAILED
report "a delivered parcel cannot move again" 409 "$(move_delivery IN_TRANSIT)"

echo "== Rejected and cancelled orders"
report "too much stock is rejected" 409 "$(call POST /api/orders "$(order_payload "{\"sku\":\"$sku\",\"quantity\":500}")")"
report "rejection reason is reported" yes "$(body_has '"reason":"INSUFFICIENT_STOCK"')"
report "an unknown product is rejected" 422 "$(call POST /api/orders "$(order_payload '{"sku":"NO-SUCH-SKU-0000","quantity":1}')")"
report "rejected orders leave stock untouched" yes "$(stock_is 8 0 && echo yes || echo no)"
report "second order is placed" 201 "$(call POST /api/orders "$(order_payload "{\"sku\":\"$sku\",\"quantity\":3}")")"
order_id="$(text_field id)"
tracking_number="$(text_field trackingNumber)"
report "second order is confirmed" CONFIRMED "$(text_field status)"
report "stock is reserved again" yes "$(stock_is 8 3 && echo yes || echo no)"
report "order is cancelled" 200 "$(call POST "/api/orders/$order_id/cancel")"
report "cancelled order has status CANCELLED" CANCELLED "$(text_field status)"
report "cancelling twice is accepted" 200 "$(call POST "/api/orders/$order_id/cancel")"
report "cancelled stock is released" yes "$(stock_is 8 0 && echo yes || echo no)"
eventually "timeline shows CANCELLED" timeline_has "$tracking_number" CANCELLED

if [ "$failures" -gt 0 ]; then
    echo "$failures check(s) failed"
    exit 1
fi
echo "All checks passed"
