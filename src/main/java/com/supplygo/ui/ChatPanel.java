package com.supplygo.ui;

import java.awt.*;
import java.util.Objects;
import java.util.concurrent.*;
import javax.swing.*;
import com.supplygo.client.UdpChatClient;
import com.supplygo.model.UserProfile;

/** All Swing mutations run on the EDT; socket operations and acknowledgements run in workers. */
final class ChatPanel extends JPanel {
    private final ClientWindow window;
    private final JTextArea history = new JTextArea(8, 58);
    private final JTextField message = new JTextField(36);
    private final JButton join = button("Join room", "joinChat"), leave = button("Leave", "leaveChat"), send = button("Send", "sendChat");
    private final JLabel status = new JLabel("Sign in, then join the shared room.");
    private final ArrayBlockingQueue<String> inbox = new ArrayBlockingQueue<>(200);
    private final Timer display = new Timer(200, event -> drain());
    private final Timer heartbeat = new Timer(10000, event -> heartbeat());
    private UdpChatClient chat;
    private UserProfile identity;
    private boolean busy, outerBusy, disposed;
    private long generation;
    ChatPanel(ClientWindow window) {
        super(new BorderLayout(0, 8)); this.window = window;
        history.setName("chatHistory"); history.setEditable(false); history.setLineWrap(true); history.setWrapStyleWord(true);
        message.setName("chatMessage"); status.setName("chatStatus"); status.putClientProperty("html.disable", Boolean.TRUE);
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT)); top.add(join); top.add(leave);
        top.add(new JLabel("Shared room • UDP messages are not encrypted")); add(top, BorderLayout.NORTH);
        add(new JScrollPane(history), BorderLayout.CENTER);
        JPanel bottom = new JPanel(new BorderLayout(0, 4)); JPanel input = new JPanel(new FlowLayout(FlowLayout.LEFT));
        input.add(message); input.add(send); bottom.add(input, BorderLayout.NORTH); bottom.add(status, BorderLayout.SOUTH); add(bottom, BorderLayout.SOUTH);
        join.addActionListener(event -> join()); leave.addActionListener(event -> leave()); send.addActionListener(event -> send());
        message.addActionListener(event -> { if (send.isEnabled()) send(); });
        display.start(); heartbeat.start(); controls();
    }
    private static JButton button(String text, String name) { JButton button = new JButton(text); button.setName(name); return button; }
    void setContext(UserProfile profile, boolean outerBusy) {
        this.outerBusy = outerBusy;
        if (!Objects.equals(profile, identity)) {
            stop(); identity = profile; inbox.clear(); history.setText(""); message.setText("");
            status.setText(profile == null ? "Sign in, then join the shared room." : "Join the room to chat with other signed-in users.");
        }
        controls();
    }
    private void controls() {
        boolean ready = identity != null && !busy && !outerBusy && !disposed;
        join.setEnabled(ready && chat == null); leave.setEnabled(ready && chat != null);
        send.setEnabled(ready && chat != null); message.setEnabled(ready && chat != null);
    }
    private void join() {
        long attempt = ++generation;
        var tcp = window.currentClient();
        window.perform("Authorizing UDP chat…", tcp::openChat, grant -> {
            if (disposed || attempt != generation || identity == null) return;
            busy = true; controls(); status.setText("Joining UDP room…");
            new SwingWorker<UdpChatClient, Void>() {
                @Override protected UdpChatClient doInBackground() throws Exception {
                    var candidate = new UdpChatClient(tcp.chatAddress(), grant, value -> {
                        // The generation check is performed on the EDT when draining.
                        inbox.offer(attempt + "\t" + value.time() + "  " + value.sender() + " [" + value.role() + "]: " + value.text());
                    });
                    try { candidate.join(); return candidate; }
                    catch (Exception error) { candidate.close(); throw error; }
                }
                @Override protected void done() {
                    try {
                        var candidate = get();
                        if (disposed || attempt != generation || identity == null) candidate.close();
                        else { chat = candidate; status.setText("Joined shared room. Messages: up to 240 UTF-8 bytes."); }
                    } catch (Exception error) { if (attempt == generation) status.setText("Could not join UDP chat. Check the server address, UDP port and firewall."); }
                    finally { if (attempt == generation) busy = false; controls(); }
                }
            }.execute();
        });
    }
    private void leave() {
        stop(); status.setText("Left room.");
        window.perform("Closing chat access…", window.currentClient()::closeChat, value -> {});
    }
    private void send() {
        String text = message.getText();
        try { com.supplygo.protocol.UdpChatProtocol.text(text); }
        catch (IllegalArgumentException error) { status.setText(error.getMessage()); return; }
        run(() -> chat.send(text), false, () -> { if (message.getText().equals(text)) message.setText(""); });
    }
    private void heartbeat() {
        if (chat == null || busy || outerBusy || disposed || identity == null) return;
        window.perform("Checking chat session…", window.currentClient()::profile, value -> {
            if (chat != null) run(() -> { chat.heartbeat(); return ""; }, true, () -> {});
        });
    }
    private void run(Callable<String> operation, boolean heartbeat, Runnable success) {
        long attempt = generation; busy = true; controls();
        new SwingWorker<String, Void>() {
            @Override protected String doInBackground() throws Exception { return operation.call(); }
            @Override protected void done() {
                if (attempt != generation || disposed) return;
                try { String result = get(); success.run(); if (!heartbeat) status.setText(result); }
                catch (Exception error) {
                    Throwable cause = error instanceof ExecutionException ? error.getCause() : error;
                    status.setText(cause.getMessage() == null ? "UDP operation failed" : cause.getMessage());
                    if (heartbeat) { stop(); status.setText("UDP room connection lost. Join again to reconnect."); }
                } finally { busy = false; controls(); }
            }
        }.execute();
    }
    private void drain() {
        String value;
        while ((value = inbox.poll()) != null) {
            String prefix = generation + "\t";
            if (!value.startsWith(prefix) || chat == null) continue;
            history.append(value.substring(prefix.length()) + "\n");
            if (history.getLineCount() > 200) {
                try { history.replaceRange("", 0, history.getLineEndOffset(history.getLineCount() - 200)); }
                catch (javax.swing.text.BadLocationException ignored) { history.setText(""); }
            }
        }
        history.setCaretPosition(history.getDocument().getLength());
    }
    private void stop() { generation++; if (chat != null) chat.close(); chat = null; busy = false; inbox.clear(); controls(); }
    void dispose() { disposed = true; stop(); display.stop(); heartbeat.stop(); }
}
