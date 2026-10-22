# SupplyGo TCP protocol, version 1

PING and QUIT remain supported. TLS connections also support account operations
described below. Business mutations, events, and UDP chat follow in later tasks.

## Framing

- TCP is a byte stream; a socket read is not a message boundary.
- Each message is exactly one UTF-8 JSON object terminated by LF (`\n`).
  CRLF is also accepted.
- At most **8192 bytes before LF**, including an optional CR, are allowed.
  The receiver enforces this limit before JSON parsing and rejects invalid UTF-8.
- A frame must contain exactly one JSON object, use strict JSON syntax, and
  stay within 16 nesting levels. Gson 2.13.2 performs JSON parsing with strict mode.
- EOF between frames is a normal disconnect. EOF inside a frame is an error.
- Requests on each connection are handled sequentially. Different clients run
  on separate handler threads, with a maximum of 32 handlers and no application
  waiting queue. An additional connection is closed when no handler is available.

## Requests

Every request has exactly these fields. Types are checked without string/number
coercion. `version` must be the JSON integer `1`; request IDs match
`[A-Za-z0-9._:-]{1,64}`. `payload` must be an empty object for both operations.
Unknown top-level fields and duplicate JSON object keys at every nesting level
are rejected.

```json
{"version":1,"requestId":"request-001","type":"PING","payload":{}}
```

```json
{"version":1,"requestId":"request-002","type":"QUIT","payload":{}}
```

## Responses

PING returns PONG and keeps the connection open:

```json
{"version":1,"requestId":"request-001","status":"OK","data":{"message":"PONG"}}
```

QUIT returns BYE and then closes the connection:

```json
{"version":1,"requestId":"request-002","status":"OK","data":{"message":"BYE"}}
```

Errors use an `error` object instead of `data`. A validated request ID is echoed
when available; otherwise `requestId` is explicitly `null`.

```json
{"version":1,"requestId":null,"status":"ERROR","error":{"code":"INVALID_MESSAGE","message":"Expected one valid JSON object with at most 16 nesting levels"}}
```

| Error code | Condition | Connection |
| --- | --- | --- |
| `INVALID_MESSAGE` | Invalid JSON, field types, shape, payload or request ID | Kept open after complete frame |
| `UNSUPPORTED_VERSION` | An integer version other than 1 | Kept open |
| `UNKNOWN_TYPE` | Type other than PING or QUIT | Kept open |
| `FRAME_TOO_LARGE` | More than 8192 bytes before LF | Closed |
| `INVALID_UTF8` | Invalid UTF-8 bytes | Closed |
| `INCOMPLETE_FRAME` | EOF before terminating LF | Closed |
| `IDLE_TIMEOUT` | No incoming bytes for 60 seconds | Closed |

The server attempts to send a framing/timeout error before closing. Delivery is
not guaranteed if the peer has already disconnected. Abrupt disconnects release
the handler and socket. Ctrl+C closes the listener and all active connections.

## Client lifecycle

`SupplyGoTcpClient` has one socket and serializes its exchanges. A new instance
is required after closing. Requests use UUIDs; replies must have protocol version
1, matching request IDs, the expected OK status, and the expected PONG/BYE message.
Errors or unexpected responses close the client. The Swing Connect action sends
a PING before it displays Connected. Each request has a 5-second read timeout;
TCP establishment uses a 3-second timeout (DNS resolution is OS-managed).

Closing the window immediately closes its socket and interrupts blocked reads.
Connection state is checked when performing an operation; there is no continuous
heartbeat. Reconnect manually after idle closure, server restart, or a timeout.
There is no automatic request retry, and the server stores no request-ID history.

## Future boundaries

TCP will carry accounts, inventory, orders, routes, and business events. The
server must enforce validation, permissions, synchronization, and persistence.
Before adding business mutations, define idempotency/duplicate-request handling
and an event envelope. The plain PING/QUIT mode does not support accounts;
account operations require TLS. UDP will be a separate messenger and will never
change stock, orders, or financial records.

## TLS account operations

Account messages use the same version 1 envelope and 8192-byte framing. Only
`LOGIN` and `REGISTER` have nonempty payloads; all other operations use `{}`.
TLS is required for every operation in this table. The server authenticates its
certificate; clients verify both trust and hostname. Session identity stays on
the server for that socket. Requests never carry a session role or session token.

| Type | Payload | Successful `data` | Authorization |
| --- | --- | --- | --- |
| `REGISTER` | `username`, `password`, `role` strings | `message: REGISTERED`, `username`, `role` | SUPPLIER/SELLER self-registration only |
| `LOGIN` | `username`, `password` strings | `message: SIGNED_IN`, `username`, `role` | Valid saved credentials |
| `GET_PROFILE` | Empty | `message: PROFILE`, `username`, `role` | Signed-in user |
| `LOGOUT` | Empty | `message: SIGNED_OUT` | Idempotent session removal |
| `ADMIN_STATS` | Empty | `message: ADMIN_STATS`, integer `userCount` | ADMIN only |

Example LOGIN (send only over TLS):

```json
{"version":1,"requestId":"auth-001","type":"LOGIN","payload":{"username":"supplier_one","password":"YOUR_OWN_PASSWORD"}}
```

Additional errors are `TLS_REQUIRED`, `AUTH_UNAVAILABLE`, `INVALID_ACCOUNT`,
`USERNAME_TAKEN`, `INVALID_CREDENTIALS`, `RATE_LIMITED`, `SERVER_BUSY`,
`STORAGE_ERROR`, `AUTH_REQUIRED`, `SESSION_EXPIRED`, and `FORBIDDEN`. These use
correlated ERROR replies and keep the connection usable. A failed LOGIN clears
any previous session. The server never includes credentials or hashes in replies.

A session expires 15 minutes after login; the existing 60-second socket inactivity
limit can close it earlier. The bounded login throttle is shared by connections,
with a one-minute cooldown after five failures for a username. There are at most
four simultaneous password-hashing operations and 32 connection handlers.
Registration is unique by normalized username and does not automatically log in.
There is no automatic retry; a registration whose reply was lost may already have
succeeded. Attempt normal sign-in rather than creating a duplicate.

See [AUTHENTICATION.md](AUTHENTICATION.md) for credentials, stores and TLS setup.

## Inventory requests (TLS and authenticated session required)

| Type | Exact payload | Successful data |
| --- | --- | --- |
| `LIST_PRODUCTS` | `{"offset":0}` | `message: "PRODUCTS"`, `products`, `nextOffset` |
| `LIST_BATCHES` | `{"offset":0}` | `message: "BATCHES"`, `batches`, `nextOffset` |
| `CREATE_PRODUCT` | `{"productId":"APPLE","name":"Apple"}` | `message: "INVENTORY_UPDATED"` |
| `RECEIVE_BATCH` | `{"productId":"APPLE","quantity":10}` | `message: "INVENTORY_UPDATED"` |
| `WITHDRAW_STOCK` | `{"productId":"APPLE","quantity":5}` | `message: "INVENTORY_UPDATED"` |

Offsets and quantities are JSON integers, not strings or decimals. Offsets range
from 0 to 10000; quantities from 1 to 1000000 whole fruit units. Each page contains
at most ten rows; `nextOffset` is -1 at the end. Product rows contain `id`, `name`,
`quantity` (total units). Batch rows contain `id` (integer), `productId`, `quantity`,
`receivedAt` (ISO-8601 UTC instant), `receivedBy` (server session username).
Rows and payloads reject unknown fields. Existing 8192-byte frame limits apply.

Only ADMIN and PRODUCTION_MANAGER may mutate inventory. All authenticated roles
may list it. Additional error codes: `INVENTORY_UNAVAILABLE`, `INVALID_INVENTORY`,
`PRODUCT_EXISTS`, `PRODUCT_NOT_FOUND`, `INSUFFICIENT_STOCK`, `INVENTORY_LIMIT`,
`STORAGE_ERROR`; existing TLS/session/permission errors still apply. Application
errors preserve the connection. See [INVENTORY.md](INVENTORY.md) for persistence,
FIFO semantics, paging consistency and the warning about retrying an
unacknowledged mutation.
