package com.supplygo.ui;
import static org.junit.jupiter.api.Assertions.*;
import java.awt.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import com.supplygo.model.*;

class RoutePanelTest {
    Component find(Container root,String name){for(Component c:root.getComponents()){if(name.equals(c.getName()))return c;if(c instanceof Container child){Component found=find(child,name);if(found!=null)return found;}}return null;}
    @Test void controlsRespectRolesAndLogoutClearsInputsAndResult() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var panel=new RoutePanel(null);
            for(Role role:Role.values()) {
                panel.setContext(new UserProfile("account",role),false);
                assertTrue(find(panel,"findRoute").isEnabled());assertEquals(role==Role.ADMIN||role==Role.PRODUCTION_MANAGER,find(panel,"saveRoad").isEnabled());
            }
            ((JTextField)find(panel,"routeFrom")).setText("A");((JTextArea)find(panel,"routeResult")).setText("A → B");
            panel.setContext(null,false);assertFalse(find(panel,"findRoute").isEnabled());assertEquals("",((JTextField)find(panel,"routeFrom")).getText());assertEquals("",((JTextArea)find(panel,"routeResult")).getText());
            panel.setContext(new UserProfile("admin",Role.ADMIN),true);assertFalse(find(panel,"saveRoad").isEnabled());
        });
    }
}
