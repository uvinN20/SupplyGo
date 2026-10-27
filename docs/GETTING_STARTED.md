# Running SupplyGo with Java 25

This milestone connects a Swing desktop frontend to a concurrent Java TCP server.
You can connect, send PING requests, receive PONG, disconnect, and reconnect.
TLS account registration and sign-in are documented in [AUTHENTICATION.md](AUTHENTICATION.md).
Inventory, orders, business profiles and a shared UDP chat room are available; their guides are linked below.

## Requirements

- JDK 25, including `java` and `javac`.
- Maven 3.9 or newer; Maven 3.10 is suitable.
- Git, VS Code, and the Extension Pack for Java.
- A graphical desktop for the Swing client. The server and tests also run headless.

Check your Windows Command Prompt:

```bat
java -version
javac -version
mvn -version
```

Maven must report JDK 25. Otherwise correct `JAVA_HOME` and reopen the terminal.

## Get the project and build

For a new checkout:

```bat
git clone https://github.com/uvinN20/SupplyGo.git
cd SupplyGo
mvn clean verify
```

For an existing checkout, commit or stash local changes, switch to `main`,
and pull the latest changes before building:

```bat
git switch main
git pull --ff-only origin main
mvn clean verify
```

The build downloads dependencies on first use and produces a JAR containing its
runtime dependencies: `target/supplygo-0.1.0-SNAPSHOT.jar`.

## Run a local demonstration

Open two terminals in the project directory. Start the backend in the first:

```bat
java -jar target/supplygo-0.1.0-SNAPSHOT.jar server
```

It prints `SupplyGo TCP server listening on 127.0.0.1:7070` and stays running.
Stop it with **Ctrl+C**, which closes the listener and active client connections.

Start the frontend in the second terminal:

```bat
java -jar target/supplygo-0.1.0-SNAPSHOT.jar client
```

1. Keep server `127.0.0.1` and TCP port `7070`, then click **Connect**.
2. The frontend sends an initial PING. After PONG, its status becomes **Connected**.
3. Click **Send Ping** to send additional requests.
4. Click **Disconnect** to receive BYE and return to **Offline**.
5. Click **Connect** again to start a new connection.

Open several client windows to demonstrate concurrent connections. A failed
connection or request is shown in the activity log and restores the Connect
button. Network work runs in the background, keeping the window responsive.
Closing a client window closes its socket without waiting for a reply.

The existing separate entry points are also available through the packaged JAR:

```bat
java -cp target/supplygo-0.1.0-SNAPSHOT.jar com.supplygo.server.ServerMain
java -cp target/supplygo-0.1.0-SNAPSHOT.jar com.supplygo.client.ClientMain
```

Use `--help` as the JAR argument for command usage. These commands also work on
Linux with JDK 25 and Maven; the Swing client requires a graphical desktop.

## Two laptops on a local network

On the server laptop, explicitly listen on all IPv4 interfaces:

```bat
java -Dsupplygo.server.bind=0.0.0.0 -jar target/supplygo-0.1.0-SNAPSHOT.jar server
```

Find that laptop's Wi-Fi IPv4 address with `ipconfig`. On the client laptop, enter
that address (for example `192.168.1.10`) in the frontend's **Server** field, or
launch with:

```bat
java -Dsupplygo.server.host=192.168.1.10 -jar target/supplygo-0.1.0-SNAPSHOT.jar client
```

Both laptops must be on a reachable local network. If Windows Firewall prompts,
allow the server on your private network. Do not enter `0.0.0.0` as the client
address: it is a server listening option. This is a plain TCP classroom demo;
account and business operations require the TLS setup in [AUTHENTICATION.md](AUTHENTICATION.md).

## Configuration and connection limits

Put Java `-D` options **before** `-jar`.

| Setting | Default | Meaning |
| --- | --- | --- |
| `supplygo.server.host` | `127.0.0.1` | Initial frontend server field |
| `supplygo.tcp.port` | `7070` | Server port and initial frontend port |
| `supplygo.udp.port` | `7071` | UDP chat listener port on the TLS server |
| `supplygo.server.bind` | `127.0.0.1` | Server-only listening address, set with `-D` |

The first three defaults are in `src/main/resources/supplygo.properties`.
The server supports at most 32 handlers; extra connections are closed when all
handlers are occupied. Each connection closes after 60 seconds without incoming
bytes. Client TCP connection attempts have a 3-second timeout and responses a
5-second read timeout; operating-system DNS resolution may take additional time.

If a connection becomes stale, **Send Ping** detects it and restores **Offline**;
there is no continuous heartbeat in this milestone. Reconnect manually. The
client closes after any response timeout or invalid reply and never silently
retries a request.

- **Connection refused:** Start the server and check the address/port.
- **Address already in use:** Stop the existing server or choose another TCP port
  with `-Dsupplygo.tcp.port=7072`, then use the same port in the client.
- **Connection timeout:** Check LAN reachability and the private-network firewall.
- **Client requires a graphical desktop:** Launch Swing on your Windows desktop.

## Validation and development

`mvn clean verify` runs configuration, protocol, and real loopback socket tests,
including concurrent clients, malformed/oversized frames, disconnection,
response correlation, timeouts, and shutdown. Tests bind temporary ports.

Use Java throughout. Work on task branches, review pull requests, and use commits
such as `feat(network): add TCP request handling`. The server owns shared state;
clients must not access repositories directly. See
[NETWORK_PROTOCOL.md](NETWORK_PROTOCOL.md) and [PROJECT_STRUCTURE.md](PROJECT_STRUCTURE.md).

After signing in over TLS, use the Inventory tab to browse stock. Administrators
and production managers can create products, receive batches and withdraw stock
in FIFO order. See [INVENTORY.md](INVENTORY.md) for a walkthrough.

The Orders tab lets sellers place and track orders. Administrators, seller
managers and production managers can approve, fulfil or cancel them. Inventory
shows available, on-hand and reserved units. Before upgrading existing inventory
data, read the migration and backup instructions in [ORDERS.md](ORDERS.md).

For supplier/seller profiles and manager directories, see [Business profiles](BUSINESS_PROFILES.md).

For the shared room, LAN setup and UDP delivery behavior, see [UDP_CHAT.md](UDP_CHAT.md).

## Route planning

See [ROUTING.md](ROUTING.md) for the Routes screen, permissions, persistence, Dijkstra algorithm and protocol.

## Browser sign-in preview

The React web preview now authenticates accounts through the Java TLS server. Start the TCP server, start the Java web gateway, and then start Vite in three terminals. Keep the gateway and Vite on the same computer for this development setup:

```powershell
# Terminal 1: start the TLS TCP server using the keystore/truststore settings in AUTHENTICATION.md
java -Dsupplygo.tls=true -Djavax.net.ssl.keyStore=certs/server.p12 -Djavax.net.ssl.keyStorePassword=YOUR_SERVER_STORE_PASSWORD -jar target/supplygo-0.1.0-SNAPSHOT.jar server

# Terminal 2: web gateway verifies the TCP server certificate using its truststore
java -Dsupplygo.tls=true -Djavax.net.ssl.trustStore=certs/trust.p12 -Djavax.net.ssl.trustStorePassword=YOUR_CLIENT_STORE_PASSWORD -Dsupplygo.server.host=localhost -jar target/supplygo-0.1.0-SNAPSHOT.jar web

# Terminal 3, from frontend/: Vite proxies /api to the local gateway
npm run dev
```

Open the URL printed by Vite. Create a supplier or seller account, or sign in with an account already provisioned on the Java server. The browser session uses an HttpOnly cookie. The current dashboard, inventory and orders remain clearly marked sample data; edits do not change server records. See [WEB_GATEWAY.md](WEB_GATEWAY.md) for configuration and deployment notes.
