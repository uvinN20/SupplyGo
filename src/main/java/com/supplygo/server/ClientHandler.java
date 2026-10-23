package com.supplygo.server;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import javax.net.ssl.SSLSocket;
import com.supplygo.service.AuthService;
import com.supplygo.protocol.FrameIO;
import com.supplygo.protocol.MessageType;
import com.supplygo.protocol.ProtocolCodec;
import com.supplygo.protocol.ProtocolException;
import com.supplygo.protocol.Request;
import com.supplygo.protocol.Response;

/** One handler owns each accepted connection and processes its requests in order. */
public final class ClientHandler implements Runnable {
    private final Socket socket;
    private com.supplygo.service.BusinessProfileService businessProfiles;
    private final AuthService authentication;
    private final com.supplygo.service.InventoryService inventory;

    public ClientHandler(Socket socket) {
        this(socket, null);
    }

    public ClientHandler(Socket socket, AuthService authentication) {
        this(socket, authentication, null);
    }

    public ClientHandler(Socket socket, AuthService authentication, com.supplygo.service.InventoryService inventory) {
        this.inventory = inventory;
        this.socket = socket;
        this.authentication = authentication;
    }

    public ClientHandler(Socket socket, AuthService auth, com.supplygo.service.InventoryService inventory,
            com.supplygo.service.BusinessProfileService profiles) {
        this(socket, auth, inventory); this.businessProfiles = profiles;
    }

    @Override
    public void run() {
        try (socket;
                var input = new BufferedInputStream(socket.getInputStream());
                var output = new BufferedOutputStream(socket.getOutputStream())) {
            if (socket instanceof SSLSocket secure) {
                secure.setSoTimeout(5000);
                secure.startHandshake();
                secure.setSoTimeout(SupplyGoTcpServer.IDLE_TIMEOUT_MS);
            }
            RequestRouter router = new RequestRouter(authentication, socket instanceof SSLSocket, inventory, businessProfiles);
            while (!socket.isClosed()) {
                final String frame;
                try {
                    frame = FrameIO.read(input);
                } catch (ProtocolException exception) {
                    FrameIO.write(output, ProtocolCodec.encode(Response.error(null, exception.code(), exception.getMessage())));
                    break; // Invalid framing cannot be safely resynchronized.
                } catch (SocketTimeoutException exception) {
                    FrameIO.write(output, ProtocolCodec.encode(Response.error(null, "IDLE_TIMEOUT", "Connection read timeout")));
                    break;
                }
                if (frame == null) {
                    break;
                }
                try {
                    Request request = ProtocolCodec.decodeRequest(frame);
                    FrameIO.write(output, ProtocolCodec.encode(router.handle(request)));
                    if (request.type() == MessageType.QUIT) {
                        break;
                    }
                } catch (ProtocolException exception) {
                    FrameIO.write(output, ProtocolCodec.encode(
                            Response.error(exception.requestId(), exception.code(), exception.getMessage())));
                }
            }
        } catch (IOException exception) {
            if (!socket.isClosed()) {
                System.err.println("TCP client disconnected: " + exception.getMessage());
            }
        }
    }
}
