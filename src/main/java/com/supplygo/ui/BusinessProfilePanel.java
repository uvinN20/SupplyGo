package com.supplygo.ui;

import java.awt.*;
import java.util.Objects;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import com.supplygo.model.*;

/** Business profile editor and role-specific directories; all I/O uses the window worker. */
final class BusinessProfilePanel extends JPanel {
    private final ClientWindow window;
    private final JTextField name = field("businessFullName"), business = field("businessName"),
            phone = field("businessPhone"), location = field("businessLocation");
    private final JButton load = button("Load my profile", "loadBusinessProfile"), save = button("Save profile", "saveBusinessProfile"),
            refresh = button("Refresh directory", "refreshBusinessDirectory"), next = button("Next page", "nextBusinessDirectory");
    private final JComboBox<String> directory = new JComboBox<>();
    private final JLabel feedback = new JLabel("Sign in to manage business profiles.");
    private final JPanel editor = new JPanel(new GridLayout(0, 2, 8, 6));
    private final JPanel directoryPanel = new JPanel(new BorderLayout(0, 6));
    private final DefaultTableModel rows = new DefaultTableModel(new String[]{"Username", "Full name", "Business", "Contact", "Location"}, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private UserProfile identity;
    private int nextOffset = -1;
    BusinessProfilePanel(ClientWindow window) {
        super(new BorderLayout(0, 8)); this.window = window;
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        editor.add(new JLabel("Full name (max 64)")); editor.add(name);
        editor.add(new JLabel("Business name (max 96)")); editor.add(business);
        editor.add(new JLabel("Contact number")); editor.add(phone);
        editor.add(new JLabel("Town / location (max 64)")); editor.add(location);
        editor.add(load); editor.add(save); add(editor, BorderLayout.NORTH);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT));
        directory.setName("businessDirectoryType"); actions.add(directory); actions.add(refresh); actions.add(next);
        directoryPanel.add(actions, BorderLayout.NORTH);
        JTable table = new JTable(rows); table.setName("businessDirectoryTable"); table.setFillsViewportHeight(true);
        table.setPreferredScrollableViewportSize(new Dimension(700, 140));
        var renderer = new javax.swing.table.DefaultTableCellRenderer(); renderer.putClientProperty("html.disable", Boolean.TRUE);
        table.setDefaultRenderer(Object.class, renderer); directoryPanel.add(new JScrollPane(table), BorderLayout.CENTER);
        add(directoryPanel, BorderLayout.CENTER); feedback.setName("businessProfileStatus");
        feedback.putClientProperty("html.disable", Boolean.TRUE); add(feedback, BorderLayout.SOUTH);
        load.addActionListener(event -> window.perform("Loading business profile…", () -> window.currentClient().businessProfile(), this::showProfile));
        save.addActionListener(event -> {
            String n = name.getText().strip(), b = business.getText().strip(), p = phone.getText().strip(), l = location.getText().strip();
            window.perform("Saving business profile…", () -> window.currentClient().updateBusinessProfile(n, b, p, l), value -> {
                showProfile(value); feedback.setText("Business profile saved.");
            });
        });
        refresh.addActionListener(event -> loadDirectory(0)); next.addActionListener(event -> loadDirectory(nextOffset));
        directory.addActionListener(event -> { rows.setRowCount(0); nextOffset = -1; next.setEnabled(false); });
    }
    private static JTextField field(String name) { var field = new JTextField(24); field.setName(name); return field; }
    private static JButton button(String label, String name) { var button = new JButton(label); button.setName(name); return button; }
    private void showProfile(BusinessProfile profile) {
        name.setText(profile.fullName()); business.setText(profile.businessName()); phone.setText(profile.contactNumber()); location.setText(profile.location());
        feedback.setText("Profile loaded. Last updated: " + profile.updatedAt());
    }
    void showError(String message) { feedback.setText(message); }
    private void loadDirectory(int offset) {
        boolean suppliers = "Suppliers".equals(directory.getSelectedItem());
        window.perform("Loading business directory…", () -> window.currentClient().businessDirectory(suppliers, offset), page -> {
            rows.setRowCount(0);
            for (var element : page.getAsJsonArray("profiles")) {
                var profile = com.supplygo.protocol.BusinessProfileData.decode(element);
                rows.addRow(new Object[]{profile.username(), profile.fullName(), profile.businessName(), profile.contactNumber(), profile.location()});
            }
            nextOffset = page.get("nextOffset").getAsInt();
            feedback.setText(rows.getRowCount() == 0 ? "No saved profiles in this directory." : "Page " + (offset / 10 + 1) + " • " + rows.getRowCount() + " profiles");
        });
    }
    void update(UserProfile actor, boolean busy) {
        if (!Objects.equals(identity, actor)) {
            identity = actor; name.setText(""); business.setText(""); phone.setText(""); location.setText("");
            rows.setRowCount(0); nextOffset = -1; directory.removeAllItems();
            if (actor != null && (actor.role() == Role.ADMIN || actor.role() == Role.SUPPLIER_MANAGER)) directory.addItem("Suppliers");
            if (actor != null && (actor.role() == Role.ADMIN || actor.role() == Role.SELLER_MANAGER)) directory.addItem("Sellers");
            feedback.setText(actor == null ? "Sign in to manage business profiles." : "Load your profile or refresh your directory.");
        }
        boolean owner = actor != null && (actor.role() == Role.SUPPLIER || actor.role() == Role.SELLER);
        boolean manager = actor != null && (actor.role() == Role.ADMIN || actor.role() == Role.SUPPLIER_MANAGER || actor.role() == Role.SELLER_MANAGER);
        editor.setVisible(owner); directoryPanel.setVisible(manager);
        for (Component component : editor.getComponents()) component.setEnabled(owner && !busy);
        directory.setEnabled(manager && !busy); refresh.setEnabled(manager && !busy); next.setEnabled(manager && !busy && nextOffset >= 0);
        if (actor != null && !owner && !manager) feedback.setText("Your role does not have access to business profiles.");
    }
}
