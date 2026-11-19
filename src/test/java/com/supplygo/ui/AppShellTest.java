package com.supplygo.ui;

import com.supplygo.model.Role;
import com.supplygo.model.UserProfile;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AppShellTest {
    @Test void sessionTransitionsGateNavigationAndResetDashboard() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AppTheme.install();
            AppShell shell = new AppShell(new JPanel(), new JPanel(), new JPanel(), new JLabel("Offline"));
            shell.addPage("Routes", "Plan a route", new JPanel());
            shell.setSession(null, false);
            shell.showPage("Routes");
            assertFalse(shell.isWorkspaceVisible());
            assertEquals("Dashboard", shell.currentPage());
            shell.setSession(new UserProfile("seller", Role.SELLER), false);
            assertTrue(shell.isWorkspaceVisible());
            shell.showPage("Routes");
            assertEquals("Routes", shell.currentPage());
            shell.setSession(new UserProfile("seller", Role.SELLER), true);
            assertEquals("Routes", shell.currentPage());
            shell.setSession(null, false);
            assertFalse(shell.isWorkspaceVisible());
            shell.setSession(new UserProfile("admin", Role.ADMIN), false);
            assertEquals("Dashboard", shell.currentPage());
        });
    }
}
