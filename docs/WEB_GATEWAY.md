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

The cookie is HttpOnly, SameSite=Strict, scoped to `/api`, and has a 15-minute idle and 8-hour absolute server-side lifetime. State-changing browser endpoints require the `X-SupplyGo-Request: 1` header. Authentication, inventory, orders, business profiles, routes, chat, and dashboard summaries use live Java server data. The dashboard reuses the inventory and orders endpoints described below.

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

Business profile endpoints require an active server session:
- `GET /api/business-profile` loads the signed-in supplier or seller's profile; returns `profile: null` when they have not saved one yet.
- `PUT /api/business-profile` saves `fullName`, `businessName`, `contactNumber`, and `location`. Requests require the CSRF header.
- `GET /api/business-profiles/suppliers?offset=0` and `GET /api/business-profiles/sellers?offset=0` list role-authorized directories in pages of ten.
- The Java server enforces profile ownership and directory permissions. Profile data is persisted in `data/business-profiles.json`.

Route endpoints require an active server session:
- `GET /api/routes/locations?offset=0` and `GET /api/routes/roads?offset=0` load the saved network in pages of ten.
- `POST /api/routes/locations` saves a location with `id` and `name`.
- `POST /api/routes/roads` saves a two-way road with `from`, `to`, and distance `meters`.
- `GET /api/routes/shortest?from=ID&to=ID` returns the shortest saved path and its total distance.
- All signed-in roles can view and search routes. Only Admin and Production Manager can add or update locations and roads; the Java server enforces these permissions.
- The road network is persisted by the Java server in `data/routes.json`.

Chat endpoints require an active server session:
- `POST /api/chat/session` joins the shared room. It requires the CSRF header and returns the signed-in username.
- `DELETE /api/chat/session` leaves the room and revokes the short-lived UDP grant. It requires the CSRF header.
- `GET /api/chat/messages?after=0` returns newer room messages and a cursor. The browser polls this endpoint while the Chat page is open.
- `POST /api/chat/messages` sends a message. JSON field: `text` (1–240 characters; the UDP protocol also enforces its UTF-8 byte limit).

The gateway obtains a short-lived chat grant over the authenticated TLS/TCP session and runs the UDP client itself; the browser never receives the grant or opens a UDP socket. Joined users from all roles share the same room. Message history is temporary, limited to the gateway session's most recent 200 received messages, and cleared when the user joins a fresh room. UDP delivery is best-effort, and chat messages are not encrypted. Do not send passwords or sensitive business information.

Self-service account creation allows only supplier and seller roles. Admin and manager accounts must be provisioned by an operator through the existing Java account management command.
