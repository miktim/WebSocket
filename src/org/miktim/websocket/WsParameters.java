/*
 * WsParameters. Common WebSocket parameters, MIT (c) 2020-2023 miktim@mail.ru
 *
 * Created: 2021-01-29
 */
package org.miktim.websocket;

import javax.net.ssl.SSLParameters;

public class WsParameters {

    String[] subProtocols = null; // WebSocket subprotocol[s] in preferred order
    int handshakeSoTimeout =  5000; // millis, TLS and WebSocket open/close handshake timeout
    int connectionSoTimeout = 5000; // millis, data exchange timeout
    boolean pingEnabled = true; // if false, connection terminate by connectionSoTimeout
    public static final int MIN_PAYLOAD_BUFFER_LENGTH = 126;
    int payloadBufferLength = 16384; // bytes. Outgoing payload length, incoming buffer length. 
    SSLParameters sslParameters;  // TLS parameters

    public WsParameters() {
        sslParameters = new SSLParameters();
        sslParameters.setNeedClientAuth(false);
    }

    public WsParameters(WsParameters wsp) {
        super();
        subProtocols = wsp.subProtocols;
        handshakeSoTimeout = wsp.handshakeSoTimeout;
        connectionSoTimeout = wsp.connectionSoTimeout;
        pingEnabled = wsp.pingEnabled;
        payloadBufferLength = wsp.payloadBufferLength;
        SSLParameters sslp = wsp.sslParameters; // !!! Java 7 
        sslParameters.setAlgorithmConstraints(sslp.getAlgorithmConstraints());
        sslParameters.setCipherSuites(sslp.getCipherSuites());
        sslParameters.setEndpointIdentificationAlgorithm(
                sslp.getEndpointIdentificationAlgorithm());
        sslParameters.setNeedClientAuth(sslp.getNeedClientAuth());
        sslParameters.setProtocols(sslp.getProtocols());
        sslParameters.setWantClientAuth(sslp.getWantClientAuth());
    }

    public void setSubProtocols(String[] subps) {
        if (subps == null || subps.length == 0) {
            subProtocols = null;
        } else {
            subProtocols = subps;
        }
    }

    public String[] getSubProtocols() {
        return subProtocols;
    }

    public void setHandshakeSoTimeout(int millis) {
        handshakeSoTimeout = millis;
    }

    public int getHandshakeSoTimeout() {
        return handshakeSoTimeout;
    }

    public void setConnectionSoTimeout(int millis, boolean ping) {
        connectionSoTimeout = millis;
        this.pingEnabled = ping;
    }

    public int getConnectionSoTimeout() {
        return connectionSoTimeout;
    }

    public boolean isPingEnabled() {
        return pingEnabled;
    }

    public int setPayloadBufferLength(int len) {
        payloadBufferLength = Math.max(len, MIN_PAYLOAD_BUFFER_LENGTH);
        return payloadBufferLength;
    }

    public int getPayloadBufferLength() {
        return payloadBufferLength;
    }

    public void setSSLParameters(SSLParameters sslParms) {
        sslParameters = sslParms;
    }

    public SSLParameters getSSLParameters() {
        return sslParameters;
    }

    public String join(Object[] array, char delimiter) {
        if (array == null || array.length == 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (Object obj : array) {
            sb.append(obj).append(",");
        }
        return sb.deleteCharAt(sb.length() - 1).toString();
    }

}
