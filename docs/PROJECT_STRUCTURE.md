# SupplyGo project structure

The project uses one Maven build and separate server/client Java entry points.
The current milestone implements TCP connectivity and configuration, while the
business application follows in later tasks. Package documentation tracks the
remaining planned areas without adding empty business classes.

| Location | Responsibility | Current status |
| --- | --- | --- |
| `Main.java` | Optional `server` / `client` dispatcher | Implemented |
| `server/ServerMain.java` | TCP backend launcher | Implemented |
| `client/ClientMain.java` | Swing application entry point | Implemented |
| `ui/ClientWindow.java` | Connection controls and activity log | Implemented |
| `config/NetworkConfig.java` | Immutable endpoint settings and validation | Implemented |
| `client/SupplyGoTcpClient.java` | TCP socket, correlated requests and timeouts | Implemented |
| `server/SupplyGoTcpServer.java` | Bounded concurrent accept loop and shutdown | Implemented |
| `server/ClientHandler.java` | Per-connection request processing | Implemented |
| `protocol/` | Message types, requests, responses, JSON and framing | PING/QUIT implemented |
| `model/` | Domain records and user roles | Package reserved |
| `service/` | Server-side authorization and business rules | Package reserved |
| `algorithm/` | Priority rankings, FIFO, Dijkstra routing | Package reserved |
| `storage/` | Server-only persistence | Package reserved |
| `os/` | Separate scheduling, synchronization, memory, deadlock demos | Optional packages reserved |
| `src/main/resources/` | Packaged configuration defaults | Implemented |
| `src/test/java/com/supplygo/config/` | Configuration regression tests | Implemented |
| `src/test/java/com/supplygo/algorithm/` | Algorithm tests | Directory reserved |
| `src/test/java/com/supplygo/service/` | Business-rule and concurrency tests | Directory reserved |
| `src/test/java/com/supplygo/network/` | Real TCP socket integration tests | Implemented |
| `src/test/java/com/supplygo/protocol/` | JSON and framing validation | Implemented |
| `data/` | Server runtime files | Placeholder only; generated content ignored |

## Dependency boundaries

The frontend uses the TCP client to send requests. The server dispatches those
requests to services. Services use domain models, algorithms, and repositories.
The client does not call server services or open the database directly.

The original project has supplier, seller, supplier-manager, seller-manager,
production-manager, and administrator roles. Later milestones must preserve
their capabilities and enforce authorization on the server, not only in screens.

OS simulators are educational tools, separate from the actual thread scheduler
or memory manager of the host operating system. Add them when the assessment
requires them. Application inventory synchronization belongs in the services.

## Next milestone

Build authentication and role-based authorization as a new vertical slice, then
server-owned persistence and business operations. Extend the existing protocol
with explicit schemas and duplicate-request rules before adding order mutations.
UDP messenger and optional OS simulations remain separate tasks.
