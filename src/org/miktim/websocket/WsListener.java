/*
 * WsListener. WebSocket listener, MIT (c) 2020-2021 miktim@mail.ru
 *
 * Accepts sockets, creates and starts connection threads.
 *
 * Created: 2020-03-09
 */
package org.miktim.websocket;

import java.lang.reflect.Array;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Vector;
import javax.net.ssl.SSLServerSocket;

public class WsListener extends Thread {

    private boolean isRunning;
    private final boolean isSecure;
    private final WsParameters wsp;
    private String connectionPrefix; // connection thread name
    private final ServerSocket serverSocket;
    private final WsHandler handler;

    WsListener(ServerSocket ss, WsHandler h, boolean secure, WsParameters wsp)
            throws Exception {
        this.serverSocket = ss;
        this.handler = h;
        this.isSecure = secure;
        this.wsp = wsp.clone();
    }

    public boolean isSecure() {
        return isSecure;
    }

    public boolean isOpen() {
        return isRunning;
    }

    public WsParameters getParameters() {
        wsp.sslParameters = isSecure
                ? ((SSLServerSocket) serverSocket).getSSLParameters() : null;
        return wsp;
    }

    private String closeReason = null;

    public void close() {
        Thread.currentThread().setPriority(MAX_PRIORITY);
        this.isRunning = false;
        try {
            serverSocket.close();
        } catch (Exception e) {
//            e.printStackTrace();
        }
    }

    public void close(String reason) {
        closeReason = reason;
        close();
    }

    public WsConnection[] listConnections() {
        return listByPrefix(WsConnection.class, connectionPrefix);
    }

    @SuppressWarnings("unchecked")
    static <T> T[] listByPrefix(Class<T> c, String prefix) {
        Vector<T> vector = new Vector<>();
        Thread[] threads = new Thread[Thread.activeCount()];
        Thread.enumerate(threads);
        for (Thread thread : threads) {
            if (thread.getName().startsWith(prefix)) {
                vector.add((T) thread);
            }
        }
        return vector.toArray((T[]) Array.newInstance(c, vector.size()));
    }

    @Override
    public void run() {
        if (!this.isRunning) {
            this.isRunning = true;
            connectionPrefix = "WsConnection-" + this.getId() + "-";
            while (this.isRunning) {
                try {
                    Socket socket = serverSocket.accept();
                    WsConnection conn
                            = new WsConnection(socket, handler, isSecure, wsp);
                    conn.setName(connectionPrefix + conn.getId());
                    socket.setSoTimeout(wsp.handshakeSoTimeout);
                    conn.start();
                } catch (Exception e) {
                    if (this.isRunning) {
                        handler.onError(null, e);
                        this.close();
                    }
                    break;
                }
            }
        }

// close associated connections            
        for (WsConnection connection : listConnections()) {
            connection.close(WsStatus.GOING_AWAY, closeReason);
        }
    }

}
