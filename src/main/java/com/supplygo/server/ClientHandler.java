package com.supplygo.server;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import com.supplygo.protocol.FrameIO;
import com.supplygo.protocol.MessageType;
import com.supplygo.protocol.ProtocolCodec;
import com.supplygo.protocol.ProtocolException;
import com.supplygo.protocol.Request;
import com.supplygo.protocol.Response;

/** One handler owns each accepted connection and processes its requests in order. */
public final class ClientHandler implements Runnable {
    private final Socket socket;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try (socket;
                var input = new BufferedInputStream(socket.getInputStream());
                var output = new BufferedOutputStream(socket.getOutputStream())) {
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
                    String message = request.type() == MessageType.PING ? "PONG" : "BYE";
                    FrameIO.write(output, ProtocolCodec.encode(Response.ok(request.requestId(), message)));
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
