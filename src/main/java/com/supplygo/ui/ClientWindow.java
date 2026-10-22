package com.supplygo.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import com.supplygo.config.NetworkConfig;

/** Minimal desktop shell. Authentication and live networking arrive in later milestones. */
public final class ClientWindow {
    private ClientWindow() {
    }

    public static JFrame create(NetworkConfig config) {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Create Swing windows on the event dispatch thread");
        }

        JPanel content = new JPanel(new BorderLayout(0, 24));
        content.setBackground(new Color(246, 248, 252));
        content.setBorder(BorderFactory.createEmptyBorder(32, 36, 32, 36));

        JPanel heading = new JPanel(new GridLayout(0, 1, 0, 8));
        heading.setOpaque(false);
        JLabel title = new JLabel("SupplyGo");
        title.setForeground(new Color(27, 76, 132));
        title.setFont(title.getFont().deriveFont(Font.BOLD, 30f));
        heading.add(title);
        heading.add(new JLabel("Connect smarter. Deliver faster."));
        content.add(heading, BorderLayout.NORTH);

        JPanel details = new JPanel(new GridLayout(0, 1, 0, 12));
        details.setBackground(Color.WHITE);
        details.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(220, 226, 235)),
                BorderFactory.createEmptyBorder(24, 24, 24, 24)));
        JLabel status = new JLabel("Project foundation | Offline");
        status.setFont(status.getFont().deriveFont(Font.BOLD, 16f));
        details.add(status);
        details.add(new JLabel("Server address: " + config.serverHost()));
        details.add(new JLabel("TCP port: " + config.tcpPort() + "     UDP port: " + config.udpPort()));
        details.add(new JLabel("This window is not connected to a server yet."));
        content.add(details, BorderLayout.CENTER);
        content.add(new JLabel("Next: connect this desktop client to the Java TCP backend."), BorderLayout.SOUTH);

        JFrame window = new JFrame("SupplyGo | Desktop Client");
        window.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        window.setContentPane(content);
        window.pack();
        window.setMinimumSize(new Dimension(window.getWidth(), window.getHeight()));
        window.setLocationRelativeTo(null);
        return window;
    }
}
