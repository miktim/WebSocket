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
//import java.net.ProtocolException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.security.KeyStore;
import java.security.MessageDigest;
import javax.net.ServerSocketFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
//import javax.net.ssl.SSLSocket;
//import javax.net.ssl.TrustManagerFactory;
import static org.samples.java.websocket.WsConnection.GOING_AWAY;

public class WsServer {

    public static final String SERVER_VERSION = "0.1.1";
    public static final int MAX_CONNECTIONS = 10;
    public static final int DEFAULT_SERVER_PORT = 80;
    public static final int DEFAULT_CONNECTION_SO_TIMEOUT = 0;
    public static final int DEFAULT_MAX_MESSAGE_LENGTH
            = WsConnection.DEFAULT_MAX_MESSAGE_LENGTH;

    private ServerSocket serverSocket;
    private boolean isRunning;
    private int connectionSoTimeout = DEFAULT_CONNECTION_SO_TIMEOUT;
    private InetSocketAddress ssoAddress;
    WsHandler handler;
    boolean isSecure;
    private int maxMessageLength = DEFAULT_MAX_MESSAGE_LENGTH;

    public WsServer(InetSocketAddress isa, WsHandler handler)
            throws NullPointerException {
        if (isa == null || handler == null) {
            throw new NullPointerException();
        }
        this.isSecure = false;
        this.ssoAddress = isa;
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
            throw new IllegalArgumentException("buffer_size");
        }
        this.maxMessageLength = len;
    }

    public void start() throws Exception {
        serverSocket = getServerSocketFactory().createServerSocket();
        serverSocket.bind(ssoAddress, MAX_CONNECTIONS);
        MessageDigest.getInstance("SHA-1"); // check algorithm present
        (new WsServerThread(this)).start();
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

        private static final String THREAD_GROUP_NAME = "WSConnections";
        private final WsServer wsServer;
        private ThreadGroup threadGroup = null;

        WsServerThread(WsServer ws) {
            wsServer = ws;
        }

        @Override
        public void run() {
            wsServer.isRunning = true;
            if (wsServer.isSecure) {
                ((SSLServerSocket) wsServer.serverSocket)
                        .setNeedClientAuth(false);
            }
            threadGroup = new ThreadGroup(THREAD_GROUP_NAME);
            Socket socket;
            while (wsServer.isRunning) {
                try {
                    if (wsServer.isSecure) {
                        socket = ((SSLServerSocket) wsServer.serverSocket).accept();
//                        ((SSLSocket) socket).startHandshake();
                    } else {
                        socket = wsServer.serverSocket.accept();
                    }
                    socket.setSoTimeout(wsServer.connectionSoTimeout); // ms for handshake & ping
                    Thread cth = new Thread(threadGroup,
                            new WsConnectionThread(wsServer, socket));
//                    cth.setDaemon(true);
                    cth.start();
                } catch (SocketException e) {
                    if (wsServer.isRunning) {
                        wsServer.handler.onError(null, e);
                    }
                } catch (IOException e) {
                    wsServer.handler.onError(null, e);
//                    e.printStackTrace();
                }
            }
// close connections            
            if (threadGroup != null) {
                Thread[] threads = new Thread[threadGroup.activeCount()];
                threadGroup.enumerate(threads);
                for (int i = 0; i < threads.length; i++) {
                    threads[i].run();
                }
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
                    connection = new WsConnection(
                            socket, server.handler, server.isSecure);
                    connection.setMaxMessageLength(server.maxMessageLength);
                    connection.start();
                } else {
                    if (connection.isOpen()) {
                        connection.close(GOING_AWAY); // server stopped
                    }
                }
            } catch (Exception e) {
                server.handler.onError(connection, e);
//                e.printStackTrace(); // WebSocket handshake exception
            }
            if (!this.socket.isClosed()) {
                try {
                    this.socket.setSoLinger(true, 5);
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
