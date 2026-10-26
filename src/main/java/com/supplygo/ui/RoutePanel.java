package com.supplygo.ui;

import java.awt.*;
import java.util.Objects;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import com.supplygo.model.*;

/** Server-owned road network; background socket work uses the existing window worker. */
final class RoutePanel extends JPanel {
    private final ClientWindow window;
    private final JComboBox<String> view = new JComboBox<>(new String[]{"Locations", "Roads"});
    private final JButton refresh = button("Refresh", "refreshRoutes"), next = button("Next page", "nextRoutes"),
            saveLocation = button("Save location", "saveLocation"), saveRoad = button("Save road", "saveRoad"), find = button("Find shortest route", "findRoute");
    private final JTextField id = field("routeLocationId"), name = field("routeLocationName"), from = field("routeFrom"), to = field("routeTo");
    private final JSpinner meters = new JSpinner(new SpinnerNumberModel(1000L, 1L, 1_000_000_000L, 1L));
    private final JTextArea result = new JTextArea(3, 50);
    private final DefaultTableModel rows = new DefaultTableModel() { @Override public boolean isCellEditable(int r, int c) { return false; } };
    private final JLabel status = new JLabel("Sign in to plan a route.");
    private UserProfile identity; private int nextOffset = -1;
    RoutePanel(ClientWindow window) {
        super(new BorderLayout(0, 6)); this.window = window;
        JPanel forms = new JPanel(new GridLayout(0, 1));
        forms.add(row(view, refresh, next)); forms.add(row(new JLabel("Location ID"), id, new JLabel("Name"), name, saveLocation));
        meters.setName("routeMeters"); meters.setEditor(new JSpinner.NumberEditor(meters, "0"));
        forms.add(row(new JLabel("From ID"), from, new JLabel("To ID"), to, find));
        forms.add(row(new JLabel("Two-way road distance (meters)"), meters, saveRoad)); add(forms, BorderLayout.NORTH);
        JTable table = new JTable(rows); table.setName("routeTable"); table.setFillsViewportHeight(true);
        var renderer = new javax.swing.table.DefaultTableCellRenderer(); renderer.putClientProperty("html.disable", Boolean.TRUE); table.setDefaultRenderer(Object.class, renderer);
        add(new JScrollPane(table), BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout()); result.setName("routeResult"); result.setEditable(false); result.setLineWrap(true); result.setWrapStyleWord(true);
        bottom.add(new JScrollPane(result), BorderLayout.CENTER); status.setName("routeStatus"); status.putClientProperty("html.disable", Boolean.TRUE); bottom.add(status, BorderLayout.SOUTH); add(bottom, BorderLayout.SOUTH);
        refresh.addActionListener(e -> load(0)); next.addActionListener(e -> load(nextOffset));
        view.addActionListener(e -> { clearRows(); if (identity != null) status.setText("Refresh to load the selected view."); next.setEnabled(false); });
        saveLocation.addActionListener(e -> { String key = id.getText().strip(), label = name.getText().strip();
            window.perform("Saving location…", () -> window.currentClient().saveLocation(key, label), value -> changed()); });
        saveRoad.addActionListener(e -> {
            try { meters.commitEdit(); } catch (java.text.ParseException error) { status.setText("Enter a whole distance from 1 to 1000000000 meters"); return; }
            String start = from.getText().strip(), end = to.getText().strip(); long distance = ((Number)meters.getValue()).longValue();
            window.perform("Saving road…", () -> window.currentClient().saveRoad(start, end, distance), value -> changed());
        });
        find.addActionListener(e -> { String start = from.getText().strip(), end = to.getText().strip(); result.setText("");
            window.perform("Finding shortest route…", () -> window.currentClient().findRoute(start, end), data -> {
                java.util.List<String> path = new java.util.ArrayList<>(); data.getAsJsonArray("path").forEach(value -> path.add(value.getAsString()));
                result.setText(String.join(" → ", path) + "\nTotal: " + data.get("meters").getAsLong() + " meters"); status.setText("Shortest distance in the saved road network.");
            });
        });
    }
    private static JButton button(String label, String name) { JButton value = new JButton(label); value.setName(name); return value; }
    private static JTextField field(String name) { JTextField value = new JTextField(12); value.setName(name); return value; }
    private static JPanel row(Component... values) { JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT)); for (Component value : values) panel.add(value); return panel; }
    private void clearRows() { rows.setRowCount(0); nextOffset = -1; }
    private void changed() { clearRows(); result.setText(""); status.setText("Road network saved. Refresh to reload."); }
    private void load(int offset) {
        boolean roads = view.getSelectedIndex() == 1;
        window.perform("Loading road network…", () -> window.currentClient().roadNetworkPage(roads, offset), data -> {
            rows.setColumnIdentifiers(roads ? new String[]{"From", "To", "Meters"} : new String[]{"ID", "Name"}); rows.setRowCount(0);
            for (var element : data.getAsJsonArray(roads ? "roads" : "locations")) {
                var item = element.getAsJsonObject(); rows.addRow(roads ? new Object[]{item.get("from").getAsString(), item.get("to").getAsString(), item.get("meters").getAsLong()}
                        : new Object[]{item.get("id").getAsString(), item.get("name").getAsString()});
            }
            nextOffset = data.get("nextOffset").getAsInt(); status.setText(rows.getRowCount() == 0 ? "No saved entries. Ask a manager to add locations and roads." : "Page " + (offset / 10 + 1) + " • " + rows.getRowCount() + " entries");
        });
    }
    void showError(String message) { status.setText(message); }
    void setContext(UserProfile profile, boolean busy) {
        if (!Objects.equals(profile, identity)) { identity = profile; clearRows(); result.setText(""); id.setText(""); name.setText(""); from.setText(""); to.setText(""); status.setText(profile == null ? "Sign in to plan a route." : "Refresh the saved locations and enter two IDs."); }
        boolean enabled = profile != null && !busy, writer = enabled && (profile.role() == Role.ADMIN || profile.role() == Role.PRODUCTION_MANAGER);
        refresh.setEnabled(enabled); view.setEnabled(enabled); next.setEnabled(enabled && nextOffset >= 0); find.setEnabled(enabled); from.setEnabled(enabled); to.setEnabled(enabled);
        id.setEnabled(writer); name.setEnabled(writer); meters.setEnabled(writer); saveLocation.setEnabled(writer); saveRoad.setEnabled(writer);
    }
}
