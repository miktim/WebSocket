/*
 * WsListener. WebSocket listener, MIT (c) 2020 miktim@mail.ru
 *
 * Release notes:
 * - Java SE 1.7+, Android compatible;
 * - RFC-6455: https://tools.ietf.org/html/rfc6455;
 * - WebSocket protocol version: 13;
 * - WebSocket extensions not supported.
 *
 * Created: 2020-03-09
 */
package org.miktim.websocket;

//import com.sun.net.httpserver.Headers;
import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Array;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyStore;
import java.util.Vector;
import javax.net.ServerSocketFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
//import javax.net.ssl.SSLSocket;
//import javax.net.ssl.TrustManagerFactory;

public class WsListener extends Thread {

    private boolean isRunning;
    private boolean isSecure;
    private int handshakeSoTimeout = WsConnection.DEFAULT_HANDSHAKE_SO_TIMEOUT;
    private int connectionSoTimeout = WsConnection.DEFAULT_CONNECTION_SO_TIMEOUT;
    private boolean pingEnabled = true;
    private String connectionPrefix; // connection thread name
    private InetSocketAddress socketAddress;
    private ServerSocket serverSocket;
    private WsHandler handler;

    WsListener(InetSocketAddress isa, WsHandler handler, boolean secure)
            throws Exception {
        if (isa == null || handler == null) {
            throw new NullPointerException();
        }
        this.isSecure = secure;
        this.socketAddress = isa;
        this.handler = handler;
        serverSocket = getServerSocketFactory().createServerSocket();
        if (this.isSecure) {
            ((SSLServerSocket) this.serverSocket).setNeedClientAuth(false);
        }
        serverSocket.bind(socketAddress); // query
    }

    public boolean isSecure() {
        return isSecure;
    }

    void setHandshakeSoTimeout(int millis) {
        handshakeSoTimeout = millis;
    }

    public int getHandshakeSoTimeout() {
        return handshakeSoTimeout;
    }

    void setConnectionSoTimeout(int millis, boolean ping) {
        connectionSoTimeout = millis;
        pingEnabled = ping;
    }

    public int getConnectionSoTimeout() {
        return connectionSoTimeout;
    }

    public boolean isPingEnabled() {
        return pingEnabled && connectionSoTimeout > 0;
    }

    private int maxMessageLength = WsConnection.DEFAULT_MAX_MESSAGE_LENGTH; //in bytes
    private boolean streamingEnabled = false;

    void setMaxMessageLength(int maxLen, boolean enableStreaming) {
        maxMessageLength = maxLen;
        streamingEnabled = enableStreaming;
    }

    public int getMaxMessageLength() {
        return maxMessageLength;
    }

    public boolean isStreamingEnabled() {
        return streamingEnabled;
    }

    private String subprotocols = null;

    public void setSubprotocol(String sub) {
        subprotocols = sub;
    }

    public String getSubprotocol() {
        return subprotocols;
    }

    public WsHandler getHandler() {
        return handler;
    }

    public boolean isOpen() {
        return isRunning;
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

    public WsConnection[] listConnections() {
        return listByPrefix(WsConnection.class, connectionPrefix);
    }

    public void close() {
        this.isRunning = false;
        try {
            serverSocket.close();
        } catch (Exception e) {
//            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        if (!this.isRunning) {
            this.isRunning = true;
            connectionPrefix = "WsConnection-" + this.getId() + "-";
            try {
                while (this.isRunning) {
                    Socket socket = serverSocket.accept();
                    WsConnection conn = new WsConnection(socket, handler);
                    conn.setName(connectionPrefix + conn.getId());
                    conn.setHandshakeSoTimeout(handshakeSoTimeout);
                    conn.setConnectionSoTimeout(connectionSoTimeout, pingEnabled);
                    conn.setMaxMessageLength(maxMessageLength, streamingEnabled);
                    conn.setSubprotocol(subprotocols);
                    socket.setSoTimeout(handshakeSoTimeout);
                    conn.start();
                }
            } catch (Exception e) { // InterruptedException
                if (this.isRunning) {
                    handler.onError(null, e);
                    this.close();
                }
            }
        }
// close listener connections            
        for (WsConnection connection : listConnections()) {
            connection.close(WsConnection.GOING_AWAY, "");
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
