package com.supplygo.ui;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import javax.swing.*;
import com.supplygo.client.ServerRejectedException;
import com.supplygo.client.SupplyGoTcpClient;
import com.supplygo.config.NetworkConfig;
import com.supplygo.model.Role;
import com.supplygo.model.UserProfile;
import com.supplygo.security.TlsSupport;

/** Socket work stays in background workers; session-dependent controls update on the EDT. */
public final class ClientWindow extends JFrame {
    private final JTextField host;
    private final JSpinner port;
    private final JCheckBox tls = new JCheckBox("Use TLS for sign-in", Boolean.getBoolean("supplygo.tls"));
    private final JLabel status = new JLabel("Offline");
    private final JLabel identity = new JLabel("Not signed in");
    private final JButton connect = button("Connect", "connect");
    private final JButton ping = button("Send Ping", "ping");
    private final JButton disconnect = button("Disconnect", "disconnect");
    private final JTextField username = new JTextField(20);
    private final JPasswordField password = new JPasswordField(20);
    private final JComboBox<Role> signupRole = new JComboBox<>(new Role[]{Role.SUPPLIER, Role.SELLER});
    private final JButton signIn = button("Sign In", "signIn");
    private final JButton register = button("Create Account", "register");
    private final JButton signOut = button("Sign Out", "signOut");
    private final JButton profileButton = button("My Profile", "profile");
    private final JButton adminStats = button("Account Count", "adminStats");
    private final JTextArea log = new JTextArea(8, 58);
    private final JComboBox<String> inventoryView = new JComboBox<>(new String[]{"Products", "Fruit batches"});
    private final JButton refreshInventory = button("Refresh", "refreshInventory");
    private final JButton nextInventory = button("Next page", "nextInventory");
    private final JButton createProduct = button("Create product", "createProduct");
    private final JButton receiveBatch = button("Receive batch", "receiveBatch");
    private final JButton withdrawStock = button("Withdraw FIFO", "withdrawStock");
    private final JLabel inventoryStatus = new JLabel("Sign in to view inventory. Quantities are whole fruit units.");
    private final javax.swing.table.DefaultTableModel inventoryRows = new javax.swing.table.DefaultTableModel() {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable inventoryTable = new JTable(inventoryRows);
    private int nextOffset = -1;
    private SupplyGoTcpClient client;
    private UserProfile profile;
    private boolean connected;
    private boolean busy;

    private ClientWindow(NetworkConfig config) {
        super("SupplyGo | Desktop Client");
        host = new JTextField(config.serverHost(), 20);
        host.setName("serverHost");
        port = new JSpinner(new SpinnerNumberModel(config.tcpPort(), 1, 65535, 1));
        port.setEditor(new JSpinner.NumberEditor(port, "0"));
        port.setName("tcpPort");
        tls.setName("useTls");
        username.setName("username"); password.setName("password"); signupRole.setName("signupRole");
        status.setName("connectionStatus"); identity.setName("identity"); log.setName("connectionLog");

        JPanel content = new JPanel(new BorderLayout(0, 16));
        content.setBackground(new Color(246, 248, 252));
        content.setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));
        JPanel heading = new JPanel(new GridLayout(0, 1, 0, 6)); heading.setOpaque(false);
        JLabel title = new JLabel("SupplyGo"); title.setForeground(new Color(27, 76, 132));
        title.setFont(title.getFont().deriveFont(Font.BOLD, 30f));
        heading.add(title); heading.add(new JLabel("Connect smarter. Deliver faster."));
        content.add(heading, BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(0, 14)); body.setOpaque(false);
        JPanel forms = new JPanel(); forms.setLayout(new BoxLayout(forms, BoxLayout.Y_AXIS));
        forms.add(row(new JLabel("Server"), host, new JLabel("TCP port"), port, tls));
        forms.add(row(connect, ping, disconnect, status));
        JPanel accounts = new JPanel(); accounts.setLayout(new BoxLayout(accounts, BoxLayout.Y_AXIS));
        accounts.setBorder(BorderFactory.createTitledBorder("Your account"));
        accounts.add(row(new JLabel("Username"), username, new JLabel("Password"), password));
        accounts.add(row(signIn, new JLabel("New account role"), signupRole, register));
        accounts.add(row(identity, profileButton, signOut, adminStats));
        accounts.add(row(new JLabel("Passwords: 12–128 characters. Administrator and manager accounts are created by the operator.")));
        forms.add(accounts); body.add(forms, BorderLayout.NORTH);
        log.setEditable(false); log.setLineWrap(true); log.setWrapStyleWord(true);
        JScrollPane scroll = new JScrollPane(log); scroll.setBorder(BorderFactory.createTitledBorder("Activity"));
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Activity", scroll);
        JPanel stock = new JPanel(new BorderLayout(0, 6));
        stock.add(row(inventoryView, refreshInventory, nextInventory, createProduct, receiveBatch, withdrawStock), BorderLayout.NORTH);
        inventoryStatus.setName("inventoryStatus");
        inventoryTable.setName("inventoryTable"); inventoryView.setName("inventoryView");
        var stockRenderer = new javax.swing.table.DefaultTableCellRenderer();
        stockRenderer.putClientProperty("html.disable", Boolean.TRUE);
        inventoryTable.setDefaultRenderer(Object.class, stockRenderer);
        inventoryTable.setFillsViewportHeight(true);
        inventoryTable.setPreferredScrollableViewportSize(new Dimension(700, 150));
        stock.add(new JScrollPane(inventoryTable), BorderLayout.CENTER);
        stock.add(inventoryStatus, BorderLayout.SOUTH); tabs.addTab("Inventory", stock);
        body.add(tabs, BorderLayout.CENTER); content.add(body, BorderLayout.CENTER);
        content.add(new JLabel("Sign-in requires a TLS server and its trusted certificate. See docs/AUTHENTICATION.md."), BorderLayout.SOUTH);
        setContentPane(content);

        connect.addActionListener(event -> connect());
        ping.addActionListener(event -> perform("Sending PING…", client::ping, value -> append("Server: " + value)));
        disconnect.addActionListener(event -> perform("Disconnecting…", client::disconnect, value -> {
            connected = false; profile = null; append("Server: " + value);
        }));
        signIn.addActionListener(event -> account(false));
        register.addActionListener(event -> account(true));
        signOut.addActionListener(event -> perform("Signing out…", client::logout, value -> {
            profile = null; append("Signed out");
        }));
        profileButton.addActionListener(event -> perform("Loading profile…", client::profile, value -> {
            profile = value; append(value.username() + " • " + value.role());
        }));
        adminStats.addActionListener(event -> perform("Loading account count…", client::adminUserCount,
                value -> append("Registered accounts: " + value)));
        refreshInventory.addActionListener(event -> loadInventory(0));
        nextInventory.addActionListener(event -> loadInventory(nextOffset));
        inventoryView.addActionListener(event -> { clearInventory(); if (profile != null && !busy) loadInventory(0); });
        createProduct.addActionListener(event -> editStock(true, false));
        receiveBatch.addActionListener(event -> editStock(false, true));
        withdrawStock.addActionListener(event -> editStock(false, false));
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent event) {
                password.setText("");
                if (client != null) client.close();
                profile = null;
            }
        });
        updateControls(); append("Start the server, choose its address and connection mode, then connect.");
        pack(); setMinimumSize(new Dimension(getWidth(), getHeight())); setLocationRelativeTo(null);
    }

    public static JFrame create(NetworkConfig config) {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Create Swing windows on the event dispatch thread");
        return new ClientWindow(config);
    }
    private static JButton button(String text, String name) { JButton button = new JButton(text); button.setName(name); return button; }
    private static JPanel row(Component... components) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        for (Component component : components) panel.add(component);
        return panel;
    }

    private void clearInventory() {
        inventoryRows.setRowCount(0); nextOffset = -1;
        inventoryStatus.setText("Refresh to load stock. Quantities are whole fruit units.");
    }
    private void loadInventory(int offset) {
        boolean batches = inventoryView.getSelectedIndex() == 1;
        perform("Loading inventory…", () -> client.inventoryPage(batches, offset), page -> showInventory(page, batches, offset));
    }
    private void showInventory(com.google.gson.JsonObject page, boolean batches, int offset) {
        inventoryRows.setColumnIdentifiers(batches ? new String[]{"Batch ID", "Product ID", "Units", "Received (UTC)", "Received by"}
                : new String[]{"Product ID", "Name", "Units available"});
        int[] widths = batches ? new int[]{70, 110, 70, 310, 140} : new int[]{140, 400, 160};
        for (int i = 0; i < widths.length; i++) inventoryTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        inventoryRows.setRowCount(0);
        for (var element : page.getAsJsonArray(batches ? "batches" : "products")) {
            var row = element.getAsJsonObject();
            String[] keys = batches ? new String[]{"id", "productId", "quantity", "receivedAt", "receivedBy"} : new String[]{"id", "name", "quantity"};
            Object[] values = new Object[keys.length];
            for (int i = 0; i < keys.length; i++) values[i] = row.get(keys[i]).getAsString();
            inventoryRows.addRow(values);
        }
        nextOffset = page.get("nextOffset").getAsInt();
        inventoryStatus.setText("Page " + (offset / 10 + 1) + " • " + inventoryRows.getRowCount() + " rows • Whole fruit units • Refresh returns to page 1");
    }
    private void editStock(boolean product, boolean receive) {
        JTextField id = new JTextField(24);
        JTextField name = new JTextField(24);
        JSpinner quantity = new JSpinner(new SpinnerNumberModel(1, 1, 1000000, 1));
        id.setName("productId"); name.setName("productName"); quantity.setName("stockQuantity");
        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        form.add(new JLabel("Product ID (uppercase)")); form.add(id);
        form.add(new JLabel(product ? "Product name" : "Whole fruit units")); form.add(product ? name : quantity);
        String title = product ? "Create product" : receive ? "Receive fruit batch" : "Withdraw oldest stock first";
        if (JOptionPane.showConfirmDialog(this, form, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;
        try { quantity.commitEdit(); }
        catch (java.text.ParseException exception) { JOptionPane.showMessageDialog(this, "Enter a whole quantity from 1 to 1000000"); return; }
        String productId = id.getText().strip(); String productName = name.getText().strip();
        int units = ((Number) quantity.getValue()).intValue();
        perform("Saving inventory…", () -> product ? client.createProduct(productId, productName) : client.changeStock(receive, productId, units), value -> {
            clearInventory(); append(title + " completed. Refresh inventory to see current stock.");
            inventoryStatus.setText(title + " saved. Click Refresh to load current stock.");
        });
    }

    private void connect() {
        try {
            port.commitEdit();
            client = new SupplyGoTcpClient(host.getText(), ((Number) port.getValue()).intValue(), tls.isSelected() ? TlsSupport.defaultContext() : null);
        } catch (Exception exception) { append("Check server settings and TLS truststore: " + exception.getMessage()); return; }
        SupplyGoTcpClient attempt = client;
        perform("Connecting…", () -> { attempt.connect(); return attempt.ping(); }, value -> {
            connected = true; profile = null; append("Server: " + value + (attempt.isSecure() ? " (TLS verified)" : " (connection demo)"));
        });
    }

    private void account(boolean creating) {
        String accountName = username.getText();
        char[] secret = password.getPassword(); password.setText("");
        Role role = (Role) signupRole.getSelectedItem();
        perform(creating ? "Creating account…" : "Signing in…", () -> {
            try { return creating ? client.register(accountName, secret, role) : client.login(accountName, secret); }
            finally { Arrays.fill(secret, '\0'); }
        }, value -> {
            if (creating) append("Account created for " + value.username() + ". Sign in with your password.");
            else { profile = value; append("Welcome, " + value.username() + " • " + value.role()); }
        });
    }

    private <T> void perform(String progress, Callable<T> operation, Consumer<T> success) {
        busy = true; status.setText(progress); updateControls();
        new SwingWorker<T, Void>() {
            @Override protected T doInBackground() throws Exception { return operation.call(); }
            @Override protected void done() {
                try { success.accept(get()); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); failed("Operation interrupted"); }
                catch (ExecutionException exception) {
                    Throwable cause = exception.getCause();
                    if (cause instanceof ServerRejectedException rejected) {
                        if (rejected.code().equals("AUTH_REQUIRED") || rejected.code().equals("SESSION_EXPIRED")) profile = null;
                        append(rejected.getMessage());
                        inventoryStatus.setText(rejected.getMessage());
                    } else failed(cause.getMessage() == null ? "Connection failed" : cause.getMessage());
                } finally { busy = false; status.setText(connected ? "Connected" : "Offline"); updateControls(); }
            }
        }.execute();
    }
    private void failed(String message) {
        connected = false; profile = null;
        if (client != null) client.close();
        append("Connection error: " + message);
    }
    private void updateControls() {
        host.setEnabled(!busy && !connected); port.setEnabled(!busy && !connected); tls.setEnabled(!busy && !connected);
        connect.setEnabled(!busy && !connected); ping.setEnabled(!busy && connected); disconnect.setEnabled(!busy && connected);
        boolean anonymous = !busy && connected && client != null && client.isSecure() && profile == null;
        username.setEnabled(anonymous); password.setEnabled(anonymous); signupRole.setEnabled(anonymous);
        signIn.setEnabled(anonymous); register.setEnabled(anonymous);
        signOut.setEnabled(!busy && profile != null); profileButton.setEnabled(!busy && profile != null);
        adminStats.setVisible(profile != null && profile.role() == Role.ADMIN); adminStats.setEnabled(!busy && profile != null);
        boolean inventoryEnabled = !busy && connected && profile != null;
        inventoryView.setEnabled(inventoryEnabled); refreshInventory.setEnabled(inventoryEnabled);
        nextInventory.setEnabled(inventoryEnabled && nextOffset >= 0);
        boolean writer = profile != null && (profile.role() == Role.ADMIN || profile.role() == Role.PRODUCTION_MANAGER);
        createProduct.setEnabled(inventoryEnabled && writer); receiveBatch.setEnabled(inventoryEnabled && writer); withdrawStock.setEnabled(inventoryEnabled && writer);
        if (profile == null) clearInventory();
        identity.setText(profile == null ? "Not signed in" : profile.username() + " • " + profile.role());
    }
    private void append(String message) {
        log.append(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "  " + message + "\n");
        if (log.getLineCount() > 200) {
            try { log.replaceRange("", 0, log.getLineEndOffset(log.getLineCount() - 200)); }
            catch (javax.swing.text.BadLocationException exception) { log.setText(""); }
        }
        log.setCaretPosition(log.getDocument().getLength());
    }
}
