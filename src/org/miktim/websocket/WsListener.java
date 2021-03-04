/*
 * WsListener. WebSocket listener, MIT (c) 2020-2021 miktim@mail.ru
 *
 * Release notes:
 * - Java SE 7+, Android compatible;
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
    private WsParameters wsp = new WsParameters();
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
        if (isSecure && wsp.sslParameters != null) {
            ((SSLServerSocket) serverSocket).setSSLParameters(wsp.sslParameters);
        }
        serverSocket.bind(socketAddress);
    }

    public boolean isSecure() {
        return isSecure;
    }

    public boolean isOpen() {
        return isRunning;
    }

    void setWsParameters(WsParameters parm) throws CloneNotSupportedException {
        this.wsp = parm.clone();
    }

    public WsParameters getWsParameters() {
        if (isSecure) {
            wsp.sslParameters = ((SSLServerSocket) serverSocket).getSSLParameters();
        }
        return this.wsp;
    }

    public WsHandler getHandler() {
        return handler;
    }

    public void close() {
        this.isRunning = false;
        try {
            serverSocket.close();
        } catch (Exception e) {
//            e.printStackTrace();
        }
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
//            if (isSecure && wsp.sslParameters != null) {
//                ((SSLServerSocket) serverSocket).setSSLParameters(wsp.sslParameters);
//            }
            connectionPrefix = "WsConnection-" + this.getId() + "-";
            while (this.isRunning) {
                try {
                    Socket socket = serverSocket.accept();
                    WsConnection conn = new WsConnection(socket, handler, isSecure);
                    conn.setName(connectionPrefix + conn.getId());
                    conn.setWsParameters(wsp);
                    socket.setSoTimeout(wsp.handshakeSoTimeout);
                    conn.start();
                } catch (Exception e) {
                    if (this.isRunning) {
                        handler.onError(null, e);
                        this.close();
                    }
                }
            }
        }
// close listener connections            
        for (WsConnection connection : listConnections()) {
            connection.close(WsStatus.GOING_AWAY, "");
        }
    }

// https://docs.oracle.com/javase/8/docs/technotes/guides/security/jsse/samples/sockets/server/ClassFileServer.java
    private ServerSocketFactory getServerSocketFactory() throws Exception {
        if (isSecure) {
            SSLServerSocketFactory ssf;
            SSLContext ctx;
            KeyManagerFactory kmf;
            KeyStore ks;
            String ksPassphrase = System.getProperty("javax.net.ssl.keyStorePassword");
            char[] passphrase = ksPassphrase.toCharArray();

            ctx = SSLContext.getInstance("TLS");
            kmf = KeyManagerFactory.getInstance("SunX509");
            ks = KeyStore.getInstance("JKS"); //
            File ksFile = new File(System.getProperty("javax.net.ssl.keyStore"));
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
