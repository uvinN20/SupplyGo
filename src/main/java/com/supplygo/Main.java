package com.supplygo;

import com.supplygo.client.ClientMain;
import com.supplygo.server.ServerMain;

/** Optional command dispatcher; each application also has its own entry point. */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        if (args.length == 0 || (args.length == 1 && "--help".equals(args[0]))) {
            System.out.println("Usage: java -jar target/supplygo-0.1.0-SNAPSHOT.jar <server|client>");
            return;
        }
        if (args.length != 1) {
            System.err.println("Expected one mode: server or client. Use --help for usage.");
            System.exit(2);
            return;
        }
        switch (args[0]) {
            case "server" -> ServerMain.main(new String[0]);
            case "client" -> ClientMain.main(new String[0]);
            default -> {
                System.err.println("Unknown mode: " + args[0] + ". Expected server or client.");
                System.exit(2);
            }
        }
    }
}
