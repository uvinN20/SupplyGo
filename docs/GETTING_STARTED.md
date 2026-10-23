# Running SupplyGo with Java 25

This milestone connects a Swing desktop frontend to a concurrent Java TCP server.
You can connect, send PING requests, receive PONG, disconnect, and reconnect.
TLS account registration and sign-in are documented in [AUTHENTICATION.md](AUTHENTICATION.md).
Inventory, orders, and UDP chat follow in later tasks.

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

## Get the feature and build

During pull request review, use this branch. After it is merged, use `main`.
Commit or stash existing local changes before switching branches.

```bat
cd C:\Users\uvinn\SupplyGo
git fetch origin
git switch feature/tcp-server-client
git pull --ff-only origin feature/tcp-server-client
mvn clean verify
```

If the branch is not available locally, use
`git switch --track origin/feature/tcp-server-client` after fetching.

For a new checkout:

```bat
git clone --branch feature/tcp-server-client https://github.com/uvinN20/SupplyGo.git
cd SupplyGo
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
real account credentials require the later TLS/authentication implementation.

## Configuration and connection limits

Put Java `-D` options **before** `-jar`.

| Setting | Default | Meaning |
| --- | --- | --- |
| `supplygo.server.host` | `127.0.0.1` | Initial frontend server field |
| `supplygo.tcp.port` | `7070` | Server port and initial frontend port |
| `supplygo.udp.port` | `7071` | Reserved; no UDP listener is started |
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
future clients must not access repositories directly. See
[NETWORK_PROTOCOL.md](NETWORK_PROTOCOL.md) and [PROJECT_STRUCTURE.md](PROJECT_STRUCTURE.md).

After signing in over TLS, use the Inventory tab to browse stock. Administrators
and production managers can create products, receive batches and withdraw stock
in FIFO order. See [INVENTORY.md](INVENTORY.md) for a walkthrough.

The Orders tab lets sellers place and track orders. Administrators, seller
managers and production managers can approve, fulfil or cancel them. Inventory
shows available, on-hand and reserved units. Before upgrading existing inventory
data, read the migration and backup instructions in [ORDERS.md](ORDERS.md).
