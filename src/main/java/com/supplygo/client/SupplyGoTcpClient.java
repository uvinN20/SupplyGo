package com.supplygo.client;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicReference;
import com.supplygo.protocol.FrameIO;
import com.supplygo.protocol.MessageType;
import com.supplygo.protocol.ProtocolCodec;
import com.supplygo.protocol.ProtocolException;
import com.supplygo.protocol.Request;
import com.supplygo.protocol.Response;

/** One connection, with sequential exchanges. Use a new instance when reconnecting. */
public final class SupplyGoTcpClient implements AutoCloseable {
    public static final int CONNECT_TIMEOUT_MS = 3000;
    public static final int RESPONSE_TIMEOUT_MS = 5000;
    private final String host;
    private final int port;
    private final AtomicReference<Socket> connection = new AtomicReference<>();
    private volatile boolean closed;
    private BufferedInputStream input;
    private BufferedOutputStream output;

    public SupplyGoTcpClient(String host, int port) {
        if (host == null || host.isBlank() || port < 1 || port > 65535) {
            throw new IllegalArgumentException("Provide a server address and port from 1 to 65535");
        }
        this.host = host.strip();
        this.port = port;
    }

    public synchronized void connect() throws IOException {
        if (closed || connection.get() != null) {
            throw new IOException("Client already connected or closed; create a new client to reconnect");
        }
        Socket socket = new Socket();
        connection.set(socket);
        try {
            if (closed) {
                throw new IOException("Client was closed");
            }
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(RESPONSE_TIMEOUT_MS);
            socket.setTcpNoDelay(true);
            input = new BufferedInputStream(socket.getInputStream());
            output = new BufferedOutputStream(socket.getOutputStream());
        } catch (IOException | RuntimeException exception) {
            close();
            throw exception;
        }
    }

    public synchronized String ping() throws IOException {
        return exchange(MessageType.PING, "PONG");
    }

    public synchronized String disconnect() throws IOException {
        try {
            return exchange(MessageType.QUIT, "BYE");
        } finally {
            close();
        }
    }

    private String exchange(MessageType type, String expectedMessage) throws IOException {
        Socket socket = connection.get();
        if (closed || socket == null || input == null) {
            throw new IOException("Client is not connected");
        }
        try {
            Request request = Request.create(type);
            FrameIO.write(output, ProtocolCodec.encode(request));
            String frame = FrameIO.read(input);
            if (frame == null) {
                throw new EOFException("Server closed the connection");
            }
            Response response = ProtocolCodec.decodeResponse(frame);
            if (response.status() == Response.Status.ERROR) {
                throw new ProtocolException(response.errorCode(), response.requestId(), response.message());
            }
            if (!request.requestId().equals(response.requestId()) || !expectedMessage.equals(response.message())) {
                throw new ProtocolException("UNEXPECTED_RESPONSE", response.requestId(), "Server sent an unexpected response");
            }
            return response.message();
        } catch (IOException exception) {
            close(); // A timeout or invalid reply makes correlation unsafe for later exchanges.
            throw exception;
        }
    }

    /** Does not take the exchange monitor, so closing the UI can cancel a blocked read. */
    @Override
    public void close() {
        closed = true;
        Socket socket = connection.getAndSet(null);
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // Closing is best effort; the client can no longer be used.
            }
        }
    }
}
