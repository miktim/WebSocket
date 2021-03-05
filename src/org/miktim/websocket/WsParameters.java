/*
 * WsParameters. Common WebSocket parameters, MIT (c) 2020-2021 miktim@mail.ru
 *
 * Created: 2021-01-29
 */
package org.miktim.websocket;

import java.util.Arrays;
import javax.net.ssl.SSLParameters;

public class WsParameters implements Cloneable {

    int handshakeSoTimeout = 30000; // millis, TLS and WebSocket open/close handshake
    int connectionSoTimeout = 60000;// millis
    boolean pingEnabled = true; // if false, connection terminate by socket timeout
    int maxMessageLength = 1048576; // bytes, incoming messages
    boolean framingEnabled = false; // ignored until implemented
    int payloadBufferLength = 16384; // bytes 
    String subProtocols[] = null; // WebSocket subprotocol[s] in preferred order
    SSLParameters sslParameters;  // TLS parameters

    public WsParameters() {
        sslParameters = new SSLParameters();
        sslParameters.setNeedClientAuth(false);
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

    public void setMaxMessageLength(int maxLen, boolean enableFraming) {
        maxMessageLength = maxLen;
        framingEnabled = enableFraming;
    }

    public int getMaxMessageLength() {
        return maxMessageLength;
    }

    public boolean isFramingEnabled() {
        return framingEnabled;
    }

    public void setPayloadBufferLength(int len) throws IllegalArgumentException {
        if (len < 125) { // control frame max payload length
            throw new IllegalArgumentException("Buffer length too short");
        }
        payloadBufferLength = len;
    }

    public int getPayloadBufferLength() {
        return payloadBufferLength;
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

    public void setSSLParameters(SSLParameters sslParms) {
        sslParameters = sslParms;
    }

    public SSLParameters getSSLParameters() {
        return sslParameters;
    }

    public static String join(Object[] array) {
        if (array == null || array.length == 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (Object obj : array) {
            sb.append(obj).append(",");
        }
        return sb.deleteCharAt(sb.length() - 1).toString();
    }

    @Override
    public WsParameters clone() throws CloneNotSupportedException {
        WsParameters clone = (WsParameters) super.clone();
        if (clone.subProtocols != null) {
            clone.subProtocols = subProtocols.clone();
        }
        return clone;
    }

//    String keyFile = null; // listener keyFile
//    String keyFilePassword = null;
}
