/*
 * WsParameters. Common WebSocket parameters, MIT (c) 2020-2023 miktim@mail.ru
 *
 * 3.2.0
 * - setters return this;
 * 3.3.1
 * - backlog parameter added;
 * 
 * Created: 2021-01-29
 */
package org.miktim.websocket;

import javax.net.ssl.SSLParameters;

public class WsParameters {

    String[] subProtocols = null; // WebSocket subprotocol[s] in preferred order
    int handshakeSoTimeout = 4000; // millis, TLS and WebSocket open/close handshake timeout
    int connectionSoTimeout = 4000; // millis, data exchange timeout
    boolean pingEnabled = true; // if false, connection terminate by connectionSoTimeout
    public static final int MIN_PAYLOAD_BUFFER_LENGTH = 126;
    int payloadBufferLength = 32768; // bytes. Outgoing payload length, incoming buffer length. 
    int backlog = -1; // maximum number of pending connections on the server socket (system default)
    
    SSLParameters sslParameters;  // TLS parameters

    public WsParameters() {
        sslParameters = new SSLParameters();
        sslParameters.setNeedClientAuth(false);
    }

    public WsParameters(WsParameters wsp) {
        super();
        if (wsp != null) {
            subProtocols = wsp.subProtocols;
            handshakeSoTimeout = wsp.handshakeSoTimeout;
            connectionSoTimeout = wsp.connectionSoTimeout;
            pingEnabled = wsp.pingEnabled;
            payloadBufferLength = wsp.payloadBufferLength;
            backlog = wsp.backlog;
            SSLParameters sslp = wsp.sslParameters; // !!! Java 7 
            sslParameters.setAlgorithmConstraints(sslp.getAlgorithmConstraints());
            sslParameters.setCipherSuites(sslp.getCipherSuites());
            sslParameters.setEndpointIdentificationAlgorithm(
                    sslp.getEndpointIdentificationAlgorithm());
            sslParameters.setNeedClientAuth(sslp.getNeedClientAuth());
            sslParameters.setProtocols(sslp.getProtocols());
            sslParameters.setWantClientAuth(sslp.getWantClientAuth());
        }
    }
    
    public WsParameters setSubProtocols(String[] subps) {
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

    public WsParameters setSSLParameters(SSLParameters sslParms) {
        sslParameters = sslParms;
        return this;
    }

    public SSLParameters getSSLParameters() {
        return sslParameters;
    }

}
