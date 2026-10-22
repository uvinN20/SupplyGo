package com.supplygo.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import com.supplygo.client.SupplyGoTcpClient;
import com.supplygo.config.NetworkConfig;

/** All widget updates happen on the EDT; all socket exchanges happen in SwingWorker. */
public final class ClientWindow extends JFrame {
    private final JTextField host;
    private final JSpinner port;
    private final JLabel status = new JLabel("Offline");
    private final JButton connect = new JButton("Connect");
    private final JButton ping = new JButton("Send Ping");
    private final JButton disconnect = new JButton("Disconnect");
    private final JTextArea log = new JTextArea(10, 52);
    private SupplyGoTcpClient client;
    private boolean connected;
    private boolean busy;

    private ClientWindow(NetworkConfig config) {
        super("SupplyGo | Desktop Client");
        host = new JTextField(config.serverHost(), 22);
        host.setName("serverHost");
        port = new JSpinner(new SpinnerNumberModel(config.tcpPort(), 1, 65535, 1));
        port.setEditor(new JSpinner.NumberEditor(port, "0"));
        port.setName("tcpPort");
        connect.setName("connect");
        ping.setName("ping");
        disconnect.setName("disconnect");
        status.setName("connectionStatus");
        log.setName("connectionLog");

        JPanel content = new JPanel(new BorderLayout(0, 20));
        content.setBackground(new Color(246, 248, 252));
        content.setBorder(BorderFactory.createEmptyBorder(28, 32, 28, 32));
        JPanel heading = new JPanel(new GridLayout(0, 1, 0, 6));
        heading.setOpaque(false);
        JLabel title = new JLabel("SupplyGo");
        title.setForeground(new Color(27, 76, 132));
        title.setFont(title.getFont().deriveFont(Font.BOLD, 30f));
        heading.add(title);
        heading.add(new JLabel("Connect smarter. Deliver faster."));
        content.add(heading, BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(0, 14));
        body.setOpaque(false);
        JPanel endpoint = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        endpoint.add(new JLabel("Server"));
        endpoint.add(host);
        endpoint.add(new JLabel("TCP port"));
        endpoint.add(port);
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        controls.add(connect);
        controls.add(ping);
        controls.add(disconnect);
        controls.add(status);
        JPanel connectionPanel = new JPanel(new BorderLayout(0, 8));
        connectionPanel.add(endpoint, BorderLayout.NORTH);
        connectionPanel.add(controls, BorderLayout.SOUTH);
        body.add(connectionPanel, BorderLayout.NORTH);
        log.setEditable(false);
        log.setLineWrap(true);
        log.setWrapStyleWord(true);
        JScrollPane scroll = new JScrollPane(log);
        scroll.setBorder(BorderFactory.createTitledBorder("Connection activity"));
        body.add(scroll, BorderLayout.CENTER);
        content.add(body, BorderLayout.CENTER);
        content.add(new JLabel("TCP connection demo • Accounts, inventory and UDP chat arrive in later tasks."), BorderLayout.SOUTH);
        setContentPane(content);

        connect.addActionListener(event -> connect());
        ping.addActionListener(event -> perform("Sending PING…", client::ping, true));
        disconnect.addActionListener(event -> perform("Disconnecting…", client::disconnect, false));
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent event) {
                if (client != null) {
                    client.close();
                }
            }
        });
        updateControls();
        append("Start the backend, then connect to send a PING request.");
        pack();
        setMinimumSize(new Dimension(getWidth(), getHeight()));
        setLocationRelativeTo(null);
    }

    public static JFrame create(NetworkConfig config) {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Create Swing windows on the event dispatch thread");
        }
        return new ClientWindow(config);
    }

    private void connect() {
        try {
            port.commitEdit();
            client = new SupplyGoTcpClient(host.getText(), ((Number) port.getValue()).intValue());
        } catch (Exception exception) {
            append("Enter a server address and a port from 1 to 65535.");
            return;
        }
        SupplyGoTcpClient attempt = client;
        perform("Connecting…", () -> {
            attempt.connect();
            return attempt.ping();
        }, true);
    }

    private void perform(String progress, Callable<String> operation, boolean staysConnected) {
        busy = true;
        status.setText(progress);
        updateControls();
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return operation.call();
            }

            @Override
            protected void done() {
                try {
                    String message = get();
                    connected = staysConnected;
                    append("Server: " + message);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    failed("Connection operation interrupted");
                } catch (ExecutionException exception) {
                    String message = exception.getCause().getMessage();
                    failed(message == null ? "Connection failed" : message);
                } finally {
                    busy = false;
                    status.setText(connected ? "Connected" : "Offline");
                    updateControls();
                }
            }
        }.execute();
    }

    private void failed(String message) {
        connected = false;
        if (client != null) {
            client.close();
        }
        append("Connection error: " + message);
    }

    private void updateControls() {
        host.setEnabled(!busy && !connected);
        port.setEnabled(!busy && !connected);
        connect.setEnabled(!busy && !connected);
        ping.setEnabled(!busy && connected);
        disconnect.setEnabled(!busy && connected);
    }

    private void append(String message) {
        log.append(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "  " + message + "\n");
        if (log.getLineCount() > 200) {
            try {
                log.replaceRange("", 0, log.getLineEndOffset(log.getLineCount() - 200));
            } catch (javax.swing.text.BadLocationException exception) {
                log.setText("");
            }
        }
        log.setCaretPosition(log.getDocument().getLength());
    }
}
