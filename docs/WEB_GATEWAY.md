# SupplyGo Web Gateway

The React/Vite browser preview uses the Java gateway for authentication. The gateway accepts same-origin JSON requests, forwards account operations over the existing certificate-verified TLS/TCP protocol, and returns an HttpOnly session cookie. The browser never receives the upstream TCP session or stores the password after the request.

## Local development

1. Configure the Java TCP server certificate and client truststore as described in [AUTHENTICATION.md](AUTHENTICATION.md), then start the TCP server.
2. Start the gateway from the project root. Use the trusted certificate for `localhost` and configure the JVM truststore:

   ```powershell
   java -Dsupplygo.tls=true `
     -Djavax.net.ssl.trustStore=certs/trust.p12 `
     -Djavax.net.ssl.trustStorePassword=YOUR_CLIENT_STORE_PASSWORD `
     -Dsupplygo.server.host=localhost `
     -jar target/supplygo-0.1.0-SNAPSHOT.jar web
   ```

3. In `frontend/`, run `npm run dev`, then open the URL Vite prints. Vite forwards `/api` to `http://127.0.0.1:7080`.

Configuration properties:

| Property | Default | Purpose |
| --- | --- | --- |
| `supplygo.web.bind` | `127.0.0.1` | Gateway bind interface; loopback by default |
| `supplygo.web.port` | `7080` | HTTP port for the local development gateway |
| `supplygo.server.host` | from `supplygo.properties` | TLS server hostname; certificate hostname validation stays enabled |
| `supplygo.tcp.port` | from `supplygo.properties` | TLS server port |
| `supplygo.web.secureCookie` | `false` | Set `true` when the browser reaches the gateway over HTTPS |

The HTTP gateway is intended for local Vite development. For a deployed browser app, put the gateway behind HTTPS on the same origin as the frontend and set `-Dsupplygo.web.secureCookie=true`; do not expose the development HTTP listener directly to the internet. Configure the reverse proxy to preserve the `/api` path. No wildcard CORS is enabled.

The cookie is HttpOnly, SameSite=Strict, scoped to `/api`, and has a 15-minute idle and 8-hour absolute server-side lifetime. State-changing browser endpoints require the `X-SupplyGo-Request: 1` header. Authentication, inventory, orders, and dashboard summaries use live Java server data. The dashboard reuses the inventory and orders endpoints described below.

Inventory endpoints require an active server session:
- `GET /api/inventory` lists persisted products and stock.
- `POST /api/inventory/products` creates a product and its opening stock. JSON fields: `id`, `name`, and `quantity`.
- `POST /api/inventory/stock` receives or withdraws stock. JSON fields: `productId`, `quantity`, and `action` (`receive` or `withdraw`).

Inventory and orders are persisted by the Java server in `data/inventory.json`; back up that file before moving or deleting the data directory. The server enforces role permissions. Only administrators and production managers can create products or change stock.

Order endpoints require an active server session:
- `GET /api/orders` lists orders visible to the signed-in seller or order manager.
- `POST /api/orders` places an order. JSON fields: `clientOrderId` (UUID), `productId`, and `quantity`. Only sellers can place orders.
- `POST /api/orders/status` changes an order status. JSON fields: `orderId` and `status` (`APPROVED`, `FULFILLED`, or `CANCELLED`). Seller managers, production managers, and administrators can approve, fulfil, or cancel. A seller can cancel only their own pending order.

Orders reserve available stock when placed, release it when cancelled, and consume it when fulfilled.

Self-service account creation allows only supplier and seller roles. Admin and manager accounts must be provisioned by an operator through the existing Java account management command.
