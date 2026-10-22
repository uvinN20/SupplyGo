# SupplyGo project structure

The project uses one Maven build and separate server/client Java entry points.
The current milestone includes working launchers and configuration, not the full
business application. Package documentation tracks planned areas without adding
empty business classes that pretend to implement features.

| Location | Responsibility | Current status |
| --- | --- | --- |
| `Main.java` | Optional `server` / `client` dispatcher | Implemented |
| `server/ServerMain.java` | Backend configuration check and launcher | Implemented; no listener yet |
| `client/ClientMain.java` | Swing application entry point | Implemented |
| `ui/ClientWindow.java` | Offline desktop shell | Implemented |
| `config/NetworkConfig.java` | Immutable endpoint settings and validation | Implemented |
| `protocol/` | Shared requests, responses, errors, notifications | Package reserved |
| `model/` | Domain records and user roles | Package reserved |
| `service/` | Server-side authorization and business rules | Package reserved |
| `algorithm/` | Priority rankings, FIFO, Dijkstra routing | Package reserved |
| `storage/` | Server-only persistence | Package reserved |
| `os/` | Separate scheduling, synchronization, memory, deadlock demos | Optional packages reserved |
| `src/main/resources/` | Packaged configuration defaults | Implemented |
| `src/test/java/com/supplygo/config/` | Configuration regression tests | Implemented |
| `src/test/java/com/supplygo/algorithm/` | Algorithm tests | Directory reserved |
| `src/test/java/com/supplygo/service/` | Business-rule and concurrency tests | Directory reserved |
| `src/test/java/com/supplygo/network/` | TCP/UDP integration tests | Directory reserved |
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

Implement a small TCP vertical slice: start the listener, connect a client, send
a request, receive a response, and handle disconnects. Add actual protocol types
and connection code there. Authentication, storage, dashboards, and UDP chat
follow in their own reviewed tasks.
