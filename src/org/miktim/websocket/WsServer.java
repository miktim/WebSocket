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
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * WebSocket server for cleartext or TLS connections.
 * 
 * - If the server is stopped normally, all associated connections are
 * closed with status code 1001 (GOING_AWAY) and it is
 * removed from the list of servers.<br>
 * - If the server is interrupted or abended, it remains in the server list. Server side
 * connections stay alive and can be closed by usual way.<br>
 */

public class WsServer extends Thread {

    private final WsStatus status = new WsStatus();

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
     *
     * @return true if is it.
     */
    public boolean isSecure() {
        return isSecure;
    }

    /**
     * Checks if the server is open.
     *
     * @return true if is it.
     * @deprecated use isActive method instead
     */
    @Deprecated
    public boolean isOpen() {
        return status.code == WsStatus.IS_OPEN; // isAlive()?
    }

    /**
     * Checks if the server is active.
     *
     * @return true if is it.
     * @since 4.3
     */
    public boolean isActive() {
        return isAlive() && status.code == WsStatus.IS_OPEN; //
    }

    /**
     * Returns server error if any.
     *
     * @return null or exception.
     */
    public Throwable getError() {
        return status.error;
    }

    /* TODO: 4.3.0
    public WsStatus getStatus() {
        synchronized(status) {
            return status.deepClone();
        }
    }
     */
    /**
     * Returns listening port.
     *
     * @return listening port number.
     */
    public int getPort() {
        return serverSocket.getLocalPort();
    }

    /**
     * Returns the listening interface address.
     *
     * @return interface address.
     * @deprecated accessed via ServerSocket methods
     */
    @Deprecated
    public InetAddress getBindAddress() {
        return serverSocket.getInetAddress();
    }

    /**
     * Returns the server side connection parameters.
     *
     * @return clone of the connection parameters.
     */
    public WsParameters getParameters() {
        return wsp.deepClone();
    }

    /**
     * Returns ServerSocket.
     *
     * @return ServerSocket.
     */
    public ServerSocket getServerSocket() {
        return serverSocket;
    }

    /**
     * Returns active server connection socket
     * @param conn server side active connection
     * @return Socket object or null
     * @since 4.3
     */
    public Socket getConnectionSocket(WsConnection conn) {
        if (connections.contains(conn)) {
            return conn.socket;
        }
        return null;
    }

    /**
     * Start server.
     *
     * @return this.
     */
    @Deprecated
    public WsServer launch() {
        this.start();
        return this;
    }

    /**
     * Lists server side connections.
     *
     * @return array of connections.
     */
    public WsConnection[] listConnections() {
        return connections.toArray(new WsConnection[0]);
    }

    /**
     * Close server with associated connections.
     * <p>
     * Stops listening. Connections are closed with the GOING_AWAY status code.
     * The server is removed from the list of active WebSocket servers. Calls
     * the onStop method of the handler.
     * </p>
     *
     * @see WsStatus
     * @deprecated use stopServer methods instead
     */
    @Deprecated
    public void close() {
        stopServer("Server shutdown");
    }

    /**
     * Close server with associated connections.
     * <p>
     * Stops listening. Connections are closed with the GOING_AWAY status code.
     * The server is removed from the list of active WebSocket servers. Calls
     * the onStop method of the handler.
     * </p>
     *
     * @param closeReason connections close reason. Reason max length is 123
     * BYTES.
     * @see WsStatus
     * @deprecated use stopServer methods instead
     */
    @Deprecated
    public void close(String closeReason) {
        stopServer(WsStatus.GOING_AWAY, closeReason);
    }

    /**
     * Stops the server and closes the associated connections.
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
     * Stops the server and closes the associated connections.
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
        if (!status.wasClean) {
            status.code = code;
            closeServerSocket();
// close associated connections
            for (WsConnection connection : listConnections()) {
                connection.close(code, reason);
            }
            status.wasClean = true;
            servers.remove(this); // remove from the list of WebSocket servers
        }
    }

    /**
     * Set custom server handler.
     *
     * @param handler custom server handler.
     * @return this
     * @throws IllegalStateException if the server is active or stopped.
     */
    @Deprecated
    public WsServer setHandler(WsServer.Handler handler) {
//        if (status != IS_OPEN) {
//            throw new IllegalStateException();
//        }
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
     * Interrupts the server, keeping server connections open.
     * <p>
     * Stops listening. 
     * The server remains in the list of servers.
     * Server's connections stay alive. 
     * </p>
     * @deprecated
     */
    @Override
    public void interrupt() {
        if (isAlive()) {
            status.code = WsStatus.GOING_AWAY;
            closeServerSocket();
        }
    }

    /**
     * Checks if the server is interrupted.
     *
     * @return true if is it.
     * @deprecated
     */
    @Deprecated
    @Override
    public boolean isInterrupted() {
        return status.code == WsStatus.GOING_AWAY && !status.wasClean;
    }

    /**
     * Starts the server thread.
     * <br>The server adds to the active servers list.<br>
     */
    @Override
    public void start() {
        servers.add(this); // add to the list of WebSocket servers
        status.code = WsStatus.IS_OPEN;
        status.wasClean = false;
        status.remotely = false;
        if (connectionHandler instanceof Handler) {
            serverHandler = (Handler) connectionHandler;
        }
        super.start();
    }

    @Override
    public void run() {
        WsConnection conn = null;
        try {
            serverHandler.onStart(this);
            while (true) {//(isOpen() ){
// serverSocket SO_TIMEOUT = 0 by WebSocket creator
                Socket socket = serverSocket.accept();
                socket.setSoTimeout(wsp.handshakeSoTimeout);
                conn = new WsConnection(socket, connectionHandler, wsp, isSecure);
// set a link to the server's connection list
                conn.connections = this.connections;
                if (serverHandler.onAccept(this, conn)) {
                    conn.start();
                } else {
                    conn.closeSocket();
                }
                conn = null; //the connection was serviced
            }
        } catch (Exception e) {
            if (status.code == WsStatus.IS_OPEN) {
                status.error = e;
                stopServer(WsStatus.INTERNAL_ERROR, "Abnormal shutdown");
                if (conn != null) {
                    conn.closeSocket();
                }
            }
        }

        try {
            serverHandler.onStop(this, (Exception) status.error);
        } catch (Exception e) {
            if (status.error == null) {
                status.error = e;
            }
        }
        if (status.error != null) {
            throw new RuntimeException("Abnormal shutdown", status.error);
        }

    }

    /**
     * WebSocket server events handler.
     */
    public interface Handler {

        /**
         * Called when the server is started.
         *
         * @param server WebSocket server.
         */
        void onStart(WsServer server);

        /**
         * Called when the connection is accepted.
         * <p>
         * It is called BEFORE WebSocket handshake.<br>
         * Leave method as soon as possible.
         * </p>
         *
         * @param server WebSocket server.
         * @param conn newly created server side connection.
         * @return true to approve connection, false to close connection.
         * @deprecated
         */
        @Deprecated
        boolean onAccept(WsServer server, WsConnection conn);

        /**
         * Called when the server is stopped.
         *
         * @param server WebSocket server.
         * @param error null or occurred exception.
         */
        void onStop(WsServer server, Exception error);
    }

    /**
     * @deprecated Use WsServer.Handler interface instead.
     */
    @Deprecated
    public interface EventHandler extends Handler {
    }
}
