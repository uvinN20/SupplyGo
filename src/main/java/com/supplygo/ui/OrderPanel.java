package com.supplygo.ui;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import com.supplygo.model.*;
import com.supplygo.protocol.OrderData;

/** Order screens share the window's background-worker lifecycle and server session. */
final class OrderPanel extends JPanel {
    private record Page(List<SupplyOrder> orders, int nextOffset) { }
    private record Draft(String seller, String submissionId, String productId, int quantity) { }
    private final ClientWindow window;
    private final JButton refresh = button("Refresh", "refreshOrders");
    private final JButton next = button("Next page", "nextOrders");
    private final JButton create = button("New order", "createOrder");
    private final JButton retry = button("Retry submission", "retryOrder");
    private final JButton discard = button("Discard draft", "discardOrderDraft");
    private final JButton approve = button("Approve", "approveOrder");
    private final JButton fulfil = button("Fulfil", "fulfilOrder");
    private final JButton cancel = button("Cancel order", "cancelOrder");
    private final JButton details = button("Details", "orderDetails");
    private final JLabel status = new JLabel("Sign in as a seller or order manager.");
    private final DefaultTableModel rows = new DefaultTableModel(new String[]{"Order ID", "Seller", "Product", "Units", "Status"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = new JTable(rows);
    private final List<SupplyOrder> displayed = new ArrayList<>();
    private UserProfile profile;
    private boolean busy;
    private int nextOffset = -1;
    private record DraftKey(String endpoint, String seller) { }
    private final java.util.Map<DraftKey, Draft> drafts = new java.util.HashMap<>();
    private DraftKey draftKey() { return new DraftKey(window.currentEndpoint(), profile == null ? "" : profile.username()); }

    OrderPanel(ClientWindow window) {
        super(new BorderLayout(0, 6)); this.window = window;
        JPanel controls = new JPanel(new GridLayout(0, 1));
        controls.add(row(refresh, next, create, retry, discard)); controls.add(row(approve, fulfil, cancel, details));
        add(controls, BorderLayout.NORTH);
        table.setName("ordersTable"); status.setName("orderStatus");
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true); table.setPreferredScrollableViewportSize(new Dimension(700, 150));
        var renderer = new DefaultTableCellRenderer(); renderer.putClientProperty("html.disable", Boolean.TRUE);
        table.setDefaultRenderer(Object.class, renderer); add(new JScrollPane(table), BorderLayout.CENTER); add(status, BorderLayout.SOUTH);
        table.getSelectionModel().addListSelectionListener(event -> updateButtons());
        refresh.addActionListener(event -> load(0)); next.addActionListener(event -> load(nextOffset));
        create.addActionListener(event -> create()); retry.addActionListener(event -> submit());
        discard.addActionListener(event -> {
            if (JOptionPane.showConfirmDialog(this, "Check your orders first if a reply was lost. Discarding this draft does not cancel a saved order.",
                    "Discard unsent or unconfirmed draft?", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) {
                drafts.remove(draftKey()); status.setText("Draft discarded. Saved orders are unchanged."); updateButtons();
            }
        });
        approve.addActionListener(event -> change(OrderStatus.APPROVED));
        fulfil.addActionListener(event -> change(OrderStatus.FULFILLED)); cancel.addActionListener(event -> change(OrderStatus.CANCELLED));
        details.addActionListener(event -> {
            SupplyOrder selected = selected();
            if (selected == null) return;
            window.perform("Loading order…", () -> window.currentClient().getOrder(selected.id()), value -> {
                replace(value);
                JOptionPane.showMessageDialog(this, "Order: " + value.id() + " • " + value.status()
                        + "\nSeller: " + value.sellerUsername() + "\nProduct: " + value.productId() + " • Units: " + value.quantity()
                        + "\nCreated (UTC): " + value.createdAt() + "\nUpdated (UTC): " + value.updatedAt()
                        + "\nUpdated by: " + value.updatedBy() + "\nSubmission ID: " + value.clientOrderId(), "Order details", JOptionPane.INFORMATION_MESSAGE);
            });
        });
        updateButtons();
    }
    void setContext(UserProfile user, boolean working) {
        if (!java.util.Objects.equals(profile, user)) {
            displayed.clear(); rows.setRowCount(0); nextOffset = -1;
            status.setText(user == null ? "Sign in as a seller or order manager." : "Refresh to load orders. Stock is reserved when an order is placed.");
        }
        profile = user; busy = working; updateButtons();
    }
    void showError(String message) { status.setText(message); }
    private void load(int offset) {
        window.perform("Loading orders…", () -> {
            var page = window.currentClient().orderPage(offset);
            var orders = new ArrayList<SupplyOrder>();
            for (var row : page.getAsJsonArray("orders")) orders.add(OrderData.decode(row, null));
            return new Page(orders, page.get("nextOffset").getAsInt());
        }, page -> {
            displayed.clear(); displayed.addAll(page.orders()); nextOffset = page.nextOffset(); render();
            status.setText("Page " + (offset / 10 + 1) + " • " + displayed.size() + " orders • Select a row for actions or details");
        });
    }
    private void create() {
        JTextField productId = new JTextField(24); productId.setName("orderProductId");
        JSpinner quantity = new JSpinner(new SpinnerNumberModel(1, 1, 1000000, 1)); quantity.setName("orderQuantity");
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Product ID (uppercase)")); form.add(productId); form.add(new JLabel("Whole fruit units")); form.add(quantity);
        if (JOptionPane.showConfirmDialog(this, form, "Place order and reserve stock", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        try {
            quantity.commitEdit();
            String id = productId.getText().strip(); new Product(id, "validation");
            int units = ((Number) quantity.getValue()).intValue();
            if (units < 1 || units > 1_000_000) throw new IllegalArgumentException("Quantity must be 1–1000000");
            drafts.put(draftKey(), new Draft(profile.username(), UUID.randomUUID().toString(), id, units));
            submit();
        } catch (java.text.ParseException | IllegalArgumentException exception) {
            JOptionPane.showMessageDialog(this, exception.getMessage(), "Check order details", JOptionPane.ERROR_MESSAGE);
        }
    }
    private void submit() {
        DraftKey key = draftKey();
        Draft draft = drafts.get(key);
        if (draft == null || profile == null || !draft.seller().equals(profile.username())) return;
        window.perform("Submitting order…", () -> window.currentClient().createOrder(draft.submissionId(), draft.productId(), draft.quantity()), order -> {
            drafts.remove(key, draft); displayed.clear(); displayed.add(order); nextOffset = -1; render();
            window.inventoryChanged(); status.setText("Order " + order.id() + " • " + order.status() + ". Refresh inventory to see available stock.");
        });
    }
    private void change(OrderStatus target) {
        SupplyOrder order = selected(); if (order == null) return;
        if (target != OrderStatus.APPROVED && JOptionPane.showConfirmDialog(this,
                (target == OrderStatus.FULFILLED ? "Fulfil order " : "Cancel order ") + order.id() + " for " + order.quantity() + " units?",
                "Confirm order action", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;
        window.perform("Updating order…", () -> window.currentClient().changeOrder(order.id(), target), value -> {
            replace(value); window.inventoryChanged(); status.setText("Order " + value.id() + " • " + value.status() + ". Refresh inventory for current stock.");
        });
    }
    private void replace(SupplyOrder value) {
        for (int i = 0; i < displayed.size(); i++) if (displayed.get(i).id() == value.id()) { displayed.set(i, value); break; }
        render();
        for (int i = 0; i < displayed.size(); i++) if (displayed.get(i).id() == value.id()) { table.setRowSelectionInterval(i, i); break; }
    }
    private void render() {
        rows.setRowCount(0);
        for (SupplyOrder order : displayed) rows.addRow(new Object[]{order.id(), order.sellerUsername(), order.productId(), order.quantity(), order.status()});
    }
    private SupplyOrder selected() {
        int row = table.getSelectedRow(); return row >= 0 && row < displayed.size() ? displayed.get(row) : null;
    }
    private void updateButtons() {
        boolean manager = profile != null && profile.role().canManageOrders();
        boolean seller = profile != null && profile.role() == Role.SELLER;
        boolean enabled = !busy && (manager || seller);
        boolean hasDraft = seller && drafts.containsKey(draftKey());
        refresh.setEnabled(enabled); next.setEnabled(enabled && nextOffset >= 0);
        create.setEnabled(!busy && seller && !hasDraft); retry.setEnabled(!busy && hasDraft); discard.setEnabled(!busy && hasDraft);
        SupplyOrder selected = selected();
        details.setEnabled(enabled && selected != null);
        approve.setEnabled(enabled && manager && selected != null && selected.status() == OrderStatus.PENDING);
        fulfil.setEnabled(enabled && manager && selected != null && selected.status() == OrderStatus.APPROVED);
        cancel.setEnabled(enabled && selected != null && (manager && selected.status().reservesStock()
                || seller && selected.status() == OrderStatus.PENDING && selected.sellerUsername().equals(profile.username())));
    }
    private static JButton button(String label, String name) { JButton button = new JButton(label); button.setName(name); return button; }
    private static JPanel row(Component... components) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        for (Component component : components) row.add(component); return row;
    }
}
