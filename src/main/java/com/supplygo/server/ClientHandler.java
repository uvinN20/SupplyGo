package com.supplygo.server;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;

import javax.net.ssl.SSLSocket;

import com.supplygo.protocol.FrameIO;
import com.supplygo.protocol.MessageType;
import com.supplygo.protocol.ProtocolCodec;
import com.supplygo.protocol.ProtocolException;
import com.supplygo.protocol.Request;
import com.supplygo.protocol.Response;
import com.supplygo.service.AuthService;
import com.supplygo.service.InventoryService;
import com.supplygo.service.OrderService;

/** One handler owns each accepted connection and processes its requests in order. */
public final class ClientHandler implements Runnable {
    private final Socket socket;
    private final UdpChatServer chat;
    private final AuthService authentication;
    private final InventoryService inventory;
    private final OrderService orders;
    private final com.supplygo.service.BusinessProfileService businessProfiles;

    public ClientHandler(Socket socket) {
        this(socket, null);
    }

    public ClientHandler(Socket socket, AuthService authentication) {
        this(socket, authentication, null);
    }

    public ClientHandler(Socket socket, AuthService authentication, InventoryService inventory) {
        this(socket, authentication, inventory, null);
    }

    public ClientHandler(Socket socket, AuthService authentication, InventoryService inventory,
            OrderService orders) {
        this(socket, authentication, inventory, orders, null);
    }

    public ClientHandler(Socket socket, AuthService authentication, InventoryService inventory, OrderService orders,
            com.supplygo.service.BusinessProfileService businessProfiles) {
        this(socket, authentication, inventory, orders, businessProfiles, null);
    }

    public ClientHandler(Socket socket, AuthService authentication, InventoryService inventory, OrderService orders,
            com.supplygo.service.BusinessProfileService businessProfiles, UdpChatServer chat) {
        this.chat = chat;
        this.businessProfiles = businessProfiles;
        this.orders = orders;
        this.inventory = inventory;
        this.socket = socket;
        this.authentication = authentication;
    }

    @Override
    public void run() {
        try (RequestRouter router = new RequestRouter(authentication, socket instanceof SSLSocket, inventory, orders, businessProfiles, chat, socket.getInetAddress());
                socket;
                var input = new BufferedInputStream(socket.getInputStream());
                var output = new BufferedOutputStream(socket.getOutputStream())) {
            if (socket instanceof SSLSocket secure) {
                secure.setSoTimeout(5000);
                secure.startHandshake();
                secure.setSoTimeout(SupplyGoTcpServer.IDLE_TIMEOUT_MS);
            }
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
