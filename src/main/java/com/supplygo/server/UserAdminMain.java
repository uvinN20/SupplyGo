package com.supplygo.server;

import java.io.Console;
import java.nio.file.Path;
import java.util.Arrays;
import com.supplygo.model.Role;
import com.supplygo.service.AuthService;
import com.supplygo.storage.AccountRepository;

/** Trusted local operator command. Stop the server before opening its account store. */
public final class UserAdminMain {
    private UserAdminMain() { }
    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: create-user <username> <SUPPLIER|SELLER|SUPPLIER_MANAGER|SELLER_MANAGER|PRODUCTION_MANAGER|ADMIN>");
            System.exit(2);
            return;
        }
        Console console = System.console();
        if (console == null) {
            System.err.println("Run create-user in an interactive terminal with password input support");
            System.exit(1);
            return;
        }
        char[] password = console.readPassword("New password (12-128 characters): ");
        char[] confirmation = console.readPassword("Confirm password: ");
        try {
            if (password == null || confirmation == null || !Arrays.equals(password, confirmation)) {
                throw new IllegalArgumentException("Passwords did not match");
            }
            Role role = Role.valueOf(args[1]);
            try (var repository = new AccountRepository(Path.of(System.getProperty("supplygo.data.dir", "data")))) {
                var profile = new AuthService(repository).provision(args[0], password, role);
                System.out.println("Created " + profile.username() + " with role " + profile.role());
            }
        } catch (Exception exception) {
            System.err.println("Could not create user: " + exception.getMessage());
            System.exit(1);
        } finally {
            if (password != null) Arrays.fill(password, '\0');
            if (confirmation != null) Arrays.fill(confirmation, '\0');
        }
    }
}
