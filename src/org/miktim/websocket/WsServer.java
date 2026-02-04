/*
 * WsServer. WebSocket Server, MIT (c) 2020-2026 miktim@mail.ru
 *
 * Accepts sockets, creates and starts connection threads.
 *
 * Created: 2020-03-09
 */
package org.miktim.websocket;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * WebSocket server for insecure or TLS connections.
 */
public class WsServer extends Thread {

    private int serverStatus = WsStatus.IS_INACTIVE;
    private Throwable serverError = null;

    private final boolean isSecure;
    private final WsParameters wsp;
    private final ServerSocket serverSocket;
    private final WsConnection.Handler connectionHandler; // connection handler
    List<WsServer> servers = null;
    private final List<WsConnection> connections
            = Collections.synchronizedList(new ArrayList<WsConnection>());

    WsServer(ServerSocket ss, WsConnection.Handler h, boolean secure, WsParameters wsp) {
        this.serverSocket = ss;
        this.connectionHandler = h;
        this.isSecure = secure;
        this.wsp = wsp;
    }

    /**
     * Waiting for the server to be ready to accept connections.
     *
     * @return this server
     * @since 5.0
     */
    public WsServer ready() {
        synchronized (this) {
            if (serverStatus == WsStatus.IS_INACTIVE) {
                try {
                    this.wait(wsp.connectionSoTimeout); //
                } catch (InterruptedException ex) {
                }
            }
        }
        return this;
    }

    /**
     * Checks the server for TLS connections.
     *
     * @return true if is it.
     */
    public boolean isSecure() {
        return isSecure;
    }

    /**
     * Checks if the server is active.
     *
     * @return true if is it.
     * @since 4.3
     */
    public boolean isActive() {
        return isAlive() && serverStatus == WsStatus.IS_OPEN; //
    }

    /**
     * Returns server error or null.
     */
    public Throwable getError() {
        return serverError;
    }

    /**
     * Returns listening port number.
     *
     */
    public int getPort() {
        return serverSocket.getLocalPort();
    }

    /**
     * Returns a clone of the server-side connection parameters.
     */
    public WsParameters getParameters() {
        return wsp.deepClone();
    }

    /**
     * Returns the ServerSocket of this server.
     *
     */
    public ServerSocket getServerSocket() {
        return serverSocket;
    }

    /**
     * Lists serve-side connections.
     *
     * @return array of server-side connections.
     */
    public WsConnection[] listConnections() {
        return connections.toArray(new WsConnection[0]);
    }

    /**
     * Stops the server and closes all server-side connections.
     * <p>
     * Stops listening. Connections are closed with the 1001 (GOING_AWAY) status
     * code. The server is removed from the list of active WebSocket servers.
     * Calls the onStop method of the server handler.
     * </p>
     *
     * @see WsStatus
     * @since 4.3
     */
    public void stopServer() {
        stopServer("Shutdown");
    }

    /**
     * Stops the server and closes all server-side connections with specified reason.
     * <p>
     * Stops listening. Connections are closed with the 1001 (GOING_AWAY) status
     * code. The server is removed from the list of active WebSocket servers.
     * Calls the onStop method of the server handler.
     * </p>
     *
     * @param reason connections close reason. Reason max length is 123 BYTES.
     * @see WsStatus
     * @since 4.3
     */
    public void stopServer(String reason) {
        stopServer(WsStatus.GOING_AWAY, reason);
    }

    void stopServer(int code, String reason) {
        if (serverStatus == WsStatus.IS_OPEN) {
            serverStatus = code;
            closeServerSocket();
// close associated connections
            for (WsConnection conn : listConnections()) {
                conn.ready().close(code, reason);
            }
        }
    }

    void closeServerSocket() {
        try {
            serverSocket.close();
        } catch (IOException ignore) {
        }
    }

    @Override
    public void start() {
        servers.add(this); // add to the list of WebSocket servers
        super.start();
    }

    @Override
    public void run() {
        setName("WsServer" + getName());
        serverStatus = WsStatus.IS_OPEN;
        try {
            synchronized (this) {
                if (connectionHandler instanceof ServerHandler) {
                    ((ServerHandler) connectionHandler).onStart(this, wsp);
                }
                this.notifyAll();
            }
            while (true) {//
// serverSocket SO_TIMEOUT = 0 by WebSocket creator
                Socket socket = serverSocket.accept();
                if (wsp.backlog > -1 && connections.size() >= wsp.backlog) {
                    try {
                        (new HttpHead())
                                .setStartLine("HTTP/1.1 429 Too Many Requests")
                                .set("Retry-After", "10")
                                .write(socket.getOutputStream());
                        socket.close();
                    } catch (IOException ignore) {
                    }
                    continue;
                }
                socket.setSoTimeout(wsp.handshakeSoTimeout);
                WsConnection conn
                        = new WsConnection(socket, connectionHandler, wsp, isSecure);
// set a link to the server's connection list
                conn.connections = this.connections;
                conn.start(); // start connection Thread
            }
        } catch (Throwable err) {
            if (serverStatus == WsStatus.IS_OPEN) {
                serverError = err;
                stopServer(WsStatus.INTERNAL_ERROR, "Abnormal shutdown");
            }
        }
        closeServerSocket();
        servers.remove(this);
        try {
            if (connectionHandler instanceof ServerHandler) {
                ((ServerHandler) connectionHandler).onStop(this, serverError);
            }
        } catch (Throwable err) {
            serverError = err;
            err.printStackTrace();
        }

    }

    /**
     * WebSocket server event handler.
     */
    interface ServerHandler {

        /**
         * Called when the server is ready to accept connections.
         *
         * @param server WebSocket server instance.
         * @param wsp server-side connection parameters.
         * @since 5.0
         */
        public void onStart(WsServer server, WsParameters wsp);

        /**
         * Called after the ServerSocket is closed.
         *
         * @param server WebSocket server instance.
         * @param err error cause or null.
         */
        public void onStop(WsServer server, Throwable err);
    }

    /**
     * WebSocket server event handler inherits the connection handler.
     */
    public interface Handler extends WsConnection.Handler, ServerHandler { };

}
