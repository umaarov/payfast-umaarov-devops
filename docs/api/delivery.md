# delivery-service API

Port 8083. Owns couriers, deliveries and the outbox of events sent to other services, in its own PostgreSQL database.

Routes under `/api/delivery/**` are public through the gateway and need an API key. Routes under `/internal/**` are for other services only and are never routed by the gateway.

## Delivery lifecycle

```
ASSIGNED -> PICKED_UP -> IN_TRANSIT -> DELIVERED
ASSIGNED -> CANCELLED
IN_TRANSIT -> FAILED -> IN_TRANSIT
```

| Rule | Behavior |
| --- | --- |
| Steps cannot be skipped or reversed | 409 |
| Cancellation | Only from `ASSIGNED`, only through the internal endpoint |
| `FAILED` | Needs a `note`; keeps the courier slot busy; can be retried with `IN_TRANSIT` |
| `DELIVERED`, `CANCELLED` | Final; the courier slot is freed |
| Repeating the current status | 200, nothing changes, no event is queued |
| Two simultaneous updates of one delivery | One wins, the other gets 409 (optimistic lock) |

## Courier assignment

A delivery is assigned when it is created. Candidates are couriers that are `AVAILABLE` and below their `maxActiveDeliveries`; the least loaded wins, ties are broken by name. The slot is taken with one conditional `UPDATE`, so simultaneous requests can never exceed a courier's capacity. If no courier has free capacity the request fails with 409 and nothing is stored, and order-service then releases the stock.

## Events sent to other services

Every status change writes rows to the `outbox_events` table in the same transaction as the change. A background dispatcher posts them every 2 seconds.

| Target | When | Endpoint |
| --- | --- | --- |
| tracking-service | Every status change | `POST /internal/tracking-events` |
| order-service | `PICKED_UP`, `IN_TRANSIT`, `DELIVERED`, `FAILED` | `POST /internal/orders/{orderId}/delivery-events` |

Dispatch rules: events of one target are sent in order; a 5xx, 408, 429 or network failure keeps the event pending and pauses that target until the next round; any other 4xx marks the event `REJECTED` and moves on. Rows are kept for audit. Each event carries its own `eventId`, so receivers can ignore duplicates.

Tracking payload:

```json
{
  "eventId": "6f1f0c52-5b3e-4b9d-9a43-6a0a5d9c4e11",
  "trackingNumber": "LGK4M9X2PQ7A",
  "status": "PICKED_UP",
  "description": "Parcel picked up by courier",
  "location": "Tashkent",
  "occurredAt": "2026-11-25T10:00:00Z"
}
```

Order payload:

```json
{
  "eventId": "c2d4a8a0-3a54-4f0f-8d6b-0d2d9e2a7f10",
  "orderId": "0b3c9a0e-8c62-4b9f-a9a3-2f6d3f0c8a55",
  "status": "PICKED_UP",
  "occurredAt": "2026-11-25T10:00:00Z"
}
```

Tracking events contain the city only: no recipient name, phone or street address.

## Courier endpoints

| Call | Result |
| --- | --- |
| `POST /api/delivery/couriers` | 201; body `{ "name", "phone", "maxActiveDeliveries" }`, capacity 1 to 50, default 5; 409 on duplicate phone |
| `GET /api/delivery/couriers?page=0&size=50` | Sorted by name |
| `GET /api/delivery/couriers/{id}` | 200 or 404 |
| `POST /api/delivery/couriers/{id}/availability` | Body `{ "available": false }` takes a courier off duty; running deliveries continue |

## Delivery endpoints

| Call | Result |
| --- | --- |
| `GET /api/delivery/deliveries?courierId=&status=&page=&size=` | Newest first, filters optional |
| `GET /api/delivery/deliveries/{id}` | 200 or 404 |
| `POST /api/delivery/deliveries/{id}/status` | Body `{ "status": "FAILED", "note": "Recipient not at home" }` |

Delivery response:

```json
{
  "id": "9a7e31f4-4e0b-4c3a-8a4f-1c7a9d0e55aa",
  "orderId": "0b3c9a0e-8c62-4b9f-a9a3-2f6d3f0c8a55",
  "trackingNumber": "LGK4M9X2PQ7A",
  "status": "ASSIGNED",
  "courier": { "id": "e1c5...", "name": "Bekzod", "phone": "+998901112233" },
  "recipientName": "Aziz Karimov",
  "recipientPhone": "+998901234567",
  "city": "Tashkent",
  "addressLine": "Amir Temur 1, apt 5",
  "failureReason": null,
  "createdAt": "2026-11-25T08:00:00Z",
  "updatedAt": "2026-11-25T08:00:00Z"
}
```

## Internal endpoints

| Call | Result |
| --- | --- |
| `POST /internal/deliveries` | Creates and assigns. 201 new, 200 if the order already has a delivery, 409 if no courier is free |
| `GET /internal/deliveries/by-order/{orderId}` | 200 or 404 |
| `POST /internal/deliveries/by-order/{orderId}/cancel` | 200; 409 once the parcel was picked up; repeating it is accepted |

Create body:

```json
{
  "orderId": "0b3c9a0e-8c62-4b9f-a9a3-2f6d3f0c8a55",
  "trackingNumber": "LGK4M9X2PQ7A",
  "recipientName": "Aziz Karimov",
  "recipientPhone": "+998901234567",
  "city": "Tashkent",
  "addressLine": "Amir Temur 1, apt 5"
}
```

Errors use RFC 7807 problem details.

## Configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| `SERVER_PORT` | `8083` | HTTP port |
| `DELIVERY_DB_URL` | `jdbc:postgresql://localhost:5432/delivery` | JDBC URL |
| `DELIVERY_DB_USER` | `delivery` | Database user |
| `DELIVERY_DB_PASSWORD` | empty | Database password, never committed |
| `TRACKING_SERVICE_URL` | `http://localhost:8084` | Base URL of tracking-service |
| `ORDER_SERVICE_URL` | `http://localhost:8081` | Base URL of order-service |

Timeouts for outgoing calls: 2 seconds to connect, 3 seconds to read.

## Running alone

```powershell
$env:DELIVERY_DB_PASSWORD = "choose-a-local-password"
docker run -d --name delivery-db -e POSTGRES_USER=delivery -e POSTGRES_PASSWORD=$env:DELIVERY_DB_PASSWORD -e POSTGRES_DB=delivery -p 5434:5432 postgres:17
$env:DELIVERY_DB_URL = "jdbc:postgresql://localhost:5434/delivery"
.\mvnw.cmd -pl delivery-service spring-boot:run
```

Without the other services running, events stay `PENDING` and are sent when those services appear.
