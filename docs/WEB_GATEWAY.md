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

The cookie is HttpOnly, SameSite=Strict, scoped to `/api`, and has a 15-minute idle and 8-hour absolute server-side lifetime. State-changing browser endpoints require the `X-SupplyGo-Request: 1` header. Authentication is the only dashboard data connected in this phase: dashboard, inventory and orders remain sample data until their API integration is implemented.

Self-service account creation allows only supplier and seller roles. Admin and manager accounts must be provisioned by an operator through the existing Java account management command.
