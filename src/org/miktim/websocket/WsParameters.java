/*
 * WsParameters. Common server/connection parameters, MIT (c) 2020-2023 miktim@mail.ru
 *
 * Created: 2021-01-29
 */
package org.miktim.websocket;

import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

/**
 * WebSocket connection parameters.
 */
public class WsParameters {

    public static final int MIN_PAYLOAD_BUFFER_LENGTH = 125;
    public static final int MIN_MESSAGE_LENGTH = 1024;

    String[] subProtocols = null; // WebSocket subprotocol[s] in preferred order
    int handshakeSoTimeout = 2000; // millis, TLS and WebSocket open/close handshake timeout
    int connectionSoTimeout = 4000; // millis, data exchange timeout
    boolean pingEnabled = true; // if false, connection terminate by connectionSoTimeout
    int payloadBufferLength = 32768; // bytes. Outgoing payload length, incoming buffer length. 
    int backlog = -1; // maximum number of pending connections on the server socket (system default)
    long maxMessageLength = 1048576L; // 1 MiB
    SSLParameters sslParameters = null;  // TLS parameters

    /**
     * Creates connection parameters.
     */
    public WsParameters() {
        try {
            SSLContext sslContext = SSLContext.getDefault();
            sslParameters = sslContext.getDefaultSSLParameters();
            sslParameters.setNeedClientAuth(false);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
    }

    // deep clone
    synchronized WsParameters deepClone() {
        WsParameters clon = new WsParameters();
        clon.subProtocols = cloneArray(subProtocols);
        clon.handshakeSoTimeout = handshakeSoTimeout;
        clon.connectionSoTimeout = connectionSoTimeout;
        clon.pingEnabled = pingEnabled;
        clon.payloadBufferLength = payloadBufferLength;
        clon.backlog = backlog;
        clon.maxMessageLength = maxMessageLength;
        SSLParameters sslp = sslParameters;
        if (sslp != null) {
// Android API 16
            clon.sslParameters.setCipherSuites(cloneArray(sslp.getCipherSuites()));
            clon.sslParameters.setProtocols(cloneArray(sslp.getProtocols()));
            clon.sslParameters.setNeedClientAuth(sslp.getNeedClientAuth());
            clon.sslParameters.setWantClientAuth(sslp.getWantClientAuth());
// TODO: downgrade Android API 24 to API 16

//            sslParameters.setAlgorithmConstraints(sslp.getAlgorithmConstraints());
//            sslParameters.setEndpointIdentificationAlgorithm(
//                    sslp.getEndpointIdentificationAlgorithm());
        }

        return clon;
    }

    static <T> T[] cloneArray(T[] array) {
        return (array == null ? null : Arrays.copyOf(array, array.length));
    }

    /**
     * Sets supported (server) or requested (client) subprotocols.
     *
     * @param subps array of subprotocls or null.
     * @return this.
     */
    public WsParameters setSubProtocols(String[] subps) {
        if (subps == null || subps.length == 0) {
            subps = null;
        } else {
            for (int i = 0; i < subps.length; i++) {
                subps[i] = String.valueOf(subps[i]).trim();
            }
        }
        subProtocols = subps;
        return this;
    }

    /**
     * Returns supported (server) or requested (client) subprotocols.
     * @return array of subprotocols or null (default).
     */
    public String[] getSubProtocols() {
        return subProtocols;
    }

    /**
     * Sets open/close WebSocket handshake timeout.
     * @param millis timeout in milliseconds.
     * @return this.
     */
    public WsParameters setHandshakeSoTimeout(int millis) {
        handshakeSoTimeout = millis;
        return this;
    }

    /**
     * Returns open/close WebSocket handshake timeout.
     * @return timeout in milliseconds. Default: 2000
     */
    public int getHandshakeSoTimeout() {
        return handshakeSoTimeout;
    }

    /**
     * Sets connection Socket timeout and ping enabled.
     * @param millis socket timeout in milliseconds.
     * @param ping true - ping enabled.
     * @return this
     */
    public WsParameters setConnectionSoTimeout(int millis, boolean ping) {
        connectionSoTimeout = millis;
        this.pingEnabled = ping;
        return this;
    }

    /**
     * Returns connection Socket timeout.
     * @return timeout in milliseconds. Default: 4000.
     */
    public int getConnectionSoTimeout() {
        return connectionSoTimeout;
    }

    /**
     * Returns ping enabled.
     * @return true if is it. Default: enabled.
     */
    public boolean isPingEnabled() {
        return pingEnabled;
    }

    /**
     * Sets the maximum payload length of the outgoing message frames.
     * The minimum length is 125 bytes.
     * @param len payload length in bytes.
     * @return this
     */
    public WsParameters setPayloadBufferLength(int len) {
        payloadBufferLength = Math.max(len, MIN_PAYLOAD_BUFFER_LENGTH);
        return this;
    }

    /**
     * Returns maximum payload length of the outgoing message frames.
     * @return payload length in bytes. Default: 32 KiB.
     */
    public int getPayloadBufferLength() {
        return payloadBufferLength;
    }


    /**
     * Sets maximum number of pending connections on the server socket.
     * @param num of pending connections.
     * @return this.
     */
    public WsParameters setBacklog(int num) {
        backlog = num;
        return this;
    }

    /**
     * Returns maximum number of pending connections on the server socket.
     * @return number of pending connections. Default: -1 (system depended).
     */
    public int getBacklog() {
        return backlog;
    }

    /**
     * Sets incoming WebSocket message max length.
     * If exceeded, the connection will be terminated with the 1009 (MESSAGE_TOO_BIG) status code
     * @param len max length.
     * @return this.
     */
    public WsParameters setMaxMessageLength(int len) {
        maxMessageLength = Math.max(len, MIN_MESSAGE_LENGTH);
        return this;
    }

    /**
     * Returns incoming WebSocket message max length.
     * @return max message length in bytes. Default: 1 MiB.
     */
    public int getMaxMessageLength() {
        return (int) maxMessageLength;
    }

    /**
     * Sets TLS connection parameters.
     * SSLParameters used by server:<br>
     * Protocols, CipherSuites, NeedClientAut, WantClientAuth.
     * @param sslParms SSL connection parameters.
     * @return this.
     */
    public WsParameters setSSLParameters(SSLParameters sslParms) {
        sslParameters = sslParms;
        return this;
    }

    /**
     * Returns TLS connection parameters.
     * @return SSL parameters. Defaults: from the SSLContext.
     */
    public SSLParameters getSSLParameters() {
        return sslParameters;
    }

}
