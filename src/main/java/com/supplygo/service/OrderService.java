package com.supplygo.service;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import com.supplygo.model.*;
import com.supplygo.storage.InventoryRepository;
import com.supplygo.storage.InventoryRepository.State;

/** Orders and inventory use the same lock and persist in the same atomic snapshot. */
public final class OrderService {
    public record Page(List<SupplyOrder> orders, int nextOffset) {
        public Page { orders = List.copyOf(orders); }
    }
    private final InventoryRepository repository;
    private final Clock clock;
    public OrderService(InventoryRepository repository) { this(repository, Clock.systemUTC()); }
    public OrderService(InventoryRepository repository, Clock clock) {
        this.repository = Objects.requireNonNull(repository); this.clock = Objects.requireNonNull(clock);
    }
    public static boolean isManager(Role role) {
        return role != null && role.canManageOrders();
    }
    public SupplyOrder create(UserProfile actor, String clientOrderId, String productId, int quantity) throws AuthException {
        reader(actor);
        if (actor.role() != Role.SELLER) throw new AuthException("FORBIDDEN", "Only sellers can place orders");
        if (!SupplyOrder.validClientOrderId(clientOrderId) || productId == null || !productId.matches("[A-Z0-9_-]{1,24}")
                || quantity < 1 || quantity > 1_000_000)
            throw new AuthException("INVALID_ORDER", "Use a UUID submission ID, valid product ID and 1–1000000 whole fruit units");
        synchronized (repository) {
            State state = repository.snapshot();
            for (SupplyOrder existing : state.orders()) {
                if (existing.sellerUsername().equals(actor.username()) && existing.clientOrderId().equals(clientOrderId)) {
                    if (!existing.productId().equals(productId) || existing.quantity() != quantity)
                        throw new AuthException("SUBMISSION_CONFLICT", "Submission ID was already used for different order details");
                    return existing; // A lost reply can be retried without reserving twice, even after restart.
                }
            }
            StockOperations.requireProduct(state, productId);
            if (StockOperations.available(state, productId) < quantity)
                throw new AuthException("INSUFFICIENT_STOCK", "Not enough available stock; order was not created");
            if (state.orders().size() == 10000 || state.nextOrderId() == Long.MAX_VALUE)
                throw new AuthException("ORDER_LIMIT", "Order storage limit reached");
            String now = clock.instant().toString();
            SupplyOrder order = new SupplyOrder(state.nextOrderId(), clientOrderId, actor.username(), productId,
                    quantity, OrderStatus.PENDING, now, now, actor.username());
            var orders = new ArrayList<>(state.orders()); orders.add(order);
            save(state.withOrders(state.nextOrderId() + 1, orders, state.batches()));
            return order;
        }
    }
    public Page list(UserProfile actor, int offset) throws AuthException {
        reader(actor);
        if (offset < 0 || offset > 10000) throw new AuthException("INVALID_ORDER", "Invalid order page offset");
        synchronized (repository) {
            List<SupplyOrder> visible = repository.snapshot().orders().stream()
                    .filter(o -> isManager(actor.role()) || o.sellerUsername().equals(actor.username())).toList();
            int start = Math.min(offset, visible.size());
            int end = Math.min(start + 10, visible.size());
            return new Page(visible.subList(start, end), end < visible.size() ? end : -1);
        }
    }
    public SupplyOrder get(UserProfile actor, long orderId) throws AuthException {
        reader(actor);
        synchronized (repository) { return visibleOrder(repository.snapshot(), actor, orderId); }
    }
    public SupplyOrder transition(UserProfile actor, long orderId, OrderStatus target) throws AuthException {
        reader(actor);
        if (target != OrderStatus.APPROVED && target != OrderStatus.FULFILLED && target != OrderStatus.CANCELLED)
            throw new AuthException("INVALID_ORDER", "Unsupported order action");
        synchronized (repository) {
            State state = repository.snapshot();
            SupplyOrder current = visibleOrder(state, actor, orderId);
            if (!isManager(actor.role()) && (target != OrderStatus.CANCELLED
                    || (current.status() != OrderStatus.PENDING && current.status() != OrderStatus.CANCELLED)))
                throw new AuthException("FORBIDDEN", "Sellers can cancel only their own pending orders");
            if (current.status() == target) return current; // Repeated action has no additional stock effect.
            boolean allowed = target == OrderStatus.APPROVED && current.status() == OrderStatus.PENDING
                    || target == OrderStatus.FULFILLED && current.status() == OrderStatus.APPROVED
                    || target == OrderStatus.CANCELLED && current.status().reservesStock();
            if (!allowed) throw new AuthException("INVALID_ORDER_STATE", "Cannot change " + current.status() + " to " + target + "; refresh orders");
            Instant now = clock.instant();
            if (now.isBefore(Instant.parse(current.updatedAt()))) now = Instant.parse(current.updatedAt());
            SupplyOrder updated = new SupplyOrder(current.id(), current.clientOrderId(), current.sellerUsername(),
                    current.productId(), current.quantity(), target, current.createdAt(), now.toString(), actor.username());
            var orders = new ArrayList<>(state.orders());
            for (int i = 0; i < orders.size(); i++) if (orders.get(i).id() == orderId) { orders.set(i, updated); break; }
            List<FruitBatch> batches = target == OrderStatus.FULFILLED
                    ? StockOperations.consumeFifo(state, current.productId(), current.quantity()) : state.batches();
            save(state.withOrders(state.nextOrderId(), orders, batches));
            return updated;
        }
    }
    private static void reader(UserProfile actor) throws AuthException {
        if (actor == null) throw new AuthException("AUTH_REQUIRED", "Sign in first");
        if (actor.role() != Role.SELLER && !isManager(actor.role()))
            throw new AuthException("FORBIDDEN", "Orders are available to sellers, seller managers, production managers and administrators");
    }
    private static SupplyOrder visibleOrder(State state, UserProfile actor, long id) throws AuthException {
        return state.orders().stream().filter(o -> o.id() == id && (isManager(actor.role()) || o.sellerUsername().equals(actor.username())))
                .findFirst().orElseThrow(() -> new AuthException("ORDER_NOT_FOUND", "Order not found"));
    }
    private void save(State state) throws AuthException {
        try { repository.save(state); }
        catch (IOException exception) { throw new AuthException("STORAGE_ERROR", "Could not save order; order and stock were not changed"); }
    }
}
