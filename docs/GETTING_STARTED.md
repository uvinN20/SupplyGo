# SupplyGo: running the Java 25 foundation

This milestone provides the Maven project, validated endpoint configuration, a
backend launcher, and a basic Swing frontend. It does **not** open TCP/UDP sockets,
connect to a database, authenticate users, or process orders yet. The backend
launcher prints the configured settings and exits successfully. The client opens
an offline desktop window. Later milestones add those features.

## Requirements

- JDK 25, including `java` and `javac`.
- Apache Maven 3.9 or newer; the team's Maven 3.10 installation is suitable.
- Git and VS Code with the Extension Pack for Java.
- A graphical desktop to open the Swing client. The server launcher and tests
  can run without a display.

From Windows Command Prompt, check:

```bat
java -version
javac -version
mvn -version
```

Maven must report that it is using JDK 25. If it reports an older JDK, update
`JAVA_HOME` to the JDK 25 installation directory and reopen the terminal.

## Get this branch

For an existing clone, commit or stash any local work before switching branches:

```bat
cd C:\Users\uvinn\SupplyGo
git fetch origin
git switch feature/project-scaffold
git pull --ff-only origin feature/project-scaffold
```

If the branch is not present locally, use
`git switch --track origin/feature/project-scaffold` after fetching.

For a new clone:

```bat
git clone --branch feature/project-scaffold https://github.com/uvinN20/SupplyGo.git
cd SupplyGo
```

## Build and test

Run from the directory containing `pom.xml`:

```bat
mvn clean verify
```

The first build downloads Maven plugins and JUnit and therefore needs internet
access. Successful builds produce `target/supplygo-0.1.0-SNAPSHOT.jar`.
Tests exercise default settings, overrides, and invalid configuration.

## Launch the two applications

Backend launcher:

```bat
java -jar target/supplygo-0.1.0-SNAPSHOT.jar server
```

Expected output includes `SupplyGo | Backend scaffold` and explicitly states that
no sockets are open. It then exits; this is expected for this milestone.

Frontend launcher:

```bat
java -jar target/supplygo-0.1.0-SNAPSHOT.jar client
```

A window titled **SupplyGo | Desktop Client** opens and displays **Project
foundation | Offline**, the configured address, and the TCP/UDP ports. Closing
the window terminates the client. There is no login or Connect button yet.

You can also run the separate Java entry points after building:

```bat
java -cp target/classes com.supplygo.server.ServerMain
java -cp target/classes com.supplygo.client.ClientMain
```

For command usage:

```bat
java -jar target/supplygo-0.1.0-SNAPSHOT.jar --help
```

These build and launch commands also work in a Linux terminal with JDK 25 and
Maven installed. A headless Linux environment cannot display the Swing client;
the client reports this and exits with code 1.

## Configuration

Defaults are in `src/main/resources/supplygo.properties`:

| Property | Default | Purpose |
| --- | --- | --- |
| `supplygo.server.host` | `127.0.0.1` | Server address the client will connect to |
| `supplygo.tcp.port` | `7070` | Planned TCP application port |
| `supplygo.udp.port` | `7071` | Planned UDP messenger port |

Override settings without editing source files by placing Java `-D` options
**before** `-jar`:

```bat
java -Dsupplygo.server.host=192.168.1.10 -Dsupplygo.tcp.port=8080 -Dsupplygo.udp.port=8081 -jar target/supplygo-0.1.0-SNAPSHOT.jar client
```

Overrides take precedence over packaged defaults. Blank addresses, non-integer
ports, and ports outside 1-65535 are rejected with a readable error. DNS lookup
and actual connection validation will be added with TCP networking.

## Development rules

- Keep application code in Java; the old C++ DSA project is a design reference.
- Work on a task branch and merge reviewed changes through a pull request.
- Use commits such as `feat(network): add TCP request handling`.
- Swing components are created on the event dispatch thread. Future network
  operations must run in background tasks so they cannot freeze the UI.
- `data/` is reserved for server-side runtime data. Generated files are ignored;
  no database is created by this milestone. Never commit passwords or user data.

See [PROJECT_STRUCTURE.md](PROJECT_STRUCTURE.md) for package responsibilities and
[NETWORK_PROTOCOL.md](NETWORK_PROTOCOL.md) for the next milestone's draft.
