# SupplyGo project structure

The project uses one Maven build and separate server/client Java entry points.
The current build implements TCP/TLS connectivity, accounts, inventory and
order management and business profiles. Package documentation tracks the remaining planned areas.

| Location | Responsibility | Current status |
| --- | --- | --- |
| `Main.java` | Optional `server` / `client` dispatcher | Implemented |
| `server/ServerMain.java` | TCP backend launcher | Implemented |
| `client/ClientMain.java` | Swing application entry point | Implemented |
| `ui/ClientWindow.java` | Connection, account and inventory controls | Implemented |
| `ui/BusinessProfilePanel.java` | Business profile editor and manager directories | Implemented |
| `ui/OrderPanel.java` | Seller and manager order screens | Implemented |
| `config/NetworkConfig.java` | Immutable endpoint settings and validation | Implemented |
| `client/SupplyGoTcpClient.java` | TCP socket, correlated requests and timeouts | Implemented |
| `server/SupplyGoTcpServer.java` | Bounded concurrent accept loop and shutdown | Implemented |
| `server/ClientHandler.java` | Per-connection request processing and TLS handshake | Implemented |
| `server/RequestRouter.java` | Per-connection identity and authorization checks | Implemented |
| `server/UserAdminMain.java` | Offline account provisioning | Implemented |
| `protocol/` | Message types, requests, responses, JSON and framing | Implemented for connectivity, accounts, inventory, orders and profiles |
| `model/` | Users, roles, products, fruit batches, orders and business profiles | Implemented |
| `service/` | Authentication, sessions, inventory, orders and business profile permissions | Implemented |
| `algorithm/` | Priority rankings and Dijkstra routing | Package reserved; FIFO is in shared stock operations |
| `storage/` | Locked account, inventory/order and business profile stores | Implemented |
| `security/` | Password hashing and JVM TLS context | Implemented |
| `os/` | Separate scheduling, synchronization, memory, deadlock demos | Optional packages reserved |
| `src/main/resources/` | Packaged configuration defaults | Implemented |
| `src/test/java/com/supplygo/config/` | Configuration regression tests | Implemented |
| `src/test/java/com/supplygo/algorithm/` | Algorithm tests | Directory reserved |
| `src/test/java/com/supplygo/service/` | Business-rule, persistence and concurrency tests | Implemented |
| `src/test/java/com/supplygo/network/` | Real TCP/TLS socket integration tests | Implemented |
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

Supplier/seller ranking, routing, UDP messaging and optional
OS simulations remain separate tasks. Database migration and production-specific
workflows can follow when their requirements are defined. See
[AUTHENTICATION.md](AUTHENTICATION.md), [INVENTORY.md](INVENTORY.md) and
[ORDERS.md](ORDERS.md) and [BUSINESS_PROFILES.md](BUSINESS_PROFILES.md) for the implemented workflows.

## Inventory increment

- `model/Product.java`, `model/FruitBatch.java`: validated stock records.
- `storage/InventoryRepository.java`: locked JSON store and atomic replacement.
- `service/InventoryService.java`: authorization, synchronized changes, FIFO and paging.
- `ui/ClientWindow.java`: Inventory tab for products, batches and manager actions.
- `docs/INVENTORY.md`: usage, stock units, concurrency and persistence limits.

## Order increment

- `model/SupplyOrder.java`, `model/OrderStatus.java`: order records and lifecycle.
- `service/OrderService.java`: seller ownership, manager actions and duplicate submissions.
- `service/StockOperations.java`: shared reservation totals and FIFO consumption.
- `storage/InventoryRepository.java`: schema version 2 stores orders and stock in one atomic snapshot; loads legacy inventory.
- `protocol/OrderData.java`: explicit order response schema.
- `ui/OrderPanel.java`: seller/manager screens and account/server-bound retry drafts.
- `docs/ORDERS.md`: permissions, demonstration, upgrade and recovery instructions.

## Business profile module

BusinessProfile is the validated data model; BusinessProfileRepository owns atomic JSON persistence and process locking; BusinessProfileService enforces ownership and directory permissions. BusinessProfileData handles wire records, and BusinessProfilePanel provides the Swing editor and manager directory. See [BUSINESS_PROFILES.md](BUSINESS_PROFILES.md).
