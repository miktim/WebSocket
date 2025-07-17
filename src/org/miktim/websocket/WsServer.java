/*
 * WsServer. WebSocket Server, MIT (c) 2020-2025 miktim@mail.ru
 *
 * Accepts sockets, creates and starts connection threads.
 *
 * Created: 2020-03-09
 */
package org.miktim.websocket;

import java.io.IOException;
import java.net.InetAddress;
//import java.io.InterruptedIOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * WebSocket server for cleartext or TLS connections.
 * <p>
 * - Servers are created using a default server handler. You can set your own
 * handler or start the server immediately.
 * The default handler's only action is to call RuntimeException when an error occurs.<br>
 * - If the server is closed normally, all associated connections are closed
 * and it is removed from the list of servers.<br>
 * - If the server is interrupted or an error occurs, it remains in the server list.
 * Server side connections stay alive and can be closed by usual way.
 * </p>
 */
public class WsServer extends Thread {

    static final int IS_OPEN = 1;
    static final int IS_INTERRUPTED = -1;
    static final int IS_CLOSED = -2;

    private int status = 0;

    private Exception error = null;
    private final boolean isSecure;
    private final WsParameters wsp;
    private final ServerSocket serverSocket;
    private final WsConnection.Handler connectionHandler; // connection handler
    List<WsServer> servers = null;
    private final List<WsConnection> connections
            = Collections.synchronizedList(new ArrayList<WsConnection>());

// default handler    
    private Handler serverHandler = new Handler() {
        @Override
        public void onStart(WsServer server) {
        }

        @Override
        public boolean onAccept(WsServer server, WsConnection conn) {
            return true;
        }

        @Override
        public void onStop(WsServer server, Exception error) {
            if (error != null) {
                throw new RuntimeException("Abnormal shutdown.", error);
            }
        }
    };

    WsServer(ServerSocket ss, WsConnection.Handler h, boolean secure, WsParameters wsp) {
        this.serverSocket = ss;
        this.connectionHandler = h;
        this.isSecure = secure;
        this.wsp = wsp;
    }

    /**
     * Checks the server for TLS connections.
     * @return true if is it.
     */
    public boolean isSecure() {
        return isSecure;
    }

    /**
     * Checks if the server is open.
     * @return true if is it.
     */
    public boolean isOpen() {
        return status == IS_OPEN;
    }

    /**
     * Checks if the server is interrupted.
     * @return true if is it.
     */
    @Override
    public boolean isInterrupted() {
        return status == IS_INTERRUPTED;
    }

    /**
     * Returns server error if any.
     * @return null or exception.
     */
    public Exception getError() {
        return error;
    }

    /**
     * Returns listening port.
     * @return listening port number.
     */
    public int getPort() {
        return serverSocket.getLocalPort();
    }

    /**
     * Returns the listening interface address.
     * @return interface address.
     */
    public InetAddress getBindAddress() {
        return serverSocket.getInetAddress();
    }

    /**
     * Returns the server side connection parameters.
     * @return clone of the connection parameters.
     */
    public WsParameters getParameters() {
        return wsp;
    }

    /**
     * Returns ServerSocket.
     * @return ServerSocket.
     */
    public ServerSocket getServerSocket() {
        return serverSocket;
    }

    /**
     * Start server.
     * @return this.
     */
    public WsServer launch() {
        this.start();
        return this;
    }

    /**
     * Lists server side connections.
     * @return array of connections.
     */
    public WsConnection[] listConnections() {
        return connections.toArray(new WsConnection[0]);
    }

    /**
     * Close server with associated connections.
     * <p>
     * Stops listening. Connections
     * are closed with the GOING_AWAY status code. The server is removed from
     * the list of active WebSocket servers. Calls the onStop method of the
     * handler.
     *</p>
     * @see WsStatus
     */
    public void close() {
        close(null);
    }

    /**
     * Close server with associated connections.
     * <p>
     * Stops listening. Connections
     * are closed with the GOING_AWAY status code. The server is removed from
     * the list of active WebSocket servers. Calls the onStop method of the
     * handler.
     * </p>
     *
     * @param closeReason connections close reason. Reason max
     * length is 123 BYTES.
     * @see WsStatus
     */
    synchronized public void close(String closeReason) {
        if (status != IS_CLOSED) {
            status = IS_CLOSED;
            closeServerSocket();

// close associated connections
            for (WsConnection connection : listConnections()) {
                connection.close(WsStatus.GOING_AWAY, closeReason);
            }
            servers.remove(this); // remove from the list of WebSocket servers
        }
    }

    /**
     * Interrupts the server, keeping server connections open.
     * <p>
     * Stops listening.
     * The server remains in the list of servers. Server's connections stay
     * alive. Calls the onStop method of the handler.
     * </p>
     */
    @Override
    synchronized public void interrupt() {
        if (isOpen()) {
            status = IS_INTERRUPTED;
            closeServerSocket();
        }
    }

    /**
     * Set custom server handler.
     *
     * @param handler custom server handler.
     * @return this
     * @throws IllegalStateException if the server is active or stopped.
     */
    public WsServer setHandler(WsServer.Handler handler) {
        if (status != 0) {
            throw new IllegalStateException();
        }
        this.serverHandler = handler;
        return this;
    }

    void closeServerSocket() {
        try {
            serverSocket.close();
        } catch (IOException e) {
//            e.printStackTrace();
        }
    }

    /**
     * Starts listening. Server adds to the active servers list. Calls the
     * onStart method of the handler.
     */
    @Override
    public void run() {
        servers.add(this); // add to the list of WebSocket servers
        status = IS_OPEN;
        try {
            serverHandler.onStart(this);
            while (isOpen()) {
// serverSocket SO_TIMEOUT = 0 by WebSocket creator
                Socket socket = serverSocket.accept();
                socket.setSoTimeout(wsp.handshakeSoTimeout);
                WsConnection conn
                        = new WsConnection(socket, connectionHandler, isSecure, wsp);
// set a link to the list of connections to the server
                conn.connections = this.connections;
                if (serverHandler.onAccept(this, conn)) {
                    conn.start();
                } else {
                    conn.closeSocket();
                }
            }
        } catch (Exception e) {
            if (isOpen()) {
                error = e;
                interrupt();
            }
        }
        serverHandler.onStop(this, error);
    }

    /**
     * WebSocket server events handler.
     */
    public interface Handler {

        /**
         * Calls on start listening.
         *
         * @param server WebSocket server.
         */
        void onStart(WsServer server);

        /**
         * Calls on connection accepted.
         * <p>
         * Called when accepting a connection BEFORE WebSocket handshake.<br>
         * Leave method as soon as possible.
         * </p>
         *
         * @param server WebSocket server.
         * @param conn newly created server side connection.
         * @return true to approve connection, false to close connection.
         */
        boolean onAccept(WsServer server, WsConnection conn);

        /**
         * Calls on stop listening.
         *
         * @param server WebSocket server.
         * @param error null or occurred exception.
         */
        void onStop(WsServer server, Exception error);
    }

    /**
     * @deprecated Use WsServer.Handler interface instead.
     * @since 4.2
     */
    @Deprecated
    public interface EventHandler extends Handler {
    }

}
