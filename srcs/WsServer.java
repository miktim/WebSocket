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
import java.io.IOException;
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

    public static final String SERVER_VERSION = "0.1.2";
    public static final int DEFAULT_SERVER_PORT = 80;
    public static final int DEFAULT_MAX_CONNECTIONS = 10;
    public static final int DEFAULT_CONNECTION_SO_TIMEOUT = 0;
    public static final int DEFAULT_MAX_MESSAGE_LENGTH = 2048;

    private boolean isRunning;
    boolean isSecure;
    private int connectionSoTimeout = DEFAULT_CONNECTION_SO_TIMEOUT;
    private int maxConnections = DEFAULT_MAX_CONNECTIONS;
    private int maxMessageLength = DEFAULT_MAX_MESSAGE_LENGTH;
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

// socket timeout for websocket handshaking & ping    
    public void setConnectionSoTimeout(int millis) {
        connectionSoTimeout = millis;
    }

    public void setMaxMessageLength(int len) throws IllegalArgumentException {
        if (len <= 0) {
            throw new IllegalArgumentException();
        }
        this.maxMessageLength = len;
    }

    public void setMaxConnections(int cnt) throws IllegalArgumentException {
        if (cnt <= 0) {
            throw new IllegalArgumentException();
        }
        this.maxConnections = cnt;
    }

    public void start() throws Exception {
        serverSocket = getServerSocketFactory().createServerSocket();
        serverSocket.bind(socketAddress, DEFAULT_MAX_CONNECTIONS);
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
        } catch (IOException e) {
//            e.printStackTrace();
        }
    }

    private class WsServerThread extends Thread {

        private final WsServer server;
        private final ThreadGroup threadGroup = new ThreadGroup("WsConnections");

        WsServerThread(WsServer ws) {
            server = ws;
        }

        @Override
        public void run() {
            server.isRunning = true;
            if (server.isSecure) {
                ((SSLServerSocket) server.serverSocket)
                        .setNeedClientAuth(false);
            }
            while (server.isRunning) {
                try {
                    Socket socket = server.serverSocket.accept();
                    socket.setSoTimeout(server.connectionSoTimeout); // for handshake & ping
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
// close connections            
            Thread[] threads = new Thread[threadGroup.activeCount()];
            threadGroup.enumerate(threads);
            for (int i = 0; i < threads.length; i++) {
                threads[i].run();
            }
        }
    }

    private class WsConnectionThread implements Runnable {

        private final WsServer server;
        private final Socket socket;
        WsConnection connection = null;

        WsConnectionThread(WsServer wss, Socket s) {
            this.server = wss;
            this.socket = s;
        }

        @Override
        public void run() {
            try {
                if (connection == null) {
                    connection = new WsConnection(socket, server.handler);
                    connection.setMaxMessageLength(server.maxMessageLength);
                    if (Thread.currentThread().getThreadGroup().activeCount()
                            > server.maxConnections) {
                        connection.start(WsConnection.POLICY_VIOLATION);
                    } else {
                        connection.start(0);
                    }
                } else {
                    if (connection.isOpen()) {
                        connection.close(WsConnection.GOING_AWAY); // server stopped
                    }
                }
            } catch (Exception e) {
                server.handler.onError(connection, e);
//                e.printStackTrace(); // WebSocket handshake exception
            }
            if (!this.socket.isClosed()) {
                try {
                    this.socket.setSoLinger(true, 2);
                    this.socket.close();
                } catch (IOException ie) {
//                    ie.printStackTrace();
                }
            }
        }
    }

    private File ksFile = null;
    private String ksPassphrase = null;

    public void setKeystore(File jksFile, String passphrase) {
        this.ksFile = jksFile;
        this.ksPassphrase = passphrase;
        System.setProperty("javax.net.ssl.trustStore", jksFile.getAbsolutePath());
        System.setProperty("javax.net.ssl.trustStorePassword", passphrase);
//        System.setProperty("javax.net.ssl.keyStore", jksFile);
//        System.setProperty("javax.net.ssl.keyStorePassword", passphrase);
    }

// https://docs.oracle.com/javase/8/docs/technotes/guides/security/jsse/samples/sockets/server/ClassFileServer.java
    private ServerSocketFactory getServerSocketFactory() throws Exception {
        if (isSecure) {
            SSLServerSocketFactory ssf;
            SSLContext ctx;
            KeyManagerFactory kmf;
            KeyStore ks;
            char[] passphrase = this.ksPassphrase.toCharArray();

            ctx = SSLContext.getInstance("TLS");
            kmf = KeyManagerFactory.getInstance("SunX509");
            ks = KeyStore.getInstance("JKS"); //
            ks.load(new FileInputStream(this.ksFile), passphrase);
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
