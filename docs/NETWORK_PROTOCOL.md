# SupplyGo TCP protocol, version 1

The first implemented operations are PING and QUIT. Authentication, mutations,
server-initiated events, and UDP chat are not part of this version's feature set.

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
Unknown top-level fields are rejected. Duplicate JSON object keys use Gson's
last-value behavior; send each field exactly once.

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
Before adding mutable operations, define sessions, idempotency/duplicate-request
handling, and an event envelope. This demonstration uses unencrypted TCP; add TLS
before sending real credentials. UDP will be a separate messenger and will never
change stock, orders, or financial records.
