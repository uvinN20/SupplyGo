# Accounts and authentication

This feature adds supplier/seller registration, sign-in, profiles, sign-out and
server-side roles. Accounts persist on the server. For inventory operations, see
[INVENTORY.md](INVENTORY.md). Order management, password reset and account
administration screens follow in later tasks.

## Build and choose the branch

During review:

```bat
git fetch origin
git switch feature/authentication
git pull --ff-only origin feature/authentication
mvn clean verify
```

After merge, use `main`. JDK 25 and Maven 3.9+ are required. The runnable JAR is
`target/supplygo-0.1.0-SNAPSHOT.jar`.

## Prepare TLS for a local demonstration

Run these commands from the project directory. `keytool` is included in your JDK.
It prompts for store passwords; choose your own, and remember which password
belongs to the server keystore and which belongs to the client truststore.

```bat
mkdir certs
keytool -genkeypair -alias supplygo-server -keyalg RSA -keysize 3072 -validity 365 -dname "CN=localhost" -ext "SAN=dns:localhost,ip:127.0.0.1" -storetype PKCS12 -keystore certs/server.p12
keytool -exportcert -alias supplygo-server -keystore certs/server.p12 -rfc -file certs/server.crt
keytool -importcert -alias supplygo-server -file certs/server.crt -storetype PKCS12 -keystore certs/trust.p12
```

Confirm the displayed certificate fingerprint when importing. Only the public
`server.crt` is copied to teammates; the server private-key file `server.p12`
stays on the server laptop. `certs/`, private-key files and generated account data
are excluded from Git.

For two laptops, include the server laptop's actual IPv4 address in the SAN when
generating the certificate, for example
`-ext "SAN=dns:localhost,ip:127.0.0.1,ip:192.168.1.10"`. Clients connect to that
address and import the public certificate into their own truststore. A changed
address requires an appropriate new certificate and trust import. Hostname and
certificate trust validation are enforced; there is no trust-all setting.

## Create the administrator and manager accounts

Stop the server before running the local operator command. It prompts twice for
the account password without echoing it. No account passwords are supplied as
command-line arguments or included in the repository.

```bat
java -jar target/supplygo-0.1.0-SNAPSHOT.jar create-user admin ADMIN
java -jar target/supplygo-0.1.0-SNAPSHOT.jar create-user supplier_manager SUPPLIER_MANAGER
java -jar target/supplygo-0.1.0-SNAPSHOT.jar create-user seller_manager SELLER_MANAGER
java -jar target/supplygo-0.1.0-SNAPSHOT.jar create-user production_manager PRODUCTION_MANAGER
```

Run this in an interactive Command Prompt/PowerShell terminal. If Java reports
that no console is available, open a normal terminal rather than redirecting
input or using an IDE output panel. The operator command can provision any of the
six roles. It is a trusted local administration action, not a network endpoint.
There are no predefined users or default account passwords.

## Start the TLS server and client

Replace the placeholders with the passwords chosen during certificate setup.
The JSSE store-password properties unlock the certificate stores; they are
separate from SupplyGo account passwords. Keep these values local.

Server terminal:

```bat
java -Dsupplygo.tls=true -Djavax.net.ssl.keyStore=certs/server.p12 -Djavax.net.ssl.keyStorePassword=YOUR_SERVER_STORE_PASSWORD -jar target/supplygo-0.1.0-SNAPSHOT.jar server
```

Client terminal:

```bat
java -Dsupplygo.tls=true -Djavax.net.ssl.trustStore=certs/trust.p12 -Djavax.net.ssl.trustStorePassword=YOUR_CLIENT_STORE_PASSWORD -jar target/supplygo-0.1.0-SNAPSHOT.jar client
```

The client starts with **Use TLS for sign-in** selected. Click **Connect**, enter
an account username/password, and choose **Sign In**. **My Profile** retrieves the
identity from the server. **Sign Out** clears the connection's session while
keeping its socket open. **Account Count** is available to administrators and is
also protected by an administrator check on the server.

To register, enter a new username/password, choose **SUPPLIER** or **SELLER**, and
click **Create Account**. Registration does not sign you in automatically: enter
your password again and click **Sign In**. Managers and administrators cannot
self-register over the network.

The original plain TCP demonstration remains available with the existing server
and client commands. Leave **Use TLS** unchecked for that demonstration. Account
controls are disabled on a plain connection and the server rejects its account
operations. A TLS client must connect to a TLS server on the selected port.

For LAN access, add `-Dsupplygo.server.bind=0.0.0.0` to the server command, select
its actual address in the client, and allow the private-network TCP port through
Windows Firewall. See [GETTING_STARTED.md](GETTING_STARTED.md).

## Account and session rules

- Usernames are normalized to lowercase; surrounding whitespace is removed.
  They have 3–32 characters: letters, digits and underscores, starting with a letter.
- New passwords have 12–128 Java characters. Spaces and Unicode are allowed;
  passwords are not trimmed. Use a unique passphrase.
- Password records use PBKDF2-HMAC-SHA256, 600,000 iterations, independent 16-byte
  random salts, and 32-byte hashes. No plaintext passwords are saved or logged.
- Sessions belong to individual sockets and expire 15 minutes after sign-in.
  Signing out, disconnecting or reconnecting removes identity. A failed new login
  also clears any previous identity on that same connection.
- Five failed attempts for a username trigger a one-minute cooldown across
  connections. The bounded in-memory throttle is reset by server restart; it is
  intended for this single-server educational deployment.
- At most four password-hashing operations run simultaneously. If busy, the
  server returns `SERVER_BUSY`; the client does not retry automatically.
- Connections still close after 60 seconds without incoming bytes. There is no
  background heartbeat; reconnect and sign in again after an idle closure.

## Storage and backups

The server owns `data/users.json` and holds `data/users.lock`. Updates are
synchronized, written to a temporary file and atomically replace the saved file.
A failed write is not published in memory. The repository allows one process and
at most 10,000 accounts. Unsupported atomic replacement or corrupt data causes a
clear error, rather than silently discarding accounts.

Use `-Dsupplygo.data.dir=PATH` consistently for the server and operator command to
change the data folder. Stop the server before backing up/restoring the account
file. Protect the folder with your operating-system account permissions; owner-only
permissions are applied where POSIX attributes are supported. Clients never open
this file. A later persistence task may migrate it to a database.

## Troubleshooting

- **TLS handshake/certificate error:** Check the truststore password, import the
  right public certificate, and connect using an address included in its SAN.
- **Wrong version/unrecognized SSL message:** Check that both ends selected TLS.
- **Account store already in use:** Stop the running server before `create-user`.
- **Invalid username or password:** Check the username and your own password;
  there is no default account. After five failures, wait one minute.
- **Session expired:** Sign in again. **Server closed connection:** reconnect first.

## Validation and references

The Maven suite includes account persistence, concurrent duplicate registration,
failed disk writes, session expiry, role enforcement, real TLS registration/login,
connection isolation, privilege escalation, and certificate rejection. Tests
generate a temporary certificate with JDK `keytool`; no production keys are stored.

- [OWASP password storage guidance](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)
- [JDK 25 JSSE reference](https://docs.oracle.com/en/java/javase/25/security/java-secure-socket-extension-jsse-reference-guide.html)
