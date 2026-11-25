# order-service API

Port 8081. Owns orders and runs the order flow across warehouse-service, delivery-service and tracking-service. Its own PostgreSQL database holds orders and their items.

Routes under `/api/orders` are public through the gateway and need an API key. Routes under `/internal/**` are for other services only and are never routed by the gateway.

## Order statuses

| Status | Meaning |
| --- | --- |
| `PENDING` | Accepted, the flow is running |
| `CONFIRMED` | Stock reserved and a courier assigned |
| `IN_DELIVERY` | Courier picked the parcel up; stock has left the warehouse |
| `DELIVERED` | Handed to the recipient |
| `CANCELLED` | Cancelled by the customer before pickup |
| `REJECTED` | The flow failed; the reason is in `rejectionReason` |

`deliveryStatus` shows the last delivery event (`PICKED_UP`, `IN_TRANSIT`, `FAILED`, `DELIVERED`).

## Order flow

```
1. save order PENDING with a new tracking number
2. tracking-service   CREATED event
3. warehouse-service  reserve stock
4. delivery-service   create delivery and assign a courier
5. order CONFIRMED
```

Tracking comes first because delivery-service sends `COURIER_ASSIGNED` right after step 4 and tracking-service requires `CREATED` as the first event of a parcel.

If step 2, 3 or 4 fails the order is compensated and ends as `REJECTED`:

| Failure | HTTP status | `reason` |
| --- | --- | --- |
| Not enough stock | 409 | `INSUFFICIENT_STOCK` |
| Unknown sku | 422 | `UNKNOWN_PRODUCT` |
| No courier has free capacity | 409 | `NO_COURIER_AVAILABLE` |
| A service is down, times out or answers 5xx | 503 | `SERVICE_UNAVAILABLE` |

Compensation, in this order, each step safe to repeat: cancel the delivery, release the stock, close the tracking timeline with `CANCELLED` (only when no delivery existed to do it). A rejected order is kept for audit; to try again the customer places a new order.

If a compensation step cannot be done because a service is unreachable, the order is flagged `compensation_pending`. A background sweeper retries every 30 seconds until it succeeds. The same sweeper rejects orders that stayed `PENDING` for more than 2 minutes, which covers a crash in the middle of the flow.

## Place an order

`POST /api/orders`

Optional header `Idempotency-Key` (up to 64 characters). A repeated key returns the existing order with 200 and causes no new side effects, including when that order was rejected.

```json
{
  "customerName": "Aziz Karimov",
  "customerPhone": "+998901234567",
  "city": "Tashkent",
  "addressLine": "Amir Temur 1, apt 5",
  "items": [
    { "sku": "BOX-M", "quantity": 2 },
    { "sku": "TAPE-1", "quantity": 1 }
  ]
}
```

201 with `Location: /api/orders/{id}`:

```json
{
  "id": "0b3c9a0e-8c62-4b9f-a9a3-2f6d3f0c8a55",
  "trackingNumber": "LGK4M9X2PQ7A",
  "status": "CONFIRMED",
  "deliveryStatus": null,
  "rejectionReason": null,
  "customerName": "Aziz Karimov",
  "customerPhone": "+998901234567",
  "city": "Tashkent",
  "addressLine": "Amir Temur 1, apt 5",
  "items": [
    { "sku": "BOX-M", "quantity": 2 },
    { "sku": "TAPE-1", "quantity": 1 }
  ],
  "createdAt": "2026-11-25T08:00:00Z",
  "updatedAt": "2026-11-25T08:00:00Z"
}
```

Duplicate skus are summed. Quantity is 1 to 1000, up to 50 lines, phone is 9 to 15 digits with an optional `+`.

Rejection body (RFC 7807), for example:

```json
{
  "title": "Order rejected",
  "status": 409,
  "detail": "Insufficient stock for sku BOX-M: requested 5, available 1",
  "orderId": "0b3c9a0e-8c62-4b9f-a9a3-2f6d3f0c8a55",
  "reason": "INSUFFICIENT_STOCK"
}
```

## Other public endpoints

| Call | Result |
| --- | --- |
| `GET /api/orders/{id}` | 200 or 404 |
| `GET /api/orders?status=&page=0&size=50` | Newest first, `size` up to 200 |
| `POST /api/orders/{id}/cancel` | 200 `CANCELLED`; repeating it is accepted; 409 once the parcel was picked up or if the order is not `CONFIRMED`; 503 if delivery-service is unreachable and the order stays `CONFIRMED` |

Cancelling cancels the delivery first, then releases the stock. If only the stock release fails, the order is still cancelled and the release is retried by the sweeper.

## Internal endpoint

`POST /internal/orders/{orderId}/delivery-events`, called by delivery-service's outbox:

```json
{ "eventId": "c2d4a8a0-3a54-4f0f-8d6b-0d2d9e2a7f10", "status": "PICKED_UP", "occurredAt": "2026-11-25T10:00:00Z" }
```

| Event | Effect |
| --- | --- |
| `PICKED_UP` | Commits the stock reservation, order becomes `IN_DELIVERY` |
| `IN_TRANSIT`, `FAILED` | Order stays `IN_DELIVERY`, `deliveryStatus` updated |
| `DELIVERED` | Commits the stock if that did not happen yet, order becomes `DELIVERED` |

Results: 200 applied or ignored as a duplicate or late event, 404 unknown order, 409 order is cancelled, rejected or pending, 503 stock could not be committed (the sender retries). Events after `DELIVERED` are ignored.

## Configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| `SERVER_PORT` | `8081` | HTTP port |
| `ORDER_DB_URL` | `jdbc:postgresql://localhost:5432/orders` | JDBC URL |
| `ORDER_DB_USER` | `orders` | Database user |
| `ORDER_DB_PASSWORD` | empty | Database password, never committed |
| `WAREHOUSE_SERVICE_URL` | `http://localhost:8082` | warehouse-service base URL |
| `DELIVERY_SERVICE_URL` | `http://localhost:8083` | delivery-service base URL |
| `TRACKING_SERVICE_URL` | `http://localhost:8084` | tracking-service base URL |

Outgoing calls time out after 2 seconds to connect and 3 seconds to read.

## Running alone

```powershell
$env:ORDER_DB_PASSWORD = "choose-a-local-password"
docker run -d --name order-db -e POSTGRES_USER=orders -e POSTGRES_PASSWORD=$env:ORDER_DB_PASSWORD -e POSTGRES_DB=orders -p 5435:5432 postgres:17
$env:ORDER_DB_URL = "jdbc:postgresql://localhost:5435/orders"
.\mvnw.cmd -pl order-service spring-boot:run
```

Placing an order needs the other three services running.
