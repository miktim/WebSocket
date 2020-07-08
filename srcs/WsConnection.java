/*
 * WsConnection. WebSocket client/server connection, MIT (c) 2020 miktim@mail.ru
 *
 * Release notes:
 * - java SE 1.7+;
 * - RFC-6455: https://tools.ietf.org/html/rfc6455;
 * - WebSocket protocol version: 13;
 * - WebSocket extensions not supported
 *
 * Created: 2020-03-09
 */
package org.samples.java.websocket;

//import com.sun.net.httpserver.Headers;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.ProtocolException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.BufferUnderflowException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
//import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Random;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
//import java.util.Base64; // from java 1.8

public class WsConnection {

    public static final String WS_VERSION = "0.1.5";
    public static final int DEFAULT_HANDSHAKE_SO_TIMEOUT = 0;
    public static final int DEFAULT_CONNECTION_SO_TIMEOUT = 0;
    public static final int DEFAULT_MAX_MESSAGE_LENGTH = 2048;

// Closure status codes see:
//  https://tools.ietf.org/html/rfc6455#section-7.4
//  https://www.iana.org/assignments/websocket/websocket.xml#close-code-number 
//  https://developer.mozilla.org/en-US/docs/Web/API/CloseEvent
    public static final int NORMAL_CLOSURE = 1000; //*
    public static final int GOING_AWAY = 1001; //* shutdown or socket timeout
    public static final int PROTOCOL_ERROR = 1002; //* 
    public static final int UNSUPPORTED_DATA = 1003; //* unsupported opcode
    public static final int NO_STATUS = 1005; //* closing without code
    public static final int ABNORMAL_CLOSURE = 1006; // closing without close farame
    public static final int INVALID_DATA = 1007; // non utf-8 text, for example
    public static final int POLICY_VIOLATION = 1008; //
    public static final int MESSAGE_TOO_BIG = 1009; // *
    public static final int UNSUPPORTED_EXTENSION = 1010; // 
    public static final int INTERNAL_ERROR = 1011; //* server only
    public static final int TRY_AGAIN_LATER = 1013; //* server overloaded 

// request line header name
    private static final String REQUEST_LINE_HEADER = "_RequestLine";

    private final Socket socket;
    private Headers requestHeaders;
    WsHandler handler;
    private URI requestURI;
    private final boolean isClientConn;
    private final boolean isSecure;
    private int closureStatus = GOING_AWAY;
    int maxMessageLength = DEFAULT_MAX_MESSAGE_LENGTH;
    int handshakeSoTimeout = DEFAULT_HANDSHAKE_SO_TIMEOUT;
    int connectionSoTimeout = DEFAULT_CONNECTION_SO_TIMEOUT;
    boolean pingPong = false;

    public static void setKeyFile(File keyFile, String passphrase) {
        System.setProperty("javax.net.ssl.trustStore", keyFile.getAbsolutePath());
        System.setProperty("javax.net.ssl.trustStorePassword", passphrase);
//        System.setProperty("javax.net.ssl.keyStore", jksFile.getAbsolutePath());
//        System.setProperty("javax.net.ssl.keyStorePassword", passphrase);
    }

    public boolean isOpen() {
        return !(this.socket == null || this.socket.isClosed() || this.socket.isInputShutdown());
    }

    public boolean isSecure() {
        return isSecure;
    }

// websocket handshake    
    public void setHanshakeSoTimeout(int millis, boolean ping) {
        handshakeSoTimeout = millis;
    }

// websocket connection/ping    
    public void setConnectionSoTimeout(int millis, boolean ping) {
        connectionSoTimeout = millis;
        pingPong = ping;

    }

    public void setMaxMessageLength(int len) throws IllegalArgumentException {
        if (len <= 0) {
            throw new IllegalArgumentException();
        }
        this.maxMessageLength = len;
    }

    public boolean isClientSide() {
        return isClientConn;
    }

    public String getPath() {
        if (requestURI != null) {
            return this.requestURI.getPath();
        }
        return null;
    }

    public String getPeerHost() {
        try {
            if (isSecure) {
                return ((SSLSocket) socket).getSession().getPeerHost();
            } else {
                return ((InetSocketAddress) socket.getRemoteSocketAddress()).getHostString();
            }
        } catch (Exception e) {
            return null;
        }
    }

    public synchronized void send(byte[] message) throws IOException {
        if (this.closureStatus == 0) {
            sendPayload(OP_BINARY | OP_FINAL, message);
        } else {
            throw new IOException("socket_closed");
        }
    }

    public synchronized void send(String message) throws IOException {
        if (this.closureStatus == 0) {
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

    public int getMaxMessageLength() {
        return maxMessageLength;
    }

    public int getClosureStatus() {
        return this.closureStatus;
    }

    public synchronized void close() {
        close(NO_STATUS);
    }

    public synchronized void close(int status) {
        if (this.closureStatus == 0) {
            this.closureStatus = status;
            if (isOpen()) {
                int absCode = Math.abs(status);
                byte[] payload = new byte[0];
                if (absCode != NO_STATUS) {
                    payload = new byte[2];
                    payload[0] = (byte) (absCode >>> 8);
                    payload[1] = (byte) (absCode & 0xFF);
                }
                try {
                    sendPayload(OP_CLOSE | OP_FINAL, payload);
                } catch (IOException e) {
//                    handler.onError(this,
//                            new IOException("closure_error:" + status, e));
                }
            }
        }
    }

    public synchronized void setHandler(WsHandler handler) 
            throws NullPointerException {
        if (handler == null) throw new NullPointerException();
        this.handler = handler;
        if (this.isOpen()) this.handler.onOpen(this);
    }

    /* WebSocket Server connection */
    WsConnection(Socket s, WsHandler h) {
        this.socket = s;
        this.handler = h;
        this.isSecure = checkSecure();
        this.isClientConn = false;
    }

    final boolean checkSecure() {
        try {
            if (((SSLSocket) socket).getSession() != null) {
                return true;
            }
        } catch (Exception e) {
        }
        return false;
    }

// unused    
    void start() throws IOException, URISyntaxException, NoSuchAlgorithmException {
        handshakeClient();
        this.handler.onOpen(this);
        listenInputStream(false);
    }

    void handshakeClient() throws IOException, URISyntaxException, NoSuchAlgorithmException {
        this.requestHeaders = receiveHeaders(this.socket);
        String[] parts = requestHeaders.getFirst(REQUEST_LINE_HEADER).split(" ");
        this.requestURI = new URI(parts[1]);
        String upgrade = requestHeaders.getFirst("Upgrade");
        String key = requestHeaders.getFirst("Sec-WebSocket-Key");
//        int version = Integer.parseInt(requestHeaders.getFirst("Sec-WebSocket-Version"));
//        String protocol = requestHeaders.getFirst("Sec-WebSocket-Protocol"); // chat, superchat

        if (parts[0].equals("GET")
                && upgrade != null && upgrade.equals("websocket")
                && key != null) {
            Headers responseHeaders = new Headers();
            responseHeaders.add("Upgrade", "websocket");
            responseHeaders.add("Connection", "Upgrade,keep-alive");
            responseHeaders.add("Sec-WebSocket-Accept", sha1Hash(key));
            responseHeaders.add("Sec-WebSocket-Version", "13");
            sendHeaders(this.socket, responseHeaders, "HTTP/1.1 101 Upgrade");
        } else {
            sendHeaders(this.socket, null, "HTTP/1.1 400 Bad Request");
            socket.close();
            throw new ProtocolException("bad_request");
        }
        closureStatus = 0;
    }

    void sendHeaders(Socket socket, Headers hs, String line)
            throws IOException {
        StringBuilder sb = new StringBuilder(line);
        sb.append("\r\n");
        if (hs != null) {
            for (String hn : hs.keySet()) {
                sb.append(hn).append(": ").append(hs.getFirst(hn)).append("\r\n");
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
        this.requestHeaders = new Headers();
        String scheme = requestURI.getScheme();
        String host = requestURI.getHost();
        if (host == null || scheme == null) {
            throw new URISyntaxException(uri, "scheme and host required");
        }
        if (!(scheme.equals("ws") || scheme.equals("wss"))) {
            throw new URISyntaxException(uri, "unsupported scheme");
        }
        if (scheme.equals("wss")) {
            SSLSocketFactory factory
                    = (SSLSocketFactory) SSLSocketFactory.getDefault();
            this.socket
                    = (SSLSocket) factory.createSocket();
            this.isSecure = true;
        } else {
            this.isSecure = false;
            socket = new Socket();
        }
    }

    public void open()
            throws Exception {
        if (!isClientConn && isOpen()) {
            throw new SocketException();
        }
        try {
            int port = requestURI.getPort();
            if (port < 0) {
                port = isSecure ? 443 : 80;
            }
            this.socket.setSoTimeout(handshakeSoTimeout);
            this.socket.connect(
                    new InetSocketAddress(requestURI.getHost(), port));
            if (isSecure) {
//                ((SSLSocket) socket).setUseClientMode(true); // 
                ((SSLSocket) socket).startHandshake();
            }
            handshakeServer();
            this.socket.setSoTimeout(connectionSoTimeout);
            (new Thread(new WsClientThread(this))).start();
        } catch (Exception e) {
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
            connection.closureStatus = 0;
            connection.handler.onOpen(connection);
            try {
                this.connection.listenInputStream(pingPong);
            } catch (IOException e) {
                this.connection.handler.onError(connection, e);
            }
        }
    }

    private void handshakeServer()
            throws IOException, NoSuchAlgorithmException {
        String requestLine = "GET " + requestURI.getPath() + " HTTP/1.1";
        byte[] byteKey = new byte[16];
//        (new SecureRandom()).nextBytes(byteKey);
        (new Random()).nextBytes(byteKey);
        String key = base64Encode(byteKey);
        requestHeaders.add("Host", requestURI.getHost()
                + ":" + requestURI.getPort());
        requestHeaders.add("Upgrade", "websocket");
        requestHeaders.add("Connection", "Upgrade,keep-alive");
        requestHeaders.add("Sec-WebSocket-Key", key);
        requestHeaders.add("Sec-WebSocket-Version", "13");

        sendHeaders(this.socket, requestHeaders, requestLine);
        Headers headers = receiveHeaders(this.socket);
        try {
            if (!(headers.getFirst(REQUEST_LINE_HEADER).split(" ")[1].equals("101")
                    && headers.getFirst("Sec-WebSocket-Accept").equals(sha1Hash(key)))) {
                throw new ProtocolException("client_handshake");
            }
        } catch (Exception e) {
            throw new ProtocolException("client_handshake");
        }
    }

    static Headers receiveHeaders(Socket socket) throws IOException {
        Headers headers = new Headers();
        BufferedReader br = new BufferedReader(
                new InputStreamReader(socket.getInputStream()));
        String line = br.readLine();
        if (line.startsWith("\u0016\u0003\u0003")) {
            throw new javax.net.ssl.SSLHandshakeException("plain_text_socket");
        }
        headers.add(REQUEST_LINE_HEADER,
                line.replaceAll("  ", " ").trim());
        String[] pair = new String[0];
        while (true) {
            line = br.readLine();
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

    void listenInputStream(boolean pingPong) throws IOException {
        BufferedInputStream is = new BufferedInputStream(socket.getInputStream());
        boolean pingSended = false;
        int opData = 0;
        byte[] payload = EMPTY_PAYLOAD;
        byte[] service = new byte[8]; //buffer for frame header elements
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
                    if (is.read(service, 0, serviceLen) != serviceLen) {//java 1.7.79
                        closeDueTo(INVALID_DATA, new EOFException("frame_length"));
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
                        closeDueTo(INVALID_DATA, new EOFException("frame_mask"));
                    }
                }
// check payloadLen
                if (payloadLen + payload.length > maxMessageLength) {
                    is.skip(payloadLen);
                    closeDueTo(MESSAGE_TOO_BIG, new BufferUnderflowException());
                    continue;
                }
// get frame payload                
                byte[] framePayload = new byte[(int) payloadLen];
                if (is.read(framePayload) != framePayload.length) {
                    closeDueTo(INVALID_DATA, new EOFException("frame"));
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
                        if (this.closureStatus == 0) {
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
                            closeDueTo(PROTOCOL_ERROR,
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
                        if (closureStatus == 0) {
                            if (framePayload.length > 1) {
                                closureStatus = -(((framePayload[0] & 0xFF) << 8)
                                        + (framePayload[1] & 0xFF));
                            } else {
                                closureStatus = -NO_STATUS;
                            }
                            sendPayload(OP_CLOSE, framePayload);
                        }
//??? server 1001 -> 1006 browser | 1011 client
                        this.socket.setSoLinger(true, 2); // seconds
                        if (isSecure) {
                            ((SSLSocket) this.socket).close();
                        } else {
                            this.socket.shutdownInput();
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
                if (!pingSended && pingPong) {
                    pingSended = true;
                    sendPayload(OP_PING | OP_FINAL, PING_PAYLOAD.getBytes());
                } else {
                    closeDueTo(-GOING_AWAY, e);
                    break;
                }
            } catch (SocketException e) {
                if (closureStatus == 0) {
                    closeDueTo(INTERNAL_ERROR, e);
                }
                break;
            } catch (Exception e) { // Exception?
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

    private void closeDueTo(int closureStatus, Exception e) {
        if (closureException == null) {
            closureException = e;
        }
        close(closureStatus);
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
        if (!isOpen()) {
            return;
        }
        boolean masked = isClientConn;
        BufferedOutputStream os
                = new BufferedOutputStream(this.socket.getOutputStream());
        byte[] header = new byte[2];
        header[0] = (byte) opData;
        byte[] mask = {0, 0, 0, 0};
        int b2 = masked ? MASKED_DATA : 0;
        int payloadLen = payload.length;
        if (payloadLen < 126) {
            header[1] = (byte) (b2 | payloadLen);
        } else if (payloadLen < 0x10000) {
            header[1] = (byte) (b2 | 126);
            header = combine(header, header); // increase by 2 bytes
            header[2] = (byte) (payloadLen >>> 8);
            header[3] = (byte) payloadLen;
        } else {
            header[1] = (byte) (b2 | 127);
            header = combine(header, mask);// 4 zero bytes of 64bit payload length
            for (int i = 3; i > 0; i--) {
                mask[i] = (byte) (payloadLen & 0xFF);
                payloadLen >>>= 8;
            }
            header = combine(header, mask); // 32bit int payload length
        }
        if (masked) {
//            (new SecureRandom()).nextBytes(mask);
            (new Random()).nextBytes(mask); // enough
            header = combine(header, mask); // add mask
            byte[] maskedPayload = payload.clone();
            unmaskPayload(mask, maskedPayload);
            os.write(header);
            os.write(maskedPayload);
        } else {
            os.write(header);
            os.write(payload);
        }
        os.flush();
    }

    private static final int STREAM_BUFFER_SIZE = 2048;

    private void stream(int opData, InputStream is) throws IOException {
        BufferedInputStream bis = new BufferedInputStream(
                is, STREAM_BUFFER_SIZE);
        byte[] buf = new byte[STREAM_BUFFER_SIZE];
        int op = opData & 0xF;
        int len;
        for (len = bis.read(buf); len == buf.length; len = bis.read(buf)) {
            sendPayload(op, buf);
            op = OP_CONTINUATION;
        }
        sendPayload(op | OP_FINAL, Arrays.copyOf(buf, len > 0 ? len : 0));
    }
}
