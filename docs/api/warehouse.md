# warehouse-service API

Port 8082. Owns products, stock counters and reservations in its own PostgreSQL database.

Routes under `/api/warehouse/**` are public through the gateway. Routes under `/internal/**` are for other services only and are never routed by the gateway.

## Stock model

| Counter | Meaning |
| --- | --- |
| `quantityOnHand` | Units physically in the warehouse |
| `quantityReserved` | Units promised to orders that have not left yet |
| `available` | `quantityOnHand - quantityReserved` |

Reservation lifecycle:

```
RESERVED -> RELEASED   order cancelled or a later step failed; stock becomes available again
RESERVED -> COMMITTED  parcel left the warehouse; on-hand and reserved both drop
```

`RELEASED` and `COMMITTED` are final. Repeating the same transition is accepted and changes nothing; the opposite transition returns 409.

Overselling is prevented by one conditional `UPDATE ... WHERE on_hand - reserved >= quantity` per product, so concurrent reservations cannot both take the last unit. Database check constraints repeat the rules. There is no delete endpoint for products or reservations.

## Public endpoints

### Create product

`POST /api/warehouse/products`

```json
{ "sku": "BOX-M", "name": "Medium box", "initialQuantity": 100 }
```

201 with `Location: /api/warehouse/products/BOX-M`:

```json
{
  "id": "5f0c7b0e-5a59-4a1c-9d0c-3e2d8f0f7a11",
  "sku": "BOX-M",
  "name": "Medium box",
  "quantityOnHand": 100,
  "quantityReserved": 0,
  "available": 100,
  "createdAt": "2026-11-25T08:00:00Z",
  "updatedAt": "2026-11-25T08:00:00Z"
}
```

400 on validation errors, 409 if the sku exists. `sku` allows letters, digits, `.`, `_`, `-` up to 64 characters.

### List products

`GET /api/warehouse/products?page=0&size=50` sorted by sku, `size` up to 200.

### Get product

`GET /api/warehouse/products/{sku}` returns 200 or 404.

### Receive stock

`POST /api/warehouse/products/{sku}/receipts`

```json
{ "quantity": 25 }
```

Returns the updated product. `quantity` is 1 to 1,000,000.

## Internal endpoints

### Reserve

`POST /internal/reservations`

```json
{
  "orderId": "0b3c9a0e-8c62-4b9f-a9a3-2f6d3f0c8a55",
  "items": [
    { "sku": "BOX-M", "quantity": 2 },
    { "sku": "TAPE-1", "quantity": 1 }
  ]
}
```

| Status | Meaning |
| --- | --- |
| 201 | Reservation created |
| 200 | A reservation for this `orderId` already exists; it is returned unchanged |
| 404 | A sku does not exist; nothing is reserved |
| 409 | Not enough stock; nothing is reserved |

Duplicate skus in one request are summed.

409 body for insufficient stock:

```json
{
  "title": "Insufficient stock",
  "status": 409,
  "detail": "Insufficient stock for sku TAPE-1: requested 1, available 0",
  "sku": "TAPE-1",
  "requested": 1,
  "available": 0
}
```

### Read, release, commit

| Call | Result |
| --- | --- |
| `GET /internal/reservations/{orderId}` | 200 or 404 |
| `POST /internal/reservations/{orderId}/release` | Reservation in `RELEASED`; 409 if it was committed |
| `POST /internal/reservations/{orderId}/commit` | Reservation in `COMMITTED`; 409 if it was released |

## Errors

All errors use RFC 7807 problem details (`application/problem+json`).

## Configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| `SERVER_PORT` | `8082` | HTTP port |
| `WAREHOUSE_DB_URL` | `jdbc:postgresql://localhost:5432/warehouse` | JDBC URL |
| `WAREHOUSE_DB_USER` | `warehouse` | Database user |
| `WAREHOUSE_DB_PASSWORD` | empty | Database password, never committed |

## Running alone

Until Docker Compose arrives, start a local PostgreSQL, then the service. In PowerShell:

```powershell
$env:WAREHOUSE_DB_PASSWORD = "choose-a-local-password"
docker run -d --name warehouse-db -e POSTGRES_USER=warehouse -e POSTGRES_PASSWORD=$env:WAREHOUSE_DB_PASSWORD -e POSTGRES_DB=warehouse -p 5432:5432 postgres:17
.\mvnw.cmd -pl warehouse-service spring-boot:run
```

Flyway creates the schema on startup.
