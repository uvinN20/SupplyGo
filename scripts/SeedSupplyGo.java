import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.supplygo.model.FruitBatch;
import com.supplygo.model.Product;
import com.supplygo.model.Role;
import com.supplygo.service.AuthService;
import com.supplygo.service.BusinessProfileService;
import com.supplygo.storage.AccountRepository;
import com.supplygo.storage.BusinessProfileRepository;
import com.supplygo.storage.InventoryRepository;

class SeedSupplyGo {
    private static final List<Product> PRODUCTS = List.of(
            new Product("FR-001", "Fresh mango"), new Product("FR-002", "Papaya"),
            new Product("FR-003", "Grapes"), new Product("FR-004", "Banana"),
            new Product("FR-005", "Orange"));
    private static final List<String> FRUITS = List.of("Mango", "Papaya", "Grapes", "Banana", "Orange");
    private static final List<String> TOWNS = List.of("Matara", "Hakmana", "Tangalle", "Galle", "Weligama");

    public static void main(String[] args) throws Exception {
        if (args.length > 1) throw new IllegalArgumentException("Expected at most one data directory argument.");
        var console = System.console();
        if (console == null) throw new IllegalStateException("Run this command in an interactive terminal.");
        char[] password = console.readPassword("Shared password for the 10 local accounts (12-128 characters): ");
        if (password == null) throw new IllegalArgumentException("No password was entered.");
        try {
            Path data = Path.of(args.length == 0 ? "data" : args[0]);
            seed(data, password);
            System.out.println("Ready: sg_seller_1 to sg_seller_5 and sg_supplier_1 to sg_supplier_5.");
            System.out.println("Start the TLS server with data directory " + data.toAbsolutePath().normalize());
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    static void seed(Path data, char[] password) throws Exception {
        if (password.length < 12 || password.length > 128) {
            throw new IllegalArgumentException("Use a password between 12 and 128 characters.");
        }
        try (var accounts = new AccountRepository(data);
             var inventory = new InventoryRepository(data);
             var profiles = new BusinessProfileRepository(data)) {
            var auth = new AuthService(accounts);
            var business = new BusinessProfileService(profiles);
            var state = inventory.snapshot();
            long matchingProducts = PRODUCTS.stream().filter(state.products()::contains).count();
            boolean idConflict = state.products().stream().anyMatch(existing ->
                    PRODUCTS.stream().anyMatch(product -> product.id().equals(existing.id()) && !product.equals(existing)));
            if (idConflict || (matchingProducts != 0 && matchingProducts != PRODUCTS.size())) {
                throw new IllegalStateException("Seed product IDs conflict with the inventory. Use an empty data directory.");
            }

            for (Role role : List.of(Role.SELLER, Role.SUPPLIER)) {
                for (int index = 0; index < FRUITS.size(); index++) {
                    String username = username(role, index);
                    if (accounts.find(username) != null && auth.login(username, password).role() != role) {
                        throw new IllegalStateException("Unexpected role for " + username);
                    }
                }
            }

            for (Role role : List.of(Role.SELLER, Role.SUPPLIER)) {
                for (int index = 0; index < FRUITS.size(); index++) {
                    String username = username(role, index);
                    var existing = accounts.find(username);
                    var user = existing == null ? auth.register(username, password, role) : existing.profile();
                    if (!profiles.snapshot().containsKey(username)) {
                        int number = index + 1;
                        String weight = FRUITS.get(index) + " 25 kg";
                        String businessType = "Retail";
                        if (role == Role.SUPPLIER) {
                            weight += ", " + FRUITS.get((index + 1) % FRUITS.size()) + " 25 kg";
                            businessType = "Supply";
                        }
                        business.update(user, "SupplyGo " + role + " " + number,
                                "SG " + businessType + " " + number + " | " + weight, "0000000", TOWNS.get(index));
                    }
                }
            }

            if (matchingProducts == 0) {
                var products = new ArrayList<>(state.products());
                products.addAll(PRODUCTS);
                var batches = new ArrayList<>(state.batches());
                long nextId = state.nextBatchId();
                String now = Instant.now().toString();
                for (Product product : PRODUCTS) {
                    int kilograms = product.id().equals("FR-001") ? 12 : 50;
                    batches.add(new FruitBatch(nextId++, product.id(), kilograms, now, "seed_setup"));
                }
                inventory.save(state.withStock(nextId, products, batches));
            }
        }
    }

    private static String username(Role role, int index) {
        return "sg_" + role.name().toLowerCase(Locale.ROOT) + "_" + (index + 1);
    }
}
