package com.supplygo.ui;

import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.*;
import com.supplygo.model.UserProfile;

/** Presentation only: existing panels retain all network and authorization logic. */
final class AppShell extends JPanel {
    private final CardLayout rootLayout = new CardLayout();
    private final JPanel roots = new JPanel(rootLayout);
    private final CardLayout pageLayout = new CardLayout();
    private final JPanel pages = new JPanel(pageLayout);
    private final JPanel navigation = new JPanel();
    private final Map<String, JButton> links = new LinkedHashMap<>();
    private final JLabel heading = AppTheme.label("Dashboard", 28, AppTheme.INK);
    private final JLabel description = AppTheme.label("Your supply chain, connected.", 13, AppTheme.MUTED);
    private final JLabel welcome = AppTheme.label("Welcome back", 28, Color.WHITE);
    private final JLabel role = AppTheme.label("", 14, Color.WHITE);
    private final JTextArea feedback = new JTextArea(2, 50);
    private final JProgressBar progress = new JProgressBar();
    private final JPanel sessionControls;
    private final JPanel login;
    private boolean signedIn;
    private String currentPage = "Dashboard";

    AppShell(JPanel connection, JPanel account, JPanel sessionControls, JLabel status) {
        super(new BorderLayout());
        this.sessionControls = sessionControls;
        setBackground(AppTheme.BACKGROUND);
        login = new JPanel(new GridLayout(1, 2, 26, 0));
        login.setBackground(AppTheme.BACKGROUND);
        login.setBorder(BorderFactory.createEmptyBorder(26, 26, 26, 26));
        JPanel story = new GradientPanel(); story.setLayout(new BorderLayout());
        story.setBorder(BorderFactory.createEmptyBorder(34, 32, 34, 32));
        JPanel message = vertical();
        message.add(AppTheme.label("SUPPLYGO / CONNECTED COMMERCE", 13, Color.WHITE));
        message.add(Box.createVerticalStrut(65));
        message.add(AppTheme.label("Fresh ideas.", 40, Color.WHITE));
        message.add(AppTheme.label("Smarter supply.", 40, Color.WHITE));
        message.add(Box.createVerticalStrut(24));
        message.add(AppTheme.label("One place for stock, orders and people.", 15, Color.WHITE));
        message.add(Box.createVerticalStrut(30));
        for (String text : new String[]{"01   Keep your inventory moving", "02   Plan every delivery", "03   Bring your team together"}) {
            message.add(AppTheme.label(text, 15, Color.WHITE)); message.add(Box.createVerticalStrut(20));
        }
        story.add(message, BorderLayout.NORTH);
        story.add(AppTheme.label("CONNECT SMARTER. DELIVER FASTER.", 12, Color.WHITE), BorderLayout.SOUTH);
        login.add(story);
        JPanel form = vertical(); form.setBorder(BorderFactory.createEmptyBorder(18, 12, 18, 12));
        form.add(AppTheme.label("Welcome to SupplyGo", 28, AppTheme.INK));
        form.add(Box.createVerticalStrut(8));
        form.add(AppTheme.label("Connect to your server, then sign in.", 14, AppTheme.MUTED));
        form.add(Box.createVerticalStrut(25));
        form.add(connection); form.add(Box.createVerticalStrut(20)); form.add(account);
        form.add(Box.createVerticalStrut(16));
        form.add(AppTheme.label("Admin accounts are created by your operator.", 12, AppTheme.MUTED));
        form.add(Box.createVerticalStrut(16));
        JButton activity = new JButton("View connection activity");
        activity.addActionListener(e -> openActivityDialog()); form.add(activity);
        for (Component c : form.getComponents()) if (c instanceof JComponent jc) {
            jc.setAlignmentX(LEFT_ALIGNMENT);
            jc.setMaximumSize(new Dimension(Integer.MAX_VALUE, jc.getPreferredSize().height));
        }
        JScrollPane formScroll = new JScrollPane(form); formScroll.setBorder(null);
        formScroll.getVerticalScrollBar().setUnitIncrement(20); login.add(formScroll);
        roots.add(login, "login");

        JPanel workspace = new JPanel(new BorderLayout(0, 0));
        JPanel sidebar = new JPanel(new BorderLayout(0, 28)); sidebar.setBackground(AppTheme.NAVY);
        sidebar.setBorder(BorderFactory.createEmptyBorder(30, 18, 24, 18));
        sidebar.setPreferredSize(new Dimension(205, 0));
        JPanel brand = vertical(); brand.add(AppTheme.label("SupplyGo", 28, Color.WHITE));
        brand.add(Box.createVerticalStrut(6)); brand.add(AppTheme.label("YOUR OPERATIONS HUB", 10, new Color(154, 177, 210)));
        sidebar.add(brand, BorderLayout.NORTH);
        navigation.setOpaque(false); navigation.setLayout(new BoxLayout(navigation, BoxLayout.Y_AXIS));
        sidebar.add(navigation, BorderLayout.CENTER);
        sidebar.add(AppTheme.label("JAVA / CONNECTED TEAM", 10, new Color(154, 177, 210)), BorderLayout.SOUTH);
        workspace.add(sidebar, BorderLayout.WEST);
        JPanel main = new JPanel(new BorderLayout(0, 18)); main.setBackground(AppTheme.BACKGROUND);
        main.setBorder(BorderFactory.createEmptyBorder(24, 24, 20, 24));
        heading.setAlignmentX(LEFT_ALIGNMENT); description.setAlignmentX(LEFT_ALIGNMENT); sessionControls.setAlignmentX(LEFT_ALIGNMENT);
        JPanel header = vertical(); header.add(heading); header.add(Box.createVerticalStrut(6)); header.add(description);
        header.add(Box.createVerticalStrut(10)); header.add(sessionControls);
        main.add(header, BorderLayout.NORTH); main.add(pages, BorderLayout.CENTER);
        workspace.add(main, BorderLayout.CENTER); roots.add(workspace, "workspace"); add(roots, BorderLayout.CENTER);
        JPanel footer = new JPanel(new BorderLayout(16, 4));
        footer.setBorder(BorderFactory.createEmptyBorder(10, 22, 10, 22));
        feedback.setEditable(false); feedback.setLineWrap(true); feedback.setWrapStyleWord(true);
        feedback.setOpaque(false); feedback.setForeground(AppTheme.MUTED);
        feedback.setFont(UIManager.getFont("Label.font")); feedback.setName("operationFeedback");
        status.putClientProperty("html.disable", true); status.setForeground(AppTheme.GREEN);
        footer.add(status, BorderLayout.WEST); footer.add(feedback, BorderLayout.CENTER);
        progress.setPreferredSize(new Dimension(90, 5)); progress.setForeground(AppTheme.BLUE);
        progress.setVisible(false); footer.add(progress, BorderLayout.EAST); add(footer, BorderLayout.SOUTH);
        addPage("Dashboard", "Your supply chain, connected.", dashboard());
    }

    private JPanel dashboard() {
        JPanel dashboard = new JPanel(new BorderLayout(0, 22)); dashboard.setOpaque(false);
        JPanel banner = new GradientPanel(); banner.setLayout(new GridLayout(0, 1, 0, 10));
        banner.setBorder(BorderFactory.createEmptyBorder(28, 28, 28, 28));
        banner.add(welcome); banner.add(role);
        banner.add(AppTheme.label("Good connections make great deliveries.", 15, Color.WHITE));
        dashboard.add(banner, BorderLayout.NORTH);
        JPanel cards = new JPanel(new GridLayout(2, 2, 18, 18)); cards.setOpaque(false);
        cards.add(shortcut("Inventory", "Stock & availability", "Browse products and fruit batches", AppTheme.BLUE));
        cards.add(shortcut("Orders", "Orders & fulfilment", "Track requests from start to finish", AppTheme.GREEN));
        cards.add(shortcut("Routes", "Routes & delivery", "Find the shortest saved route", new Color(112, 69, 197)));
        cards.add(shortcut("Chat", "Team messenger", "Join your shared team room", new Color(181, 85, 20)));
        dashboard.add(cards, BorderLayout.CENTER);
        dashboard.add(AppTheme.label("Open a workspace to load live data. Available actions follow your account role.", 12, AppTheme.MUTED), BorderLayout.SOUTH);
        return dashboard;
    }
    private JPanel shortcut(String page, String title, String subtitle, Color color) {
        JPanel card = new JPanel(new BorderLayout(0, 14));
        card.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(4, 0, 0, 0, color), BorderFactory.createEmptyBorder(20, 22, 20, 22)));
        JPanel copy = vertical(); copy.add(AppTheme.label(title, 21, color)); copy.add(Box.createVerticalStrut(12));
        copy.add(AppTheme.label(subtitle, 13, AppTheme.MUTED)); card.add(copy, BorderLayout.CENTER);
        JButton open = new JButton("Open " + page + "  →"); open.setForeground(color);
        open.addActionListener(e -> showPage(page)); card.add(open, BorderLayout.SOUTH); return card;
    }
    void addPage(String name, String subtitle, JComponent component) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14)); wrapper.add(component);
        JScrollPane viewport = new JScrollPane(wrapper); viewport.setBorder(null);
        viewport.getVerticalScrollBar().setUnitIncrement(20);
        pages.add(viewport, name);
        JButton link = new JButton(name); link.setHorizontalAlignment(SwingConstants.LEFT);
        link.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44)); link.setPreferredSize(new Dimension(165, 44));
        link.setName("nav" + name.replace(" ", "")); link.putClientProperty("pageSubtitle", subtitle);
        link.setBackground(AppTheme.NAVY); link.setForeground(new Color(202, 215, 237));
        link.addActionListener(e -> showPage(name)); links.put(name, link);
        navigation.add(link); navigation.add(Box.createVerticalStrut(8));
    }
    void showPage(String name) {
        if (!signedIn || !links.containsKey(name)) return;
        currentPage = name; pageLayout.show(pages, name); heading.setText(name);
        description.setText((String) links.get(name).getClientProperty("pageSubtitle"));
        links.forEach((key, button) -> { button.setBackground(key.equals(name) ? AppTheme.BLUE : AppTheme.NAVY); button.setForeground(Color.WHITE); });
    }
    void setSession(UserProfile profile, boolean busy) {
        boolean active = profile != null;
        progress.setVisible(busy); progress.setIndeterminate(busy);
        sessionControls.setVisible(active);
        if (active) {
            welcome.setText("Welcome back, " + profile.username());
            role.setText(profile.role().toString().replace('_', ' ') + "  /  YOUR WORKSPACE");
        } else { welcome.setText("Welcome back"); role.setText(""); }
        boolean changed = active != signedIn; signedIn = active;
        rootLayout.show(roots, active ? "workspace" : "login");
        if (active && changed) showPage("Dashboard");
        links.values().forEach(button -> button.setEnabled(active));
    }
    boolean isWorkspaceVisible() { return signedIn; }
    String currentPage() { return currentPage; }
    void showFeedback(String text) { feedback.setText(text); feedback.setCaretPosition(0); }
    private void openActivityDialog() {
        JTextArea detail = new JTextArea(feedback.getText(), 5, 50);
        detail.setEditable(false); detail.setLineWrap(true); detail.setWrapStyleWord(true);
        JOptionPane.showMessageDialog(this, new JScrollPane(detail), "Latest connection activity", JOptionPane.INFORMATION_MESSAGE);
    }
    private static JPanel vertical() {
        JPanel panel = new JPanel(); panel.setOpaque(false); panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS)); return panel;
    }
    private static final class GradientPanel extends JPanel {
        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setPaint(new GradientPaint(0, 0, new Color(36, 81, 202), getWidth(), getHeight(), new Color(0, 137, 123)));
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 24, 24);
            g.setColor(new Color(255, 255, 255, 16));
            g.fillOval(getWidth() - 200, -70, 330, 330); g.fillOval(-80, getHeight() - 180, 300, 300); g.dispose();
        }
    }
}
