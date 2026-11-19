package com.supplygo.ui;

import java.awt.*;
import javax.swing.*;
import com.formdev.flatlaf.FlatLightLaf;

/** Shared palette and accessible, scalable Swing defaults. */
public final class AppTheme {
    static final Color NAVY = new Color(19, 32, 61);
    static final Color BLUE = new Color(43, 96, 235);
    static final Color GREEN = new Color(0, 125, 105);
    static final Color INK = new Color(31, 46, 73);
    static final Color MUTED = new Color(87, 103, 128);
    static final Color BACKGROUND = new Color(242, 246, 252);
    private AppTheme() {}
    public static void install() {
        FlatLightLaf.setup();
        UIManager.put("defaultFont", new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        UIManager.put("Panel.background", Color.WHITE);
        UIManager.put("Label.foreground", INK);
        UIManager.put("Component.arc", 12);
        UIManager.put("Button.arc", 12);
        UIManager.put("TextComponent.arc", 12);
        UIManager.put("Component.focusColor", BLUE);
        UIManager.put("Button.margin", new Insets(9, 14, 9, 14));
        UIManager.put("Table.rowHeight", 34);
        UIManager.put("Table.selectionBackground", new Color(222, 234, 255));
        UIManager.put("Table.selectionForeground", INK);
        UIManager.put("TableHeader.background", new Color(235, 241, 250));
        UIManager.put("ScrollBar.width", 10);
    }
    static void primary(JButton button) { button.setBackground(BLUE); button.setForeground(Color.WHITE); }
    static JLabel label(String text, int size, Color color) {
        JLabel label = new JLabel(text);
        label.putClientProperty("html.disable", true);
        label.setForeground(color);
        label.setFont(label.getFont().deriveFont(size >= 20 ? Font.BOLD : Font.PLAIN, (float) size));
        return label;
    }
}
