# Network protocol outline

**Draft only. No protocol parser, TCP listener, or UDP messenger is implemented
in the scaffold milestone.** Confirm and implement this outline in the TCP task.

## Transport boundaries

- TCP carries account operations, inventory, orders, routes, and business events.
- UDP is reserved for the separate messenger demonstration. It must not change
  stock, order state, or financial records because delivery is not guaranteed.
- The server owns validation, permissions, shared state, and persistence.

## Proposed first TCP slice

Start with `PING` / `PONG` and clean disconnects before adding business operations.
Use a documented UTF-8, newline-delimited JSON envelope, with a protocol version
and request ID. The next task must select its JSON dependency and enforce a
message-size limit before allocating an unbounded buffer.

Example request (proposed):

```json
{"version":1,"requestId":"request-001","type":"PING","payload":{}}
```

Example response (proposed):

```json
{"version":1,"requestId":"request-001","status":"OK","data":{"message":"PONG"}}
```

Define structured error codes, response correlation, timeouts, and handling for
malformed or oversized messages in that task. Later unsolicited events need an
explicit event envelope so clients can distinguish them from replies.

Before implementing mutable operations, define authentication/session handling
and duplicate-request behavior so reconnecting or retrying cannot create an
order twice. Do not send real credentials over an unencrypted connection; define
TLS before supporting real users outside a controlled demonstration.
