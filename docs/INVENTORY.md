# Inventory management

Requires Java 25, Maven 3.9+, and the TLS/account setup in
[AUTHENTICATION.md](AUTHENTICATION.md). Use `feature/inventory-management` while
reviewing the inventory-only feature, then `main` after merge. Order-enabled
builds are described in [ORDERS.md](ORDERS.md). Run `mvn clean verify` and use
the same server/client launch commands as authentication.

## Desktop workflow

1. Sign in as an administrator or production manager. The operator can create a
   production manager with `java -jar target/supplygo-0.1.0-SNAPSHOT.jar create-user production_manager PRODUCTION_MANAGER`
   while the server is stopped.
2. Open the **Inventory** tab. Choose **Products**, then **Create product**.
   For example: ID `APPLE`, name `Apple`. IDs are case-sensitive, 1–24 uppercase
   letters, digits, underscores or hyphens. Names allow 1–60 printable characters.
3. **Receive batch** for `APPLE` with 5 units, then another batch with 10 units.
4. **Withdraw FIFO** for `APPLE` with 8 units. The first batch is consumed and
   the second retains 7 units. A request exceeding available stock changes nothing.
5. Click **Refresh** to see current totals or choose **Fruit batches** to see the
   remaining physical batches, their server receipt times (UTC) and receiving account.
   Products show available, on-hand and reserved quantities when orders are enabled.
   **Next page** shows the next ten rows; Refresh returns to the first page.
6. Sign in as a supplier or seller: inventory is readable, but mutation controls
   are disabled and the server rejects attempted stock changes from these roles.

All six authenticated roles can read stock. Only `ADMIN` and
`PRODUCTION_MANAGER` can create products, receive batches or withdraw stock.
Permissions come from the server session. Requests cannot supply an acting
username, role, receipt timestamp or batch ID.

## Stock and persistence rules

- Quantities are **whole fruit units**, from 1 to 1,000,000 per receipt or
  withdrawal. These are not kilograms, prices or monetary values.
- FIFO means server receipt order, using monotonic batch IDs; partial withdrawals
  retain the original ID and receipt metadata. Depleted batches are removed.
- Products are append-only in this feature. Limits: 1,000 products and 10,000
  currently nonempty batches; total available quantities use a Java `long`.
- Active orders reserve units. Direct withdrawals cannot consume the units needed
  for those reservations. Available stock is physical stock minus active reservations.
- The server saves `inventory.json` beside `users.json` under `data/`, or the
  directory selected by `-Dsupplygo.data.dir=...`. Generated data is not committed.
- One shared repository monitor protects inventory and order validation, FIFO calculation, file
  replacement and in-memory publication. Concurrent clients cannot oversell.
  The tradeoff is that disk writes serialize stock operations.
- A separate file lock rejects another server using the same inventory store.
  Writes force a temporary file and atomically replace the saved file before
  publishing memory. A failed write leaves in-memory stock unchanged. A filesystem
  without atomic replacement support rejects writes; there is no unsafe fallback.
- Invalid saved data stops startup; it is not silently erased. Back up the data
  directory with the server stopped. Keep the directory accessible only to the
  server operator; Windows uses its normal inherited filesystem permissions.

## Scope and operational limits

Order reservations and fulfilment are described in [ORDERS.md](ORDERS.md).
Expiry dates, weights, prices, supplier ownership, inventory adjustments, historical
movements and automatic replenishment are future features. FIFO here has no expiry
policy. Direct withdrawals are trusted manager actions; sellers submit orders
through the Orders tab.

Each page is current when read, not a snapshot shared across requests. Concurrent
changes can move batch rows between pages; Refresh starts a fresh traversal.
There is no automatic polling. Existing sessions expire after 15 minutes and
inactive connections after 60 seconds; reconnect/sign in when necessary.

Direct inventory requests are not automatically retried. Order submissions have
separate duplicate protection described in ORDERS.md. If a connection fails after a mutation
was sent, it may already have been saved even without a reply. Reconnect and
inspect stock before repeating it. Exactly-once retries and a durable audit log
are not part of this increment. Atomic replacement is not a guarantee against
all power-loss scenarios.

## Verification

`mvn clean verify` covers concurrent withdrawals (exactly ten out of twenty
10-unit requests succeed against 100 units), partial FIFO allocation across
products, persistence/reopening, authorization, invalid quantities/products,
failed replacement without memory publication, store locks, corrupt files,
bounded pages, and real TLS requests from manager and seller clients.
