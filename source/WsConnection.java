/*
 * WsConnection java SE 1.8+
 * MIT (c) 2020 miktim@mail.ru
 * RFC-6455: https://tools.ietf.org/html/rfc6455
 *
 * Release notice:
 * - WebSocket extensions not supported;
 * - supported protocol versions: 13 
 * - connection methods not thread-safe
 *
 * Created: 2020-03-09
 */
package org.samples.java.wsserver;

import com.sun.net.httpserver.Headers;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ProtocolException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.BufferUnderflowException;
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
    public static final int ABNORMAL_CLOSURE = 1006; // closing connection without op
    public static final int INVALID_DATA = 1007; // non utf-8 text, for example
    public static final int MESSAGE_TOO_BIG = 1009; //*
    public static final int UNSUPPORTED_EXTENSION = 1010; //* 
    public static final int INTERNAL_ERROR = 1011; //*
// 
    public static final int MAX_MESSAGE_LENGTH = 2048;
// request line header name
    static final String REQUEST_LINE_HEADER = "_RequestLine";

    private final Socket socket;
    private final Headers requestHeaders;
    private final WsHandler handler;
    private int closureCode = 0;
    private int maxMessageLength = MAX_MESSAGE_LENGTH;

    public void streamText(InputStream is) throws IOException {
        stream(OP_TEXT, is, true);
    }

    public void streamBinary(InputStream is) throws IOException {
        stream(OP_BINARY, is, true);
    }

    public void send(byte[] message) throws IOException {
        sendPayload(OP_BINARY | OP_FINAL, message, false);
    }

    public void send(String message) throws IOException {
        sendPayload(OP_TEXT | OP_FINAL, message.getBytes(), false);
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
        close(NORMAL_CLOSURE);
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

    private class WSException extends Exception {

        private final int code;
        private final Exception cause;

        WSException(int closureCode, Exception cause) {
            code = closureCode;
            this.cause = cause;
        }

        int getClosureCode() {
            return code;
        }

        @Override
        public Exception getCause() {
            return cause;
        }
    }

    private void handshakeClient() throws IOException {
        String upgrade = requestHeaders.getFirst("Upgrade");
        String key = requestHeaders.getFirst("Sec-WebSocket-Key");
        int version = Integer.parseInt(requestHeaders.getFirst("Sec-WebSocket-Version"));
        String protocol = requestHeaders.getFirst("Sec-WebSocket-Protocol"); // chat, superchat

        if (upgrade == null || key == null //|| protocol == null
                || !upgrade.equals("websocket") || version != 13) {
            sendResponseHeaders(null, "400 Bad Request");
            socket.close();
            throw new ProtocolException();
        } else {
            Headers responseHeaders = new Headers();
            responseHeaders.set("Upgrade", "websocket");
            responseHeaders.set("Connection", "upgrade,keep-alive");
            responseHeaders.set("Sec-WebSocket-Accept", sha1Hash(key));
//            responseHeaders.set("Sec-WebSocket-Version", "" + version);
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

    private void waitInputStream() throws IOException {
        InputStream is = socket.getInputStream();
        boolean pingSended = false;
        int opData = 0;
        byte[] payload = null;
        while (this.isOpen()) {
            try {
                int b1 = is.read();
                if (b1 == -1) {
                    throw new WSException(PROTOCOL_ERROR, new EOFException());
                }

                if ((b1 & OP_EXTENSONS) > 0) {
                    throw new WSException(UNSUPPORTED_EXTENSION, new ProtocolException());
                }
// check op
                switch (b1) {
                    case OP_TEXT_FINAL: {

                    }
                    case OP_TEXT: {

                    }
                    case OP_BINARY_FINAL: {

                    }
                    case OP_BINARY: {
                        opData = b1 & 0xF;
                        b1 &= OP_FINAL;
                        break;
                    }
                    case OP_PING: {

                    }
                    case OP_PONG: {

                    }
                    case OP_CLOSE: {
                        break;
                    }
                    default: {
                        throw new WSException(UNSUPPORTED_DATA, new ProtocolException()); // IO ProtocolException
                    }
                }

                int b2 = is.read();
                if (b2 == -1) {
                    throw new WSException(PROTOCOL_ERROR, new EOFException());
                }
                byte[] mask = (b2 & MASKED_DATA) != 0 ? new byte[4] : null;
                long payloadLen = b2 & 0x7F;
                if (payloadLen == 126L) {
                    payloadLen = (is.read() << 8) + is.read();
                } else if (payloadLen == 127L) { //!!! int = 32 bits
                    payloadLen = 0L;
                    for (int i = 0; i < 8; i++) {
                        payloadLen <<= 8 + is.read();
                    }
                    if (payloadLen > maxMessageLength) {
                        throw new WSException(MESSAGE_TOO_BIG, new BufferUnderflowException());
                    }
                }
                if (mask != null && is.read(mask) != mask.length) {
                    throw new WSException(PROTOCOL_ERROR, new EOFException());
                }
                byte[] framePayload
                        = new byte[(int) payloadLen & 0xFFFFFFFF];
                if (is.read(framePayload) != framePayload.length) {
                    throw new WSException(PROTOCOL_ERROR, new EOFException());
                }
                if (mask != null) {
                    unmaskPayload(mask, framePayload);
                }
                if ((b1 & 0xF) == OP_CONTINUATION) {
                    if (payload == null) {
                        payload = framePayload;
                    } else {
                        if ((payload.length + framePayload.length) <= maxMessageLength) {
                            payload = combine(payload, framePayload);
                        } else {
                            throw new WSException(MESSAGE_TOO_BIG, new BufferUnderflowException());
                        }
                    }
                }
                switch (b1) {
                    case OP_CONTINUATION:
                        break;
                    case OP_FINAL: {
                        if (opData == OP_BINARY) {
                            handler.onMessage(this, payload);
                        } else {
                            handler.onMessage(this, new String(payload));
                        }
                        opData = 0;
                        payload = null;
                        break;
                    }
                    case OP_PONG: {
                        if (pingSended
                                && (new String(framePayload)).equals(PING_PAYLOAD)) {
                            pingSended = false;
                            break;
                        }
                        throw new WSException(PROTOCOL_ERROR, new ProtocolException());
                    }
                    case OP_PING: {
                        sendPayload(OP_PONG | OP_FINAL, framePayload, false);
                        break;
                    }
                    case OP_CLOSE: { // close handshake
                        if (closureCode == 0) {
                            if (framePayload.length > 1) {
                                this.closureCode
                                        = -((payload[0] << 8) + framePayload[1]);
                            } else {
                                closureCode = -NORMAL_CLOSURE;
                            }
                            sendPayload(OP_CLOSE | OP_FINAL, framePayload, false);
                            if (closureCode != -NORMAL_CLOSURE) {
                                handler.onError(this, new ProtocolException()); // ????
                            }
                        }
                        this.socket.close();
                        break;
                    }
                    default: {
                        throw new WSException(UNSUPPORTED_DATA, new ProtocolException());
                    }
                }
            } catch (SocketTimeoutException e) {
                if (!pingSended) {
                    pingSended = true;
                    sendPayload(OP_PING | OP_FINAL, PING_PAYLOAD.getBytes(), false);
                } else {
                    closureCode = -GOING_AWAY;
                    handler.onError(this, e);
                    this.socket.close();
                    break;
                }
            } catch (WSException e) {
                close(e.getClosureCode());
                handler.onError(this, e.getCause());
                break;
            } catch (IOException e) {
                close(INTERNAL_ERROR);
                handler.onError(this, e);
                this.socket.close();
                break;
            }
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

    void close(int code) {
        if (this.closureCode == 0) {
            this.closureCode = code;
            if (isOpen()) {
                byte[] payload = new byte[2];
                payload[0] = (byte) (code >>> 8);
                payload[1] = (byte) (code & 0xFF);
                try {
//                    this.socket.shutdownInput();
                    sendPayload(OP_CLOSE | OP_FINAL, payload, false);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void sendPayload(int opData, byte[] payload, boolean maskData)
            throws IOException {
        OutputStream os = this.socket.getOutputStream();
        synchronized (os) {
            os.write((byte) opData);
            byte[] mask = {0, 0, 0, 0};
            int b2 = maskData ? MASKED_DATA : 0;
            int payloadLen = payload.length;
            if (payloadLen < 126) {
                os.write((byte) (b2 | payloadLen));
            } else if (payloadLen <= 0xFFFF) {
                mask[0] = (byte) (b2 | 126);
                mask[1] = (byte) (payloadLen >>> 8);
                mask[3] = (byte) (byte) payloadLen;
                os.write(mask, 0, 3);
            } else {
                os.write((byte) (b2 | 127));
                os.write(mask); // 4 zero bytes of 64bit length
                for (int i = 4; i > 0; i--) {
                    mask[i] = (byte) (payloadLen & 0xFF);
                    payloadLen >>>= 8;
                }
                os.write(mask);
            }
            if (maskData) {
                (new SecureRandom()).nextBytes(mask);
                os.write(mask);
                unmaskPayload(mask, payload);
            }
            os.write(payload);
            os.flush();
        }
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
    /*
    private void printHeaders(Headers hs) {
        for (String hn : hs.keySet()) {
            System.out.println(hn + ":" + hs.getFirst(hn));
        }
    }
     */
}
