# Getting Started with SupplyGo

This document covers the **initial Maven starter**, not the full networked system.
The TCP server and client applications will be implemented in later tasks.

## Requirements

- Oracle JDK 25 (project compiles with Java 25)
- Apache Maven 3.10.0 (the team’s verified version)
- Visual Studio Code with the *Extension Pack for Java*
- Git

## Open the project

1. Clone the repository: `git clone https://github.com/uvinN20/SupplyGo.git`
2. Open the `SupplyGo` folder in VS Code.
3. Confirm `java -version` and `mvn -version` both report Java 25 in the terminal.

## Compile and test

```sh
mvn clean test
```

## Run the starter application

```sh
mvn package
java -cp target/classes com.supplygo.Main
```

Expected output:

```text
SupplyGo - Java project initialized.
```

## Next task

Build a basic TCP server and client using `ServerSocket` and `Socket`.
The `server` and `client` packages are placeholders until then.
