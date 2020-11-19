/*
 * WebSocket. WebSocket factory, MIT (c) 2020 miktim@mail.ru
 *
 * Release notes:
 * - Java SE 1.7+, Android compatible;
 * - RFC-6455: https://tools.ietf.org/html/rfc6455;
 * - WebSocket protocol version: 13;
 * - WebSocket extensions not supported.
 *
 * Created: 2020-06-06
 */
package org.miktim.websocket;

//import com.sun.net.httpserver.Headers;
import java.io.File;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
//import javax.net.ssl.SSLSocket;
//import javax.net.ssl.TrustManagerFactory;

public class WebSocket {

    private int handshakeSoTimeout = WsConnection.DEFAULT_HANDSHAKE_SO_TIMEOUT; // open/close handshake
    private int connectionSoTimeout = WsConnection.DEFAULT_CONNECTION_SO_TIMEOUT;
    private boolean pingPong = true;
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

    public static void setKeystore(File jksFile, String passphrase) {
        System.setProperty("javax.net.ssl.trustStore", jksFile.getAbsolutePath());
        System.setProperty("javax.net.ssl.trustStorePassword", passphrase);
//        System.setProperty("javax.net.ssl.keyStore", jksFile.getAbsolutePath());
//        System.setProperty("javax.net.ssl.keyStorePassword", passphrase);
    }

    public void setHanshakeSoTimeout(int millis) {
        handshakeSoTimeout = millis;
    }

    public void setConnectionSoTimeout(int millis, boolean ping) {
        connectionSoTimeout = millis;
        pingPong = ping;
    }

    private int maxMessageLength = WsConnection.DEFAULT_MAX_MESSAGE_LENGTH; //in bytes
    private boolean streamingEnabled = false;

    public void setMaxMessageLength(int maxLen, boolean enableStreaming) {
        maxMessageLength = maxLen;
        streamingEnabled = enableStreaming;
    }

    public int getMaxMessageLength() {
        return maxMessageLength;
    }

    public boolean isStreamingEnabled() {
        return streamingEnabled;
    }

    private String subprotocol = null;

    public void setSubprotocol(String sub) {
        subprotocol = sub ;
    }

    public String getSubprotocol() {
        return subprotocol;
    }

    public WsListener listen(int port, WsHandler handler) throws Exception {
        return startListener(port, handler, false);
    }

    public WsListener listenSafely(int port, WsHandler handler) throws Exception {
        return startListener(port, handler, true);
    }

    WsListener startListener(int port, WsHandler handler, boolean secure)
            throws Exception {
        WsListener listener
                = new WsListener(makeSocketAddress(port), handler, secure);
        listener.setName(listenerPrefix + listener.getId());
        listener.setHandshakeSoTimeout(handshakeSoTimeout);
        listener.setConnectionSoTimeout(connectionSoTimeout, pingPong);
        listener.setMaxMessageLength(maxMessageLength, streamingEnabled);
        listener.setSubprotocol(subprotocol);
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
        WsConnection connection = new WsConnection(uri, handler);
        connection.setName(connectionPrefix + connection.getId());
        connection.setHandshakeSoTimeout(handshakeSoTimeout);
        connection.setConnectionSoTimeout(connectionSoTimeout, pingPong);
        connection.setMaxMessageLength(maxMessageLength, streamingEnabled);
        connection.setSubprotocol(subprotocol);
        connection.open();
        connection.start();
        return connection;
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
            conn.close(WsConnection.GOING_AWAY, "");
        }
    }

}
