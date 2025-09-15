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
 * <p>
 * - Servers are created using a default server handler. You can set your own
 * handler or start the server immediately with start() or launch() methods. The
 * default handler's only action is to call RuntimeException when an error
 * occurs.<br>
 * - If the server is closed normally or abended, all associated connections are
 * closed with status code 1001 (GOING_AWAY) or 1011 (INTERNAL_ERROR) and it is
 * removed from the list of servers.<br>
 * - If the server is interrupted , it remains in the server list. Server side
 * connections stay alive and can be closed by usual way.<br>
 * </p>
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
     */
    public boolean isOpen() {
        return status.code == WsStatus.IS_OPEN;
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
     */
    //TODO: @Deprecated 
    public InetAddress getBindAddress() {
        return serverSocket.getInetAddress();
    }

    /* ?TODO: 4.3.0
    public SocketAddress getLocalSocketAddress() {
       return serverSocket.localSocketAddress();
     */
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
    //TODO: 4.3.0 @Deprecated
    public ServerSocket getServerSocket() {
        return serverSocket;
    }

    /* TODO: 4.3.0
  public Socket getConnectionSocket(WsConnection conn) {
    conn = connections.get(conn);
    if(conn == null) return null;
    return conn.socket;
  }  
     */
    /**
     * Start server.
     *
     * @return this.
     */
    //TODO: 4.3.0 @Deprecated
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
     */
    public void close() {
        close("Server shutdown");
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
     */
    public void close(String closeReason) {
        close(WsStatus.GOING_AWAY, closeReason);
    }

    void close(int code, String reason) {
        if (!status.wasClean) {
            status.code = code;
            status.wasClean = true;
            closeServerSocket();
// close associated connections
            for (WsConnection connection : listConnections()) {
                connection.close(code, reason);
            }
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
    // TODO: 4.3.0 @deprecated
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
     * Stops listening. The server remains in the list of servers. Server's
     * connections stay alive. Calls the onStop method of the handler.
     * </p>
     */
    @Override
    public void interrupt() {
        if (isOpen()) {
            status.code = WsStatus.GOING_AWAY;
            closeServerSocket();
        }
    }

    /**
     * Checks if the server is interrupted.
     *
     * @return true if is it.
     */
    @Override
    public boolean isInterrupted() {
        return status.code == WsStatus.GOING_AWAY && !status.wasClean;
    }

    /**
     * Starts listening.
     * <br>Server adds to the active servers list.<br>
     * Calls the onStart method of the handler.
     */
    @Override
    public void start() {
        servers.add(this); // add to the list of WebSocket servers
        status.code = WsStatus.IS_OPEN;
        status.wasClean = false;
        status.remotely = false;
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
                conn = new WsConnection(socket, connectionHandler, isSecure, wsp);
// set a link to the list of connections to the server
                conn.connections = this.connections;
                if (serverHandler.onAccept(this, conn)) {
                    conn.start();
                    conn = null;
                } else {
                    conn.closeSocket();
                }
            }
        } catch (Exception e) {
            if (status.code == WsStatus.IS_OPEN){// || !serverSocket.isClosed()) { //isOpen or isInterrupted
                status.error = e;
                status.code = WsStatus.INTERNAL_ERROR;
                closeServerSocket();
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
         */
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
     * @since 4.2
     */
    @Deprecated
    public interface EventHandler extends Handler {
    }
}
