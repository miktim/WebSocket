/*
 * WsConnection java SE 1.7+
 * MIT (c) 2020 miktim@mail.ru
 * RFC-6455: https://tools.ietf.org/html/rfc6455
 *
 * Release notice:
 * - WebSocket extensions not supported;
 * - protocol version: 13 
 *
 * Created: 2020-03-09
 */
package org.samples.java.websocket;

import com.sun.net.httpserver.Headers;
import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ProtocolException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.BufferOverflowException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
//import java.util.Base64; // from java 1.8

public class WsConnection {

// closure codes see RFC: https://tools.ietf.org/html/rfc6455#section-7.4 
// https://developer.mozilla.org/en-US/docs/Web/API/CloseEvent
    public static final int NORMAL_CLOSURE = 1000; //*
    public static final int GOING_AWAY = 1001; //* shutdown or socket timeout
    public static final int PROTOCOL_ERROR = 1002; //* 
    public static final int UNSUPPORTED_DATA = 1003; //* unsupported opcode
    public static final int NO_STATUS = 1005; // closing without code
    public static final int ABNORMAL_CLOSURE = 1006; // closing without close farame
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
    private final URI requestURI;
    private final boolean isClientConn;
    private final boolean isSecure;
    private int closureCode = 0;
    private int maxMessageLength = DEFAULT_MAX_MESSAGE_LENGTH;

    public synchronized void send(byte[] message) throws IOException {
        if (this.closureCode == 0) {
            sendPayload(OP_BINARY | OP_FINAL, message);
        } else {
            throw new IOException("socket_closed");
        }
    }

    public synchronized void send(String message) throws IOException {
        if (this.closureCode == 0) {
            sendPayload(OP_TEXT | OP_FINAL, message.getBytes());
        } else {
            throw new IOException("socket_closed");
        }
    }

    public synchronized void streamText(InputStream is) throws IOException {
        stream(OP_TEXT, is);
    }

    public synchronized void streamBinary(InputStream is) throws IOException {
        stream(OP_BINARY, is);
    }

    public boolean isSecure() {
        return isSecure;
    }

    void setMaxMessageLength(int len) {
        maxMessageLength = len;
    }

    public int getMaxMessageLength() {
        return maxMessageLength;
    }

    public boolean isOpen() {
        return !(this.socket.isClosed() || this.socket.isInputShutdown());
    }

    public int getClosureCode() {
        return this.closureCode;
    }

    public synchronized void close() {
        close(NORMAL_CLOSURE);
    }

    public synchronized void close(int code) {
        if (this.closureCode == 0) {
            this.closureCode = code;
            if (isOpen()) {
                int absCode = Math.abs(code);
                byte[] payload = new byte[2];
                payload[0] = (byte) (absCode >>> 8);
                payload[1] = (byte) (absCode & 0xFF);
                try {
                    sendPayload(OP_CLOSE | OP_FINAL, payload);
                } catch (IOException e) {
                    handler.onError(this,
                            new IOException("closure_error:" + code, e));
                }
            }
        }
    }

    public String getPath() {
        return this.requestURI.getPath();
    }

    /* WebSocket Server connection */
    WsConnection(Socket s, Headers hs, WsHandler hd) throws URISyntaxException {
        this.socket = s;
        this.requestHeaders = hs;
        this.handler = hd;
        this.requestURI = new URI(
                requestHeaders.getFirst(REQUEST_LINE_HEADER).split(" ")[1]);
        this.isSecure = checkSecure();
        this.isClientConn = false;
    }

    void start() throws IOException, NoSuchAlgorithmException {
        handshakeClient();
        this.handler.onOpen(this);
        waitInputStream();
    }

    private boolean checkSecure() {
        try {
            if (((SSLSocket) socket).getHandshakeSession() != null) {
                return true;
            }
        } catch (Exception e) {
        }
        return false;
    }

    private void handshakeClient() throws IOException, NoSuchAlgorithmException {
        String upgrade = requestHeaders.getFirst("Upgrade");
        String key = requestHeaders.getFirst("Sec-WebSocket-Key");
//        int version = Integer.parseInt(requestHeaders.getFirst("Sec-WebSocket-Version"));
//        String protocol = requestHeaders.getFirst("Sec-WebSocket-Protocol"); // chat, superchat

        if (upgrade == null || key == null || !upgrade.equals("websocket")) {
            sendHeaders(this.socket, null, "HTTP/1.1 400 Bad Request");
            socket.close();
            throw new ProtocolException("bad_request");
        } else {
            Headers responseHeaders = new Headers();
            responseHeaders.set("Upgrade", "websocket");
            responseHeaders.set("Connection", "upgrade,keep-alive");
            responseHeaders.set("Sec-WebSocket-Accept", sha1Hash(key));
            responseHeaders.set("Sec-WebSocket-Version", "13");
            sendHeaders(this.socket, responseHeaders, "HTTP/1.1 101 Upgrade");
        }
    }

    void sendHeaders(Socket socket, Headers h, String line)
            throws IOException {
        StringBuilder sb = new StringBuilder(line);
        sb.append("\r\n");
        if (h != null) {
            for (String hn : h.keySet()) {
                sb.append(hn).append(": ").append(h.getFirst(hn)).append("\r\n");
            }
        }
        sb.append("\r\n");
        socket.getOutputStream().write(sb.toString().getBytes());
        socket.getOutputStream().flush();
    }

    /* WebSocket client connection */
    public WsConnection(String uri, WsHandler handler) throws
            URISyntaxException, NullPointerException,
            IOException, NoSuchAlgorithmException {
        this.isClientConn = true;
        if (handler == null) {
            throw new NullPointerException();
        }
        this.handler = handler;
        this.requestURI = new URI(uri);
        requestHeaders = new Headers();
        String scheme = requestURI.getScheme();
        String host = requestURI.getHost();
        if (host == null || scheme == null) {
            throw new URISyntaxException(uri, "scheme and host required");
        }
        if (!(scheme.equals("ws") || scheme.equals("wss"))) {
            throw new URISyntaxException(uri, "unsupported scheme");
        }
        int port = requestURI.getPort();
        if (port < 0) {
            port = scheme.equals("wss") ? 443 : 80;
        }
        if (scheme.equals("wss")) {
            SSLSocketFactory factory
                    = (SSLSocketFactory) SSLSocketFactory.getDefault();
            this.socket
                    //                    = (SSLSocket) factory.createSocket(host, port);
                    = (SSLSocket) factory.createSocket();
            this.isSecure = true;
        } else {
            this.isSecure = false;
//            socket = new Socket(host, port);
            socket = new Socket();
        }
    }

    public void open()
            throws IOException, NoSuchAlgorithmException {
        if (!isClientConn) {
            throw new SocketException();
        }
        try {
            int port = requestURI.getPort();
            if (port < 0) {
                port = isSecure ? 443 : 80;
            }
            if (isSecure) {
                ((SSLSocket) socket).connect(
                        new InetSocketAddress(requestURI.getHost(), port));
                ((SSLSocket) socket).startHandshake();
            } else {
                socket.connect(
                        new InetSocketAddress(requestURI.getHost(), port));
            }
            /*
            if (isSecure) {
                ((SSLSocket) socket).startHandshake();
            }
             */
            handshakeServer();
            this.handler.onOpen(this);
            this.socket.setSoTimeout(0);
            (new Thread(new WsClientThread(this))).start();
        } catch (IOException | NoSuchAlgorithmException e) {
            try {
                this.socket.close();
            } catch (IOException ie) {
            }
            throw e;
        }
    }

    private class WsClientThread implements Runnable {

        private final WsConnection connection;

        WsClientThread(WsConnection conn) {
            this.connection = conn;
        }

        @Override
        public void run() {
            try {
                this.connection.waitInputStream();
            } catch (IOException e) {

            }
        }
    }

    private void handshakeServer()
            throws IOException, NoSuchAlgorithmException {
        String requestLine = "GET " + requestURI.getPath() + " HTTP/1.1";
        byte[] byteKey = new byte[16];
        (new SecureRandom()).nextBytes(byteKey);
        String key = base64Encode(byteKey);
        requestHeaders.set("Host", requestURI.getHost());
        requestHeaders.set("Upgrade", "websocket");
        requestHeaders.set("Connection", "Upgrade");
        requestHeaders.set("Sec-WebSocket-Key", key);
        requestHeaders.set("Sec-WebSocket-Version", "13");

        sendHeaders(this.socket, requestHeaders, requestLine);
        Headers headers = receiveHeaders(this.socket);
        try {
            if (!(headers.getFirst(REQUEST_LINE_HEADER).split(" ")[1].equals("101")
                    && headers.getFirst("Sec-WebSocket-Accept").equals(sha1Hash(key)))) {
                throw new ProtocolException();
            }
        } catch (NullPointerException e) {
            throw new ProtocolException();
        }
    }

    static Headers receiveHeaders(Socket socket) throws IOException {
        Headers headers = new Headers();
        BufferedReader br = new BufferedReader(
                new InputStreamReader(socket.getInputStream()));
        headers.add(REQUEST_LINE_HEADER,
                br.readLine().replaceAll("  ", " ").trim());
        String[] pair = new String[0];
        while (true) {
            String line = br.readLine();
            if (line == null || line.isEmpty()) {
                break;
            }
            if (line.startsWith(" ") || line.startsWith("\t")) { // continued
                headers.add(pair[0], headers.getFirst(pair[0]) + line);
                continue;
            }
            pair = line.split(":");
            headers.add(pair[0].trim(), pair[1].trim());
        }
        return headers;
    }

    private static String sha1Hash(String key) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        return base64Encode(
                md.digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11")
                        .getBytes()));
    }

    public static String base64Encode(byte[] b) {
        final byte[] chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".getBytes();
        int i = 0;
        int l = 0;
        byte[] b64 = new byte[((b.length + 2) / 3 * 4)];
        Arrays.fill(b64, (byte) '=');
        while (i < b.length) {
            int k = Math.min(3, b.length - i);
            int bits = 0;
            int shift = 16;
            for (int j = 0; j < k; j++) {
                bits += ((b[i++] & 0xFF) << shift);
                shift -= 8;
            }
            shift = 18;
            for (int j = 0; j <= k; j++) {
                b64[l++] = chars[(bits >> shift) & 0x3F];
                shift -= 6;
            }
        }
        return new String(b64);
//        return Base64.getEncoder().encodeToString(b);
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
                int b1 = is.read();
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
                        closeDueTo(PROTOCOL_ERROR, new ProtocolException());
                    }
                }
                int b2 = is.read();
// get payload length
                long payloadLen = b2 & 0x7F;
                int serviceLen = 0;
                if (payloadLen == 126L) {
                    serviceLen = 2;
                } else if (payloadLen == 127L) {
                    serviceLen = 8;
                }
                if (serviceLen > 0) {
                    if (is.read(service, 0, serviceLen) != serviceLen) {
                        closeDueTo(-GOING_AWAY, new EOFException("frame_length"));
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
                        closeDueTo(-GOING_AWAY, new EOFException("frame_mask"));
                    }
                }
// check payloadLen                
                if (payloadLen + payload.length > maxMessageLength) {
                    is.skip(payloadLen);
                    closeDueTo(MESSAGE_TOO_BIG, new BufferOverflowException());
                    continue;
                }
// get frame payload                
                byte[] framePayload
                        = new byte[(int) payloadLen & 0xFFFFFFFF]; //?
                if (is.read(framePayload) != framePayload.length) {
                    closeDueTo(-GOING_AWAY, new EOFException("frame"));
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
                        } else {
                            handler.onError(this,
                                    new ProtocolException("unexpected_pong"));
                        }
                        pingSended = false;
                        break;
                    }
                    case OP_PING: {
                        sendPayload(OP_PONG | OP_FINAL, framePayload);
                        break;
                    }
                    case OP_CLOSE: { // close handshake
                        if (closureCode == 0) {
                            if (framePayload.length > 1) {
                                closureCode = -(((framePayload[0] & 0xFF) << 8)
                                        + (framePayload[1] & 0xFF));
                            } else {
                                closureCode = -NO_STATUS;
                            }
                            sendPayload(OP_CLOSE, framePayload);
                        }
//??? server 1001 -> 1006 browser | 1011 client                     
//                        this.socket.shutdownOutput();
                        this.socket.setSoLinger(true, 3); // seconds
                        if (isSecure) {
                            ((SSLSocket) this.socket).close();
                        } else {
                            this.socket.close();
                        }
                        break;
                    }
                    default: {
                        closeDueTo(PROTOCOL_ERROR,
                                new ProtocolException("unknown_frame"));
                    }
                }
            } catch (SocketTimeoutException e) {
                if (!pingSended) {
                    pingSended = true;
                    sendPayload(OP_PING | OP_FINAL, PING_PAYLOAD.getBytes());
                } else {
                    closeDueTo(-GOING_AWAY, e);
                    break;
                }
            } catch (SocketException e) {
                if (closureCode == 0) {
                    closeDueTo(INTERNAL_ERROR, e);
                }
                break;
            } catch (IOException e) { // Exception?
                closeDueTo(INTERNAL_ERROR, e);
                break;
            }
        }
        if (closureException != null) {
            handler.onError(this, closureException);
        }
        handler.onClose(this);
    }

    private Exception closureException = null;

    private void closeDueTo(int closureCode, Exception e) {
        close(closureCode);
        if (closureException == null) {
            closureException = e;
        }
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

    private synchronized void sendPayload(int opData, byte[] payload)
            throws IOException {
        //       if (this.socket.isClosed()) {
        //           return;
        //       }
        boolean masked = isClientConn;
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
            byte[] maskedPayload = payload.clone();
            unmaskPayload(mask, maskedPayload);
            os.write(maskedPayload);
        } else {
            os.write(payload);
        }
        os.flush();
    }

    private void stream(int opData, InputStream is)
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
                sendPayload(op, buf);
                op = OP_CONTINUATION;
            }
        }
        sendPayload(op | OP_FINAL, Arrays.copyOf(buf, pos));
    }

}
