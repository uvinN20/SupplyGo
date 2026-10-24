# UDP messenger

SupplyGo has one shared chat room for all six signed-in roles. The Swing client uses real UDP datagrams for chat; accounts, inventory, orders and profile changes remain on TCP/TLS.

## Run and demonstrate

1. Follow [AUTHENTICATION.md](AUTHENTICATION.md) to start the TLS server and configure each client's trusted certificate. The same server process starts the UDP listener automatically, on port 7071 by default.
2. Open two desktop clients and sign in with two accounts.
3. Open **Chat**, choose **Join room**, and send a message. Each joined participant, including the sender, sees the server-assigned username, role and UTC time.
4. Choose **Leave** to exit the room while remaining signed in. **Join room** creates fresh chat access. Signing out or disconnecting also closes chat and clears the local history.
5. Close and reopen a client to demonstrate reconnection. No old messages are restored.

For a LAN demonstration set `-Dsupplygo.server.bind=0.0.0.0` on the server and use the server's LAN address in the client. Its TLS certificate must contain that address or DNS name. Allow inbound TCP 7070 and UDP 7071 through the server firewall for your test network. The server advertises its UDP port over TLS, so clients do not need a separate port field. Change it on the server with `-Dsupplygo.udp.port=7072` if needed. Port forwarding must preserve that advertised UDP port; the TCP and UDP client traffic must arrive from the same IP address.

A plain TCP demo server does not start UDP chat. If UDP cannot bind, TLS server startup fails with a clear error rather than offering a non-working chat grant.

## What the status means

- **Joined shared room:** the UDP server acknowledged this socket's join.
- **Accepted by server:** the server accepted the message and attempted to send it to joined users. This does not prove any recipient received or read it.
- **No UDP confirmation:** the datagram or its acknowledgement may have been lost. Check the room before resending; there is no automatic retry.
- **UDP room connection lost:** heartbeat failed. Join again to obtain fresh access.

UDP can lose, duplicate or reorder packets. Duplicate and older sequence numbers are discarded. A later packet may therefore cause an earlier packet or acknowledgement to be discarded. This educational messenger intentionally does not implement reliable delivery, offline messages, message persistence, private conversations, attachments or read receipts.

## Security and limits

Chat messages are **not encrypted**. HMAC signatures verify integrity and authenticated senders; they do not provide confidentiality. Use the room for non-sensitive coursework demonstrations. Never send passwords or private business information through chat.

The server issues a random 128-bit ticket and a separate 256-bit HMAC key only over the current authenticated TLS connection. Neither login passwords nor the HMAC key are sent in UDP packets. The UDP server determines sender name and role from the grant, and rejects supplied sender fields. Client datagrams are restricted to the TCP peer's IP address and pinned to the UDP endpoint after the first valid JOIN. Server replies use a direction-specific signature to prevent reflecting a server packet as a client request. The client uses a connected UDP socket to restrict receive traffic to the selected server endpoint.

- One grant per TCP connection; requesting another revokes the previous grant.
- At most 32 grants, including grants waiting to join.
- Grant expiry never extends the account session's absolute 15-minute deadline.
- TCP logout, processed login attempts, QUIT and connection closure revoke the grant. CLOSE_CHAT revokes it while retaining the account session.
- UDP grants expire after 45 seconds without a valid client packet.
- The desktop checks its TCP session and sends a UDP heartbeat every 10 seconds while joined and idle. This keeps the existing TCP idle timeout from closing an otherwise active room. It does not extend the absolute login session.
- Five accepted SEND messages per participant per wall-clock second.
- Single-line messages, at most 240 UTF-8 bytes; controls and unpaired UTF-16 surrogates are rejected. Unicode is supported within the byte limit.
- Datagrams are at most 1200 bytes. Invalid, oversized, unsigned and replayed packets are silently dropped.
- No server-side message history. The desktop keeps at most 200 display lines and a bounded queue of 200 pending display messages.

All participants in this shared room can read each other's messages. Membership and keys are memory-only and disappear when the server stops. Do not log grants; credential and response diagnostic strings are redacted.

## Protocol

TCP/TLS requests use the existing version-1 envelope:

| Type | Payload | Success |
| --- | --- | --- |
| OPEN_CHAT | {} | CHAT_GRANT with ticket, key, port and expiresAt |
| CLOSE_CHAT | {} | CHAT_CLOSED with no attributes |

Both require an authenticated session. OPEN_CHAT replaces prior access for that connection. Chat grants are independent of inventory/order data and cannot authorize business mutations.

Each UDP datagram is one UTF-8 JSON object, with no newline framing:

```text
{"version":1,"ticket":"<32 lowercase hex characters>","sequence":1,"payload":"<base64url JSON>","mac":"<base64url HMAC-SHA256>"}
```

Base64url values omit padding. The MAC input is the ASCII string `C|1|ticket|sequence|payload` for client packets or `S|1|ticket|sequence|payload` for server packets. The key is the decoded grant key. Client and server maintain independent positive signed-64-bit sequence counters per grant. All object schemas reject unknown and duplicate keys.

Decoded payloads:

| Direction | Type | Exact fields |
| --- | --- | --- |
| Client | JOIN, HEARTBEAT, LEAVE | type |
| Client | SEND | type, text |
| Server | ACK | type, replyTo, status (OK or ERROR), message |
| Server | MESSAGE | type, sender, role, text, time (UTC ISO-8601) |

ACK replyTo refers to the client sequence number. JOIN is required before SEND, HEARTBEAT or LEAVE. The Java client waits at most three seconds for an acknowledgement. Server broadcasts include the sender; the desktop shows received broadcasts instead of claiming local sends are delivered.

## Testing and module mapping

Run `mvn clean verify` on Java 25. Tests use actual loopback UDP sockets and real TLS authorization, plus injected time for expiry and capacity checks. Coverage includes multi-client messages, Unicode, sender identity, joins, leave/rejoin, heartbeat, malformed packets, signature tampering, direction reflection, replay, endpoint pinning, text limits, rate limits, timeouts and grant revocation.

This feature demonstrates connectionless datagrams, client/server communication, best-effort delivery, socket timeouts, receiver threads, synchronized shared state, bounded queues, and Swing's event-dispatch thread. Compare its delivery behavior with the project's ordered TCP request/response operations during your demonstration.
