/*
 * WsServer. WebSocket Server, MIT (c) 2020-2025 miktim@mail.ru
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
 *
 * <p>
 * If the server is stopped normally or crashed, all associated connections are
 * closed with status code 1001 (GOING_AWAY) or 1011 (INTERNAL_ERROR) and it is
 * removed from the list of servers.</p>
 */
public class WsServer extends Thread {

    private WsStatus status = new WsStatus();

    private final boolean isSecure;
    private final WsParameters wsp;
    private final ServerSocket serverSocket;
    private final WsConnection.Handler connectionHandler; // connection handler
    List<WsServer> servers = null;
    private final List<WsConnection> connections
            = Collections.synchronizedList(new ArrayList<WsConnection>());

// default handler    
    private ServerHandler serverHandler = new ServerHandler() {
        @Override
        public void onStart(WsServer server, WsParameters wsp) {
        }

        @Override
        public void onStop(WsServer server, WsStatus status) {
        }
    };

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
            while (status.code == WsStatus.IS_INACTIVE) {
                try {
                    this.wait(500); // exception
                } catch (InterruptedException ex) {
                    return null;
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
        return isAlive() && status.code == WsStatus.IS_OPEN; //
    }

    /**
     * Returns server status.
     *
     * @return server status.
     * @since 5.0
     */
    public WsStatus getStatus() {
        return status.deepClone();
    }

    /**
     * Returns listening port number.
     *
     * @return listening port number.
     */
    public int getPort() {
        return serverSocket.getLocalPort();
    }

    /**
     * Returns the server-side connection parameters.
     *
     * @return clone of the connection parameters.
     */
    public WsParameters getParameters() {
        return wsp.deepClone();
    }

    /**
     * Returns the ServerSocket of this server.
     *
     * @return the ServerSocket object.
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
// remove server from WebSocket server list
            servers.remove(this);
            status.wasClean = true;
        }
    }

    void closeServerSocket() {
        try {
            serverSocket.close();
        } catch (IOException ignore) {
        }
    }

    /**
     * Interrupts the server, keeping server connections open.
     * <p>
     * Stops listening. The server remains in the list of servers. Server's
     * connections stay alive and can be closed by usual way.
     * </p>
     *
     * @deprecated 4.3
     */
    @Deprecated
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
     * @deprecated 4.3
     */
    @Deprecated
    @Override
    public boolean isInterrupted() {
        return status.code == WsStatus.GOING_AWAY && !status.wasClean;
    }

    @Override
    public void start() {
        servers.add(this); // add to the list of WebSocket servers
        status.code = WsStatus.IS_OPEN;
        status.wasClean = false;
        status.remotely = false;
        if (connectionHandler instanceof ServerHandler) {
            serverHandler = (ServerHandler) connectionHandler;
        }
        super.start();
    }

    @Override
    public void run() {
        setName("WsServer" + getName());
        try {
            synchronized (this) {
                serverHandler.onStart(this, wsp);
                this.notifyAll();
            }
            while (true) {//(isOpen() ){
// serverSocket SO_TIMEOUT = 0 by WebSocket creator
                Socket socket = serverSocket.accept();
                if (wsp.backlog > -1 && connections.size() >= wsp.backlog) {
                    try {
                        (new HttpHead())
                                .setStartLine("HTTP/1.1 429 Too Many Requests")
//                            .set("Retry-After", "10")
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
        } catch (Exception e) {
            if (status.code == WsStatus.IS_OPEN) {
                status.error = e;
                stopServer(WsStatus.INTERNAL_ERROR, "Abnormal shutdown");
            }
        }

        closeServerSocket();
        try {
            serverHandler.onStop(this, status);
        } catch (Exception ex) {
            if (status.error == null) {
                status.error = ex;
            }
        }
        if (status.error != null) {
            throw new RuntimeException("Abnormal shutdown", status.error);
        }

    }

    /**
     * WebSocket server event handler.
     */
    interface ServerHandler {

        /**
         * Called when the server is ready to accept connections.
         *
         * @param server WebSocket server.
         * @param wsp server-side connection parameters.
         * @since 5.0
         */
        public void onStart(WsServer server, WsParameters wsp);

        /**
         * Called when the server stops after the ServerSocket is closed.
         *
         * @param server WebSocket server.
         * @param status clone of the server status.
         * @see WsStatus
         * @since 5.0
         */
        public void onStop(WsServer server, WsStatus status);
    }

    /**
     * WebSocket server events handler. Extends connection handler.
     */
    public interface Handler extends WsConnection.Handler, ServerHandler {
    };

}
