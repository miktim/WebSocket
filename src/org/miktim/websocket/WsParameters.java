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
            clon.sslParameters.setNeedClientAuth(sslp.getNeedClientAuth());
            clon.sslParameters.setProtocols(cloneArray(sslp.getProtocols()));
            clon.sslParameters.setWantClientAuth(sslp.getWantClientAuth());
// TODO: removed code Android API 24 to API 16

//            sslParameters.setAlgorithmConstraints(sslp.getAlgorithmConstraints());
//            sslParameters.setEndpointIdentificationAlgorithm(
//                    sslp.getEndpointIdentificationAlgorithm());
        }

        return clon;
    }

    static <T> T[] cloneArray(T[] array) {
        return (array == null ? null : Arrays.copyOf(array, array.length));
    }

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

    public String[] getSubProtocols() {
        return subProtocols;
    }

    public WsParameters setHandshakeSoTimeout(int millis) {
        handshakeSoTimeout = millis;
        return this;
    }

    public int getHandshakeSoTimeout() {
        return handshakeSoTimeout;
    }

    public WsParameters setConnectionSoTimeout(int millis, boolean ping) {
        connectionSoTimeout = millis;
        this.pingEnabled = ping;
        return this;
    }

    public int getConnectionSoTimeout() {
        return connectionSoTimeout;
    }

    public boolean isPingEnabled() {
        return pingEnabled;
    }

    public WsParameters setPayloadBufferLength(int len) {
        payloadBufferLength = Math.max(len, MIN_PAYLOAD_BUFFER_LENGTH);
        return this;
    }

    public int getPayloadBufferLength() {
        return payloadBufferLength;
    }

// maximum number of pending connections on the server socket
// default value -1: system depended 
    public WsParameters setBacklog(int num) {
        backlog = num;
        return this;
    }

    public int getBacklog() {
        return backlog;
    }

    public WsParameters setMaxMessageLength(int len) {
        maxMessageLength = Math.max(len, MIN_MESSAGE_LENGTH);
        return this;
    }

    public int getMaxMessageLength() {
        return (int) maxMessageLength;
    }

    public WsParameters setSSLParameters(SSLParameters sslParms) {
        sslParameters = sslParms;
        return this;
    }

    public SSLParameters getSSLParameters() {
        return sslParameters;
    }

}
