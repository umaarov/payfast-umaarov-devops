# Frontend

A React 19 and TypeScript single-page app built with Vite, in `frontend/`. It has two parts:

| Part | Address | Sign-in | What it does |
| --- | --- | --- | --- |
| Public tracking | `/` and `/track/<number>` | none | Search a tracking number and see the parcel timeline, newest step first. Refreshes every 15 seconds until the parcel is delivered or cancelled |
| Staff console | `/console/...` | API key | Dashboard, orders, deliveries, couriers, warehouse |

The app talks only to the gateway through relative `/api/...` paths, so the browser sees one origin and no CORS setup is needed. Design decisions: [ADR-017](decisions.md) for the app and [ADR-018](decisions.md) for the container.

## Staff console

| Screen | Address | What you can do |
| --- | --- | --- |
| Dashboard | `/console` | Order counts per status (each is a link to the filtered list), low stock (5 units or fewer available), courier load, the latest orders |
| Orders | `/console/orders` | Filter by status, page through orders, open one |
| New order | `/console/orders/new` | Customer data and any number of product lines chosen from the warehouse list; rejections show the reason and a link to the rejected order |
| Order | `/console/orders/<id>` | Customer, items, delivery and courier, the public tracking link; cancel a `CONFIRMED` order after a confirmation step. Refreshes every 5 seconds while the order is active |
| Deliveries | `/console/deliveries` | Filter by status and courier; move a parcel to its next step; a failed attempt needs a note |
| Couriers | `/console/couriers` | Add a courier, see the load of each, take a courier off duty or back on duty |
| Warehouse | `/console/warehouse` | Add a product, receive stock, see on hand, reserved and available, filter the page by SKU or name |

Rules the app follows:

- Delivery steps offered in the UI are `ASSIGNED` to `PICKED_UP` to `IN_TRANSIT` to `DELIVERED`, plus `FAILED` from `IN_TRANSIT` and back to `IN_TRANSIT`. The backend still decides; a refused step shows the server message.
- Every order is sent with a generated `Idempotency-Key`. Submitting the same form again after an error reuses the key, so a retry can never create a second order. Changing any field creates a new key.
- Forms check the same limits as the backend (phone 9 to 15 digits, quantity 1 to 1000, sku characters, courier capacity 1 to 50) before sending anything.
- A `401` from the gateway removes the key and sends the user to the sign-in page.

## The API key

The sign-in page checks the key with one call to the warehouse list. An accepted key is kept in the browser tab's `sessionStorage`, so it is gone when the tab closes, and "Sign out" removes it at once. It is sent only in the `X-API-Key` header of console calls. The public tracking page never sends it.

The key is the one from `GATEWAY_API_KEYS` in `.env`; with several keys, any one works.

## Run it in development

Prerequisites: Node.js 22.12 or newer. The backend must be running and reachable as a gateway (see [docker.md](docker.md)).

```powershell
cd frontend
npm ci
npm run dev
```

Open `http://localhost:5173`. Vite proxies `/api` to `http://localhost:8080`. For a gateway elsewhere:

```powershell
$env:GATEWAY_URL = "http://localhost:8090"
npm run dev
```

## Run it in a container

`docker compose up --build -d` also builds and starts the frontend on `http://localhost:3000`, served by nginx with no Node.js needed at run time. nginx proxies `/api/` to the gateway, so the browser sees one origin and no CORS settings are needed. The image, the proxy rules and the security headers are described in [docker.md](docker.md); the cluster setup is in [kubernetes.md](kubernetes.md).

Because every response carries a policy of `default-src 'self'`, the app must not use inline scripts, inline `style` attributes in markup, remote fonts, or calls to another origin. Style values set from code, such as the width of the courier load bar, are allowed.

## Commands

| Command | Purpose |
| --- | --- |
| `npm run dev` | Dev server with hot reload |
| `npm run lint` | ESLint, TypeScript and React hooks rules |
| `npm test` | Vitest and Testing Library, no browser and no backend needed |
| `npm run build` | Type-check, then a production bundle in `frontend/dist` |
| `npm run preview` | Serve the production bundle locally |

## Code layout

```
frontend/src
  api/         request wrapper, ApiError, typed endpoint functions
  auth/        API key store (sessionStorage) and the hook that reads it
  components/  layouts, status badges, error notice, forms helpers
  lib/         validation, formatting, delivery lifecycle, dashboard statistics
  pages/       one file per screen group
  test/        test setup, fetch mock, data builders
```

## Tests

67 tests cover the request wrapper (key header, problem details, network errors, 401 handling), validation and lifecycle rules, and every screen with a mocked `fetch`: sign-in and route protection, tracking, order placement with idempotency, rejection and cancellation, delivery steps, courier and warehouse forms. They run in CI job `frontend` together with lint, build and `npm audit` for production dependencies.

Sign-in, dashboard, deliveries and the public tracking page were also checked by hand in a browser against the real backend services.

## Limits

- Lists use page-size paging because the API returns plain arrays without totals. The dashboard looks at the latest 200 records.
- There are no user accounts. Whoever has the API key has full access.
- The UI follows the system light or dark setting. It has a layout for narrow screens, but it was only checked on a desktop browser.
