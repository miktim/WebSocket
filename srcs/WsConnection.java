/*
 * WsConnection java SE 1.8+
 * MIT (c) 2020 miktim@mail.ru
 * RFC-6455: https://tools.ietf.org/html/rfc6455
 *
 * Release notice:
 * - WebSocket extensions not supported;
 * - protocol version: 13 
 *
 * Created: 2020-03-09
 */
package org.samples.java.wsserver;

import com.sun.net.httpserver.Headers;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ProtocolException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64; // from java 1.8

public class WsConnection {

// closure codes see RFC: https://tools.ietf.org/html/rfc6455#section-7.4 
    public static final int NORMAL_CLOSURE = 1000; //*
    public static final int GOING_AWAY = 1001; //* shutdown or socket timeout
    public static final int PROTOCOL_ERROR = 1002; //* 
    public static final int UNSUPPORTED_DATA = 1003; //* unsupported opcode
//    public static final int MOZILLA_DEFAULT = 1005; // mozilla internal default
//    public static final int ABNORMAL_CLOSURE = 1006; // closing connection without op
    public static final int INVALID_DATA = 1007; // non utf-8 text, for example
    public static final int POLICY_VIOLATION = 1008; //
    public static final int MESSAGE_TOO_BIG = 1009; // *
    public static final int UNSUPPORTED_EXTENSION = 1010; // client only 
    public static final int INTERNAL_ERROR = 1011; //* server only
// 
    static final int DEFAULT_MAX_MESSAGE_LENGTH = 2048;
// request line header name
    static final String REQUEST_LINE_HEADER = "_RequestLine";

    private final Socket socket;
    private final Headers requestHeaders;
    private final WsHandler handler;
    private int closureCode = 0;
    private int maxMessageLength = DEFAULT_MAX_MESSAGE_LENGTH;

    public void send(byte[] message) throws IOException {
        if (this.closureCode == 0) {
            sendPayload(OP_BINARY | OP_FINAL, message, false);
        } // else trhow new IOException("close_hanshake");
    }

    public void send(String message) throws IOException {
        if (this.closureCode == 0) {
            sendPayload(OP_TEXT | OP_FINAL, message.getBytes(), false);
        } // else trhow new IOException("close_hanshake");
    }

    public void streamText(InputStream is) throws IOException {
        stream(OP_TEXT, is, true);
    }

    public void streamBinary(InputStream is) throws IOException {
        stream(OP_BINARY, is, true);
    }

    void setMaxMessageLength(int len) {
        maxMessageLength = len;
    }

    public int getMaxMessageLength() {
        return maxMessageLength;
    }

    public boolean isOpen() {
        return !this.socket.isClosed();
    }

    public int getClosureCode() {
        return this.closureCode;
    }

    public void close() {
        try {
            close(NORMAL_CLOSURE);
        } catch (IOException e) {
//            e.printStackTrace();
        }
    }

    void close(int code) throws IOException {
        if (this.closureCode == 0) {
            if (isOpen()) {
                this.closureCode = code;
                int absCode = Math.abs(code);
                byte[] payload = new byte[2];
                payload[0] = (byte) (absCode >>> 8);
                payload[1] = (byte) (absCode & 0xFF);
                sendPayload(OP_CLOSE | OP_FINAL, payload, false);
            }
        }
    }

    public String getPath() {
        try {
            return (new URI(requestHeaders.getFirst(REQUEST_LINE_HEADER)
                    .split(" ")[1])).getPath();
        } catch (Exception e) {
            return "unknown";
        }
    }

    WsConnection(Socket s, Headers hs, WsHandler hd) {
        this.socket = s;
        this.requestHeaders = hs;
        this.handler = hd;
    }

    void start() throws IOException {
        handshakeClient();
        this.handler.onOpen(this);
        waitInputStream();
    }

    private void handshakeClient() throws IOException {
        String upgrade = requestHeaders.getFirst("Upgrade");
        String key = requestHeaders.getFirst("Sec-WebSocket-Key");
//        int version = Integer.parseInt(requestHeaders.getFirst("Sec-WebSocket-Version"));
//        String protocol = requestHeaders.getFirst("Sec-WebSocket-Protocol"); // chat, superchat

        if (upgrade == null || key == null || !upgrade.equals("websocket")) {
            sendResponseHeaders(null, "400 Bad Request");
            socket.close();
            throw new ProtocolException("bad_request");
        } else {
            Headers responseHeaders = new Headers();
            responseHeaders.set("Upgrade", "websocket");
            responseHeaders.set("Connection", "upgrade,keep-alive");
            responseHeaders.set("Sec-WebSocket-Accept", sha1Hash(key));
            responseHeaders.set("Sec-WebSocket-Version", "13");
            sendResponseHeaders(responseHeaders, "101 Upgrade");
        }
    }

    void sendResponseHeaders(Headers h, String reason)
            throws IOException {
        StringBuilder sb = new StringBuilder("HTTP/1.1 ");
        sb.append(" ").append(reason).append("\r\n");
        if (h != null) {
            for (String hn : h.keySet()) {
                sb.append(hn).append(": ").append(h.getFirst(hn)).append("\r\n");
            }
        }
        sb.append("\r\n");
        socket.getOutputStream().write(sb.toString().getBytes());
        socket.getOutputStream().flush();
    }

    private String sha1Hash(String key) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-1");
            return base64Encode(
                    md.digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11")
                            .getBytes()));
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace(); //??? throws
        }
        return null;
    }

    public static String base64Encode(byte[] b) {
        return Base64.getEncoder().encodeToString(b);
    }

    static final int OP_FINAL = 0x80;
    static final int OP_EXTENSONS = 0x70;
    static final int OP_CONTINUATION = 0x0;
    static final int OP_TEXT = 0x1;
    static final int OP_BINARY = 0x2;
    static final int OP_TEXT_FINAL = 0x81;
    static final int OP_BINARY_FINAL = 0x82;
    static final int OP_CLOSE = 0x88;
    static final int OP_PING = 0x89;
    static final int OP_PONG = 0x8A;
    static final int MASKED_DATA = 0x80;
    static final String PING_PAYLOAD = "Pong";
    static final byte[] EMPTY_PAYLOAD = new byte[0];

    private void waitInputStream() throws IOException {
        InputStream is = socket.getInputStream();
        boolean pingSended = false;
        int opData = 0;
        byte[] payload = EMPTY_PAYLOAD;
        byte[] service = new byte[8]; //buffer for frame elements
        while (this.isOpen()) {
            try {
                int serviceLen = 2;
                if (is.read(service, 0, serviceLen) != serviceLen) {
                    close(-GOING_AWAY);
                }
                int b1 = service[0] & 0xFF;
// check op    
                switch (b1) {
                    case OP_TEXT_FINAL:
                    case OP_TEXT:
                    case OP_BINARY_FINAL:
                    case OP_BINARY: {
                        opData = b1 & 0xF;
                        b1 &= OP_FINAL;
                        break;
                    }
                    case OP_CONTINUATION:
                    case OP_FINAL:
                    case OP_PING:
                    case OP_PONG:
                    case OP_CLOSE: {
                        break;
                    }
                    default: {
                        close(PROTOCOL_ERROR);
                    }
                }

                int b2 = service[1] & 0xFF;
// get payload length
                long payloadLen = b2 & 0x7F;
                serviceLen = 0;
                if (payloadLen == 126L) {
                    serviceLen = 2;
                } else if (payloadLen == 127L) {
                    serviceLen = 8;
                }
                if (serviceLen > 0) {
                    if (is.read(service, 0, serviceLen) != serviceLen) {
                        close(-GOING_AWAY);
                    }
                    payloadLen = 0;
                    for (int i = 0; i < serviceLen; i++) {
                        payloadLen <<= 8;
                        payloadLen += (service[i] & 0xFF);
                    }
                }
// get mask 
                if ((b2 & MASKED_DATA) != 0) {
                    serviceLen = 4;
                    if (is.read(service, 0, serviceLen) != serviceLen) {
                        close(-GOING_AWAY);
                    }
                }
// check payloadLen                
                if (payloadLen + payload.length > maxMessageLength) {
                    is.skip(payloadLen);
                    close(MESSAGE_TOO_BIG);
                    continue;
                }
// get frame payload                
                byte[] framePayload
                        = new byte[(int) payloadLen & 0xFFFFFFFF]; //?
                if (is.read(framePayload) != framePayload.length) {
                    close(-GOING_AWAY);
                }
// unmask frame payload                
                if ((b2 & MASKED_DATA) != 0) {
                    unmaskPayload(service, framePayload);
                }

                switch (b1) {
                    case OP_CONTINUATION:
                        payload = combine(payload, framePayload);
                        break;
                    case OP_FINAL: {
                        payload = combine(payload, framePayload);
                        if (this.closureCode == 0) {
                            if (opData == OP_BINARY) {
                                handler.onMessage(this, payload);
                            } else {
                                handler.onMessage(this, new String(payload, "utf-8"));
                            }
                        }
                        opData = 0;
                        payload = EMPTY_PAYLOAD;
                        break;
                    }
                    case OP_PONG: {
                        if (pingSended
                                && (new String(framePayload)).equals(PING_PAYLOAD)) {
                            pingSended = false;
                            break;
                        }
                        close(PROTOCOL_ERROR);
                    }
                    case OP_PING: {
                        sendPayload(OP_PONG | OP_FINAL, framePayload, false);
                        break;
                    }
                    case OP_CLOSE: { // close handshake
                        if (closureCode == 0) {
                            if (framePayload.length > 1) {
                                this.closureCode
                                        = -((framePayload[0] << 8) + framePayload[1]);
                            } else {
                                closureCode = -NORMAL_CLOSURE;
                            }
                            sendPayload(OP_CLOSE, framePayload, false);
                        }
//??? 1001 -> 1006                        
                        this.socket.setSoTimeout(0);
                        this.socket.shutdownOutput(); 
                        this.socket.setSoLinger(true, 5); // seconds?
                        this.socket.close();
                        break;
                    }
                    default: {
                        close(PROTOCOL_ERROR);
                    }
                }
            } catch (SocketTimeoutException e) {
                if (!pingSended) {
                    pingSended = true;
                    sendPayload(OP_PING | OP_FINAL, PING_PAYLOAD.getBytes(), false);
                } else {
                    close(-GOING_AWAY);
                    break;
                }
            } catch (SocketException e) {
                if (closureCode == 0) {
                    close(INTERNAL_ERROR);
                }
                break;
            } catch (IOException e) { // Exception?
                close(INTERNAL_ERROR);
                break;
            }
        }
        if (Math.abs(closureCode) != NORMAL_CLOSURE) {
            handler.onError(this,
                    new ProtocolException("abnormal_closure"));
        }
        handler.onClose(this);
    }

    private void unmaskPayload(byte[] mask, byte[] payload) {
        for (int i = 0; i < payload.length; i++) {
            payload[i] ^= mask[i % 4];
        }
    }

    public static byte[] combine(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    private synchronized void sendPayload(int opData, byte[] payload, boolean masked)
            throws IOException {
        OutputStream os = this.socket.getOutputStream();
        os.write((byte) opData);
        byte[] mask = {0, 0, 0, 0};
        int b2 = masked ? MASKED_DATA : 0;
        int payloadLen = payload.length;
        if (payloadLen < 126) {
            os.write((byte) (b2 | payloadLen));
        } else if (payloadLen < 0x10000) {
            mask[0] = (byte) (b2 | 126);
            mask[1] = (byte) (payloadLen >>> 8);
            mask[2] = (byte) payloadLen;
            os.write(mask, 0, 3);
        } else {
            os.write((byte) (b2 | 127));
            os.write(mask); // 4 zero bytes of 64bit length
            for (int i = 3; i > 0; i--) {
                mask[i] = (byte) (payloadLen & 0xFF);
                payloadLen >>>= 8;
            }
            os.write(mask);
        }
        if (masked) {
            (new SecureRandom()).nextBytes(mask);
            os.write(mask);
            unmaskPayload(mask, payload);
        }
        os.write(payload);
        os.flush();
    }

    private void stream(int opData, InputStream is, boolean maskData)
            throws IOException {
        byte[] buf = new byte[2048];
        int pos = 0;
        int op = opData & 0xF;
        for (int len = is.read(buf); len >= 0; len = is.read(buf, pos, len)) {
            if (len < buf.length) {
                pos = len;
                len = buf.length - pos;
            } else {
                pos = 0;
                len = buf.length;
                sendPayload(op, buf, maskData);
                op = OP_CONTINUATION;
            }
        }
        sendPayload(op | OP_FINAL, Arrays.copyOf(buf, pos), maskData);
    }
}
