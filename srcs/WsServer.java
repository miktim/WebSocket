/*
 * WsServer. WebSocket Server, MIT (c) 2020 miktim@mail.ru
 *
 * Release notes:
 * - Java SE 1.7+, Android compatible;
 * - RFC-6455: https://tools.ietf.org/html/rfc6455;
 * - WebSocket protocol version: 13;
 * - WebSocket extensions not supported.
 *
 * Created: 2020-03-09
 */
package org.samples.java.websocket;

//import com.sun.net.httpserver.Headers;
import java.io.File;
import java.io.FileInputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyStore;
import java.security.MessageDigest;
import javax.net.ServerSocketFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
//import javax.net.ssl.SSLSocket;
//import javax.net.ssl.TrustManagerFactory;

public class WsServer {

    public static final int DEFAULT_SERVER_PORT = 80;
    public static final int DEFAULT_MAX_CONNECTIONS = 8;

    private boolean isRunning;
    boolean isSecure;
    private int maxConnections = DEFAULT_MAX_CONNECTIONS;
    int handshakeSoTimeout = WsConnection.DEFAULT_HANDSHAKE_SO_TIMEOUT;
    int connectionSoTimeout = WsConnection.DEFAULT_CONNECTION_SO_TIMEOUT;
    boolean pingEnabled = true;
    private int maxMessageLength = WsConnection.DEFAULT_MAX_MESSAGE_LENGTH;
    private InetSocketAddress socketAddress;
    private ServerSocket serverSocket;
    WsHandler handler;

    public WsServer(InetSocketAddress isa, WsHandler handler)
            throws NullPointerException {
        if (isa == null || handler == null) {
            throw new NullPointerException();
        }
        this.isSecure = false;
        this.socketAddress = isa;
        this.handler = handler;
    }

    public WsServer(int port, WsHandler handler)
            throws NullPointerException {
        this((new InetSocketAddress(port)), handler);
    }

    public boolean isSecure() {
        return isSecure;
    }

    public void setHanshakeSoTimeout(int millis, boolean ping) {
        handshakeSoTimeout = millis;
    }

    public int getHandshakeSoTimeout() {
        return handshakeSoTimeout;
    }

    public void setConnectionSoTimeout(int millis, boolean ping) {
        connectionSoTimeout = millis;
        pingEnabled = ping;
    }

    public int getConnectionSoTimeout() {
        return connectionSoTimeout;
    }

    public boolean isPingEnabled() {
        return pingEnabled && connectionSoTimeout > 0;
    }

    public void setMaxMessageLength(int len) throws IllegalArgumentException {
        if (len <= 0) {
            throw new IllegalArgumentException();
        }
        this.maxMessageLength = len;
    }

    public int getMaxMessageLength() {
        return maxMessageLength;
    }

    public void setMaxConnections(int cnt) throws IllegalArgumentException {
        if (cnt <= 0) {
            throw new IllegalArgumentException();
        }
        this.maxConnections = cnt;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public WsHandler getHandler() {
        return handler;
    }

    public void start() throws Exception {
        serverSocket = getServerSocketFactory().createServerSocket();
        serverSocket.bind(socketAddress, DEFAULT_MAX_CONNECTIONS); // query
        MessageDigest.getInstance("SHA-1"); // check algorithm present
        (new WsServerThread(this)).start();
    }

    public boolean isRunning() {
        return isRunning;
    }

    public void stop() {
        this.isRunning = false;
        try {
            serverSocket.close();
        } catch (Exception e) {
//            e.printStackTrace();
        }
    }

    private class WsServerThread extends Thread {

        private final WsServer server;
        private ThreadGroup threadGroup;

        WsServerThread(WsServer wss) {
            server = wss;
        }

        @Override
        public void run() {
            server.isRunning = true;
            threadGroup = new ThreadGroup("WsConnections-"
                    + Thread.currentThread().getId());
            if (server.isSecure) {
                ((SSLServerSocket) server.serverSocket)
                        .setNeedClientAuth(false);
            }
            while (server.isRunning) {
                try {
                    Socket socket = server.serverSocket.accept();
                    socket.setSoTimeout(server.handshakeSoTimeout);
                    (new Thread(threadGroup,
                            new WsConnectionThread(server, socket))).start();
                } catch (Exception e) {
                    if (server.isRunning) {
                        server.handler.onError(null, e);
                        server.stop();
                        break;
                    }
                }
            }
// close server connections            
            Thread[] threads = new Thread[threadGroup.activeCount()];
            threadGroup.enumerate(threads);
            for (Thread thread : threads) {
                thread.run();
            }
        }
    }

    private class WsConnectionThread implements Runnable {

        private final WsServer server;
        private final Socket socket;
        WsConnection connection = null;

        WsConnectionThread(WsServer srv, Socket soc) {
            this.server = srv;
            this.socket = soc;
        }

        @Override
        public void run() {
            try {
                if (connection == null) {
                    connection = new WsConnection(socket, server.handler);
                    connection.handshakeClient();
                    connection.setMaxMessageLength(server.maxMessageLength);
                    connection.setConnectionSoTimeout(server.connectionSoTimeout, server.pingEnabled);
                    if (Thread.currentThread().getThreadGroup().activeCount()
                            > server.maxConnections) {
                        connection.close(WsConnection.TRY_AGAIN_LATER);
                    } else {
                        connection.getHandler().onOpen(connection);
                    }
                    connection.listenInputStream();
                } else {
                    connection.close(WsConnection.GOING_AWAY); // server stopped
                }
            } catch (Exception e) {
                connection.getHandler().onError(connection, e); // WebSocket handshake exception
            }
            connection.closeSocket(); // force close
        }
    }

// https://docs.oracle.com/javase/8/docs/technotes/guides/security/jsse/samples/sockets/server/ClassFileServer.java
    private ServerSocketFactory getServerSocketFactory() throws Exception {
        if (isSecure) {
            SSLServerSocketFactory ssf;
            SSLContext ctx;
            KeyManagerFactory kmf;
            KeyStore ks;
            String ksPassphrase = System.getProperty("javax.net.ssl.trustStorePassword");
            char[] passphrase = ksPassphrase.toCharArray();

            ctx = SSLContext.getInstance("TLS");
            kmf = KeyManagerFactory.getInstance("SunX509");
            ks = KeyStore.getInstance("JKS"); //
            File ksFile = new File(System.getProperty("javax.net.ssl.trustStore"));
            ks.load(new FileInputStream(ksFile), passphrase);
            kmf.init(ks, passphrase);
//        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
//        tmf.init(ks);
//        ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
            ctx.init(kmf.getKeyManagers(), null, null);

            ssf = ctx.getServerSocketFactory();
            return ssf;
        } else {
            return ServerSocketFactory.getDefault();
        }
    }

}
