/*
 * WebSocket. WebSocket factory, MIT (c) 2020-2021 miktim@mail.ru
 *
 * Release notes:
 * - Java SE 7+, Android compatible;
 * - RFC-6455: https://tools.ietf.org/html/rfc6455;
 * - WebSocket protocol version: 13;
 * - WebSocket extensions not supported.
 *
 * Created: 2020-06-06
 */
package org.miktim.websocket;

import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
//import javax.net.ssl.SSLSocket;
//import javax.net.ssl.TrustManagerFactory;

public class WebSocket {

//    private int handshakeSoTimeout = WsConnection.DEFAULT_HANDSHAKE_SO_TIMEOUT; // open/close handshake
//    private int connectionSoTimeout = WsConnection.DEFAULT_CONNECTION_SO_TIMEOUT;
//    private boolean pingPong = true;
    private WsParameters wsp = new WsParameters();
    private InetAddress bindAddress;
    private final long wsId = (new Thread()).getId();
    private final String listenerPrefix = "WsListener-" + wsId + "-";
    private final String connectionPrefix = "WsConnection-" + wsId + "-";

    public WebSocket() throws NoSuchAlgorithmException {
        MessageDigest.getInstance("SHA-1"); // check algorithm present
    }

    public WebSocket(InetAddress bindAddr) throws Exception {
        super();
        if (NetworkInterface.getByInetAddress(bindAddr) == null) {
            throw new BindException();
        }
        bindAddress = bindAddr;
    }

    public static void setTrustStore(String jksFile, String passphrase) {
        System.setProperty("javax.net.ssl.trustStore", jksFile);
        System.setProperty("javax.net.ssl.trustStorePassword", passphrase);
    }

    public static void setKeyStore(String jksFile, String passphrase) {
        System.setProperty("javax.net.ssl.keyStore", jksFile);
        System.setProperty("javax.net.ssl.keyStorePassword", passphrase);
    }

    public void setWsParameters(WsParameters parm) throws CloneNotSupportedException {
        this.wsp = parm.clone();
    }

    public WsParameters getWsParameters() {
        return this.wsp;
    }

    public WsListener listen(int port, WsHandler handler) throws Exception {
        return startListener(port, handler, false);
    }

    public WsListener listenSafely(int port, WsHandler handler) throws Exception {
        return startListener(port, handler, true);
    }

    WsListener startListener(int port, WsHandler handler, boolean secure)
            throws Exception {
        WsListener listener = new WsListener(
                makeSocketAddress(port), handler, secure);
        listener.setName(listenerPrefix + listener.getId());
        listener.setWsParameters(wsp);
        listener.start();
        return listener;
    }

    private InetSocketAddress makeSocketAddress(int port) {
        if (bindAddress == null) {
            return new InetSocketAddress(port);
        }
        return new InetSocketAddress(bindAddress, port);
    }

    public WsConnection connect(String uri, WsHandler handler) throws Exception {
        WsConnection conn = new WsConnection(uri, handler, bindAddress);
        conn.setName(connectionPrefix + conn.getId());
        conn.setWsParameters(wsp);
        conn.start();
        return conn;
    }

    public WsConnection[] listConnections() {
        return WsListener.listByPrefix(WsConnection.class, connectionPrefix);
    }

    public WsListener[] listListeners() {
        return WsListener.listByPrefix(WsListener.class, listenerPrefix);
    }

    public void closeAll() {
// close WebSocket listeners/connections 
        for (WsListener listener : listListeners()) {
            listener.close();
        }
        for (WsConnection conn : listConnections()) {
            conn.close(WsStatus.GOING_AWAY, "");
        }
    }

}
