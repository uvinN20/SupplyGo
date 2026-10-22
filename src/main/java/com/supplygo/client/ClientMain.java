package com.supplygo.client;

import java.awt.GraphicsEnvironment;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import com.supplygo.config.NetworkConfig;
import com.supplygo.ui.ClientWindow;

/** Starts the desktop frontend on Swing's event dispatch thread. */
public final class ClientMain {
    private ClientMain() {
    }

    public static void main(String[] args) {
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("SupplyGo client requires a graphical desktop. Run it on Windows or a Linux desktop.");
            System.exit(1);
            return;
        }

        final NetworkConfig config;
        try {
            config = NetworkConfig.load();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            System.err.println("Cannot launch SupplyGo client: " + exception.getMessage());
            System.exit(1);
            return;
        }

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception exception) {
                System.err.println("Using default Swing appearance: " + exception.getMessage());
            }
            ClientWindow.create(config).setVisible(true);
        });
    }
}
