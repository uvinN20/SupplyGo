package com.supplygo.server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import com.supplygo.service.AuthService;

/** Blocking accept loop with at most 32 active clients and no waiting socket queue. */
public final class SupplyGoTcpServer implements AutoCloseable {
    public static final int MAX_CLIENTS = 32;
    public static final int IDLE_TIMEOUT_MS = 60_000;
    private final ServerSocket listener;
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(0, MAX_CLIENTS, 30, TimeUnit.SECONDS,
            new SynchronousQueue<>(), Thread.ofPlatform().name("supplygo-client-", 1).factory());
    private final Set<Socket> clients = new HashSet<>();
    private final Object lifecycle = new Object();
    private final AtomicBoolean started = new AtomicBoolean();
    private volatile boolean closed;
    private final AuthService authentication;
    private final com.supplygo.service.InventoryService inventory;

    /** Port zero requests an operating-system-assigned port, useful for local tests. */
    public SupplyGoTcpServer(String bindAddress, int port) throws IOException {
        this(bindAddress, port, null, null);
    }

    public SupplyGoTcpServer(String bindAddress, int port, SSLContext tls, AuthService authentication) throws IOException {
        this(bindAddress, port, tls, authentication, null);
    }

    public SupplyGoTcpServer(String bindAddress, int port, SSLContext tls, AuthService authentication,
            com.supplygo.service.InventoryService inventory) throws IOException {
        if (bindAddress == null || bindAddress.isBlank()) {
            throw new IllegalArgumentException("Server bind address must not be blank");
        }
        this.authentication = authentication;
        this.inventory = inventory;
        listener = tls == null ? new ServerSocket() : tls.getServerSocketFactory().createServerSocket();
        if (listener instanceof SSLServerSocket secure) secure.setEnabledProtocols(new String[]{"TLSv1.3", "TLSv1.2"});
        try {
            listener.bind(new InetSocketAddress(bindAddress.strip(), port));
        } catch (IOException | RuntimeException exception) {
            listener.close();
            throw exception;
        }
    }

    public int port() { return listener.getLocalPort(); }

    public void serve() throws IOException {
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("Server accept loop can only run once");
        }
        try {
            while (!closed) {
                Socket socket = listener.accept();
                synchronized (lifecycle) {
                    if (closed) {
                        closeSocket(socket);
                        break;
                    }
                    try {
                        socket.setSoTimeout(IDLE_TIMEOUT_MS);
                        socket.setTcpNoDelay(true);
                        clients.add(socket);
                        workers.execute(() -> {
                            try {
                                new ClientHandler(socket, this.authentication, this.inventory).run();
                            } finally {
                                synchronized (lifecycle) {
                                    clients.remove(socket);
                                }
                            }
                        });
                    } catch (RejectedExecutionException exception) {
                        clients.remove(socket);
                        closeSocket(socket);
                        System.err.println("TCP client limit reached (" + MAX_CLIENTS + ")");
                    } catch (IOException exception) {
                        clients.remove(socket);
                        closeSocket(socket);
                        System.err.println("TCP connection rejected: " + exception.getMessage());
                    }
                }
            }
        } catch (SocketException exception) {
            if (!closed) {
                throw exception;
            }
        } finally {
            close();
        }
    }

    /** Closing the listener unblocks accept; closing client sockets unblocks reads. */
    @Override
    public void close() {
        synchronized (lifecycle) {
            if (closed) {
                return;
            }
            closed = true;
            try {
                listener.close();
            } catch (IOException exception) {
                System.err.println("Could not close TCP listener: " + exception.getMessage());
            }
            clients.forEach(SupplyGoTcpServer::closeSocket);
            workers.shutdown();
        }
    }

    private static void closeSocket(Socket socket) {
        try {
            socket.close();
        } catch (IOException exception) {
            System.err.println("Could not close TCP connection: " + exception.getMessage());
        }
    }
}
