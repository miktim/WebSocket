/*
 * WsConnection. WebSocket connection, MIT (c) 2020 miktim@mail.ru
 *
 * Release notes:
 * - java SE 1.7+;
 * - RFC-6455: https://tools.ietf.org/html/rfc6455;
 * - WebSocket protocol version: 13;
 * - WebSocket extensions not supported
 *
 * Created: 2020-03-09
 */
package org.miktim.websocket;

//import com.sun.net.httpserver.Headers;
import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
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
import java.nio.BufferUnderflowException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidParameterException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Random;
//import java.security.SecureRandom;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
//import java.util.Base64; // java 1.8+

public class WsConnection extends Thread {

    public static final String VERSION = "2.2.2";
    public static final int DEFAULT_HANDSHAKE_SO_TIMEOUT = 5000; // open/close handshake
    public static final int DEFAULT_CONNECTION_SO_TIMEOUT = 20000; // ping

// Closure status codes see:
//  https://tools.ietf.org/html/rfc6455#section-7.4
//  https://www.iana.org/assignments/websocket/websocket.xml#close-code-number 
//  https://developer.mozilla.org/en-US/docs/Web/API/CloseEvent

    public static final int NORMAL_CLOSURE = 1000; //*
    public static final int GOING_AWAY = 1001; //* 
    public static final int PROTOCOL_ERROR = 1002; //* 
    public static final int UNSUPPORTED_DATA = 1003; //* 
    public static final int NO_STATUS = 1005; //* 
    public static final int ABNORMAL_CLOSURE = 1006; //* 
    public static final int INVALID_DATA = 1007; // 
    public static final int POLICY_VIOLATION = 1008; //
    public static final int MESSAGE_TOO_BIG = 1009; //*
    public static final int UNSUPPORTED_EXTENSION = 1010; // 
    public static final int INTERNAL_ERROR = 1011; //* 
    public static final int TRY_AGAIN_LATER = 1013; //*
    

// request line header name
    private static final String REQUEST_LINE_HEADER = "_RequestLine";

    private final Socket socket;
    private WsHandler handler;
    private Headers peerHeaders;
    private URI requestURI;
    private final boolean isClientSide;
    private final boolean isSecure;
    private int closeCode = GOING_AWAY;
    private String closeReason = "";
    private int handshakeSoTimeout = DEFAULT_HANDSHAKE_SO_TIMEOUT;
    private int connectionSoTimeout = DEFAULT_CONNECTION_SO_TIMEOUT;
    private boolean pingEnabled = true;
    private final MessageDigest messageDigest = MessageDigest.getInstance("SHA-1");
    
    public boolean isOpen() {
        return closeCode == 0;
    }

    public boolean isSecure() {
        return isSecure;
    }

    void setHandshakeSoTimeout(int millis) {
        handshakeSoTimeout = millis;
    }

    public int getHandshakeSoTimeout() {
        return handshakeSoTimeout;
    }

    void setConnectionSoTimeout(int millis, boolean ping)
            throws SocketException {
        connectionSoTimeout = millis;
        pingEnabled = ping;
    }

    public int getConnectionSoTimeout() {
        return connectionSoTimeout;
    }

    public boolean isPingEnabled() {
        return pingEnabled && connectionSoTimeout > 0;
    }

    public static final int DEFAULT_MAX_MESSAGE_LENGTH = 8192;
    private int maxMessageLength = DEFAULT_MAX_MESSAGE_LENGTH; //in bytes
    private boolean streamingEnabled = false;

    void setMaxMessageLength(int maxLen, boolean enableStreaming) {
        maxMessageLength = maxLen;
        streamingEnabled = enableStreaming;
    }

    public int getMaxMessageLength() {
        return maxMessageLength;
    }

    public boolean isStreamingEnabled() {
        return streamingEnabled;
    }

    private String subprotocols = null;
    private String agreedSubprotocol = null;

    void setSubprotocol(String sub) {
        subprotocols = sub;
    }

    public String getSubprotocol() {
        return subprotocols;
    }

    public String getAgreedSubprotocol() {
        return agreedSubprotocol;
    }

// listener side
    private void setAgreedSubprotocol(Headers rq, Headers rs) {
        String sub = rq.getFirst("Sec-WebSocket-Protocol");
        if (sub == null || subprotocols == null || subprotocols.trim().isEmpty()) {
            return;
        }
        String[] rqSubl = sub.replaceAll(" ", "").split(",");
        String[] lsSubl = subprotocols.replaceAll(" ", "").split(",");
        for (String subRq : rqSubl) {
            for (String subLs : lsSubl) {
                if (subRq.equals(subLs)) {
                    agreedSubprotocol = subRq;
                    rs.add("Sec-WebSocket-Protocol", subRq);
                    return;
                }
            }
        }
    }

// client side
    private boolean setAgreedSubprotocol(String rsSub) {
        if ((agreedSubprotocol = rsSub) == null) {
            return true; //???
        } else if (subprotocols == null) {
            return false;
        }
        String[] subl = subprotocols.replaceAll(" ", "").split(",");
        for (String sub : subl) {
            if (sub.equals(agreedSubprotocol)) {
                return true;
            }
        }
        return false;
    }

    public synchronized void setHandler(WsHandler handler)
            throws NullPointerException {
        if (handler == null) {
            throw new NullPointerException();
        }
        this.handler = handler;
        if (this.isOpen()) {
            this.handler.onOpen(this);
        }
    }

    public WsHandler getHandler() {
        return handler;
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
    
    public String getSSLSessionProtocol() {
        if(this.isSecure() && this.isSocketOpen()) 
            return ((SSLSocket)this.socket).getSession().getProtocol();
        return null;
    }

    public void send(byte[] message) throws IOException {
        syncSend(message, false);
//        syncSend(new ByteArrayInputStream(message), false);
    }

    public void send(String message) throws IOException {
        syncSend(message.getBytes(StandardCharsets.UTF_8), true);
//        syncSend(new ByteArrayInputStream(
//                message.getBytes(StandardCharsets.UTF_8)), true);
    }

    public void send(InputStream is, boolean isText) throws IOException {
        syncSend(is, isText);
    }

    public static final int STREAM_PAYLOAD_LENGTH = 8192;

    private synchronized void syncSend(Object obj, boolean isText) throws IOException {
        if (obj == null) {
            throw new NullPointerException();
        }
        int op = isText ? OP_TEXT : OP_BINARY;
        try {
            if (obj instanceof byte[]) {
                sendPayload(op | OP_FINAL, (byte[]) obj);
            } else if (obj instanceof InputStream) {
// BufferedInputStream is not good for large payloads?
                byte[] buf = new byte[STREAM_PAYLOAD_LENGTH];
                InputStream is = (InputStream) obj;
                int len = 0;
                while (true) {
                    try {
                        len = this.readFully(is, buf, 0, buf.length);
                    } catch (IOException e) {
                        this.close(INTERNAL_ERROR, "");
                        throw e;
                    }
                    if (len == buf.length) {
                        sendPayload(op, buf);
                        op = OP_CONTINUATION;
                    } else {
// be sure to send the final frame even if eof is detected!            
                        sendPayload(op | OP_FINAL,
                                Arrays.copyOf(buf, len >= 0 ? len : 0));
                        break;
                    }
                }
            } else {
                throw new InvalidParameterException();
            }
        } catch (IOException e) {
            this.close(ABNORMAL_CLOSURE, "");
            throw e;
        }
    }

    public int getCloseCode() {
        return this.closeCode;
    }

    public String getCloseReason() {
        return this.closeReason;
    }

    public void close() {
        close(NO_STATUS, "");
    }

    public synchronized void close(int status, String reason) {
        if (this.closeCode == 0) {
            status = (status < 1000 || status > 4999) ? NO_STATUS : status;
            byte[] payload = new byte[0];
            if (status != NO_STATUS) {
                payload = new byte[2];
                payload[0] = (byte) (status >>> 8);
                payload[1] = (byte) (status & 0xFF);
                byte[] msgBytes = (reason == null ? "" : reason).getBytes(StandardCharsets.UTF_8);
                payload = combine(payload,
                        Arrays.copyOf(msgBytes, Math.min(msgBytes.length, 123)));
            }
            try {
                this.socket.setSoTimeout(handshakeSoTimeout);
                pingEnabled = false;
                sendPayload(OP_CLOSE, payload);
                this.closeCode = status; // disable sending data
            } catch (IOException e) {
                this.closeCode = status;
//                handler.onError(this, new IOException("closure_error", e));
            }
        }
    }

// WebSocket Server connection
    WsConnection(Socket s, WsHandler h) throws NoSuchAlgorithmException {
        this.socket = s;
        this.handler = h;
        this.isSecure = checkSecure();
        this.isClientSide = false;
    }

    private boolean checkSecure() {
        try {
            if (((SSLSocket) socket).getSession() != null) {
                return true;
            }
        } catch (Exception e) {
        }
        return false;
    }

    void handshakeClient() throws IOException, URISyntaxException {
        closeCode = PROTOCOL_ERROR;
        this.peerHeaders = receiveHeaders(this.socket);
        String[] parts = peerHeaders.getFirst(REQUEST_LINE_HEADER).split(" ");
        this.requestURI = new URI(parts[1]);
        String upgrade = peerHeaders.getFirst("Upgrade");
        String key = peerHeaders.getFirst("Sec-WebSocket-Key");
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
            setAgreedSubprotocol(this.peerHeaders, responseHeaders);
            sendHeaders(this.socket, responseHeaders, "HTTP/1.1 101 Upgrade");
        } else {
            sendHeaders(this.socket, null, "HTTP/1.1 400 Bad Request");
            throw new ProtocolException("bad_request");
        }
        closeCode = 0;
    }

    private void sendHeaders(Socket socket, Headers hs, String line)
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

//  WebSocket client connection
    WsConnection(String uri, WsHandler handler) throws
            URISyntaxException, NullPointerException,
            IOException, NoSuchAlgorithmException {
        this.isClientSide = true;
        if (uri == null || handler == null) {
            throw new NullPointerException();
        }
        this.handler = handler;
        this.requestURI = new URI(uri);
        String scheme = requestURI.getScheme();
        String host = requestURI.getHost();
        if (host == null || scheme == null) {
            throw new URISyntaxException(uri, "scheme_and_host_required");
        }
        if (!(scheme.equals("ws") || scheme.equals("wss"))) {
            throw new URISyntaxException(uri, "unsupported_scheme");
        }
        if (scheme.equals("wss")) {
            this.isSecure = true;
            SSLSocketFactory factory
                    = (SSLSocketFactory) SSLSocketFactory.getDefault();
            this.socket
                    = (SSLSocket) factory.createSocket();
        } else {
            this.isSecure = false;
            socket = new Socket();
        }
    }

    void open() throws IOException {
        if (!isClientSide || this.socket.isConnected()) {
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
//            this.start(); //
        } catch (IOException e) {
            closeSocket();
            throw e;
        }
    }

    private void handshakeServer() throws IOException {
        closeCode = PROTOCOL_ERROR;
        String requestLine = "GET " + requestURI.getPath() + " HTTP/1.1";
        byte[] byteKey = new byte[16];
//        (new SecureRandom()).nextBytes(byteKey);
        (new Random()).nextBytes(byteKey);
        String key = base64Encode(byteKey);
        Headers headers = new Headers();
        headers.add("Host", requestURI.getHost()
                + ":" + requestURI.getPort());
        headers.add("Upgrade", "websocket");
        headers.add("Connection", "Upgrade,keep-alive");
        headers.add("Sec-WebSocket-Key", key);
        headers.add("Sec-WebSocket-Version", "13");
        if (subprotocols != null && !subprotocols.trim().isEmpty()) {
            headers.add("Sec-WebSocket-Protocol", subprotocols.trim());
        }

        sendHeaders(this.socket, headers, requestLine);
        this.peerHeaders = receiveHeaders(this.socket);
        if (!(peerHeaders.getFirst(REQUEST_LINE_HEADER).split(" ")[1].equals("101")
                && peerHeaders.getFirst("Sec-WebSocket-Accept").equals(sha1Hash(key))
                && setAgreedSubprotocol(peerHeaders.getFirst("Sec-WebSocket-Protocol")))) {
            throw new ProtocolException("client_handshake");
        }
        String asub = peerHeaders.getFirst("Sec-WebSocket-Protocol");
// close the connection if the negotiated subprotocolList is not in the client's subprotocolList list
// (rfc 6455 4.1 server response 6.)       
        closeCode = 0;
    }

    private static Headers receiveHeaders(Socket socket) throws IOException {
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

    private String sha1Hash(String key) {
        return base64Encode(this.messageDigest.digest(
                (key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes()));
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

    @Override
    public void run() {
        try {
//            this.socket.setSoTimeout(handshakeSoTimeout);
            if (isClientSide) {
                handshakeServer();
            } else {
                handshakeClient();
            }
            this.socket.setSoTimeout(connectionSoTimeout);
            this.closeCode = 0;
            this.handler.onOpen(this);
            this.listenInputStream();
        } catch (Exception e) { // InterruptedException
            this.handler.onError(this, e);
            this.closeSocket();
        }
    }

    static final int OP_FINAL = 0x80;
    static final int OP_CONTINUATION = 0x0;
    static final int OP_TEXT = 0x1;
    static final int OP_BINARY = 0x2;
    static final int OP_TEXT_FINAL = 0x81;
    static final int OP_BINARY_FINAL = 0x82;
    static final int OP_CLOSE = 0x88;
    static final int OP_PING = 0x89;
    static final int OP_PONG = 0x8A;
    static final int MASKED_DATA = 0x80;
    static final byte[] PING_PAYLOAD = "PingPong".getBytes();
    static final byte[] EMPTY_PAYLOAD = new byte[0];

    void listenInputStream() throws IOException {
// BufferedInputStream is not good for large payloads?
//        BufferedInputStream is = new BufferedInputStream(socket.getInputStream());
        InputStream is = socket.getInputStream();
        boolean pingSent = false;
        boolean maskedData;
        int opData = 0;
        byte[] message = EMPTY_PAYLOAD;
        long messageLen = 0;
        byte[] service = new byte[8]; //buffer for frame header elements
        boolean done = false;

        while (!done) {
            try {
                int b1 = is.read();
                int b2 = is.read();
                if ((b1 | b2) == -1) {
                    throw new EOFException("frame_op");
                }
// check op    
                switch (b1) {
                    case OP_TEXT_FINAL:
                    case OP_TEXT:
                    case OP_BINARY_FINAL:
                    case OP_BINARY: {
                        if (opData == 0) {
                            opData = b1 & 0xF;
                            b1 &= OP_FINAL;
                            break;
                        }
                    }
                    case OP_CONTINUATION:
                    case OP_FINAL: {
                        if (opData != 0) {
                            break;
                        }
                    }
                    case OP_PING:
                    case OP_PONG:
                    case OP_CLOSE: {
                        break;
                    }
                    default: {
                        closeDueTo(UNSUPPORTED_DATA, new ProtocolException("unsupported_op"));
                    }
                }
// get frame payload length
                long framePayloadLen = b2 & 0x7F;
                int serviceLen = 0;
                if (framePayloadLen == 126L) {
                    serviceLen = 2;
                } else if (framePayloadLen == 127L) {
                    serviceLen = 8;
                }
                if (serviceLen > 0) {
                    if (this.readFully(is, service, 0, serviceLen) != serviceLen) {
                        throw new EOFException("frame_length");
                    }
                    framePayloadLen = 0;
                    for (int i = 0; i < serviceLen; i++) {
                        framePayloadLen <<= 8;
                        framePayloadLen += (service[i] & 0xFF);
                    }
                }
// get mask 
                maskedData = (b2 & MASKED_DATA) != 0;
                if (maskedData) {
                    serviceLen = 4;
                    if (this.readFully(is, service, 0, serviceLen) != serviceLen) {
                        throw new EOFException("frame_mask");
                    }
                }
// client MUST mask the data, server MUST NOT
                if (Boolean.compare(this.isClientSide, maskedData) == 0) {
                    closeDueTo(PROTOCOL_ERROR, new ProtocolException("mask_mismatch"));
                }
// check frame payload length
                switch (b1) {
                    case OP_CONTINUATION:
                    case OP_FINAL: {
                        messageLen += framePayloadLen;
                        if (closeCode == 0) {
                            if (messageLen <= (long) this.maxMessageLength) {
//                                    || streamingEnabled) {
                                break;
                            }
                            message = EMPTY_PAYLOAD;
                            closeDueTo(MESSAGE_TOO_BIG, new BufferUnderflowException());
                        }
                    }
                    case OP_PING:
                    case OP_PONG:
                    case OP_CLOSE: {
                        if (framePayloadLen <= 125L) {
                            break;
                        }
                        closeDueTo(PROTOCOL_ERROR, new ProtocolException());
                    }
                    default: {
                        is.skip(framePayloadLen);
                        framePayloadLen = 0;
                    }
                }
// read frame payload                
                byte[] framePayload = new byte[(int) framePayloadLen];
                if (this.readFully(is, framePayload, 0, (int) framePayloadLen)
                        != framePayload.length) {
                    throw new EOFException("frame_payload");
                }
// unmask frame payload                
                if (maskedData) {
                    umaskPayload(service, framePayload);
                }
// perform frame op
                switch (b1) {
                    case OP_CONTINUATION:
                        if (this.closeCode == 0) {
                            message = combine(message, framePayload);
                        }
                        break;
                    case OP_FINAL: {
                        if (this.closeCode == 0) {
                            message = combine(message, framePayload);
                            if (opData == OP_BINARY) {
                                handler.onMessage(this, message);
                            } else {
                                handler.onMessage(this,
                                        new String(message, StandardCharsets.UTF_8));
                            }
                        }
                        opData = 0;
                        message = EMPTY_PAYLOAD;
                        messageLen = 0;
                        break;
                    }
                    case OP_PONG: {
                        if (pingSent
                                && Arrays.equals(framePayload, PING_PAYLOAD)) {
                        } else {
                            closeDueTo(PROTOCOL_ERROR,
                                    new ProtocolException("unexpected_pong"));
                        }
                        pingSent = false;
                        break;
                    }
                    case OP_PING: {
                        sendPayload(OP_PONG, framePayload);
                        break;
                    }
                    case OP_CLOSE: { // close handshake
                        done = true;
                        if (closeCode == 0) {
                            sendPayload(OP_CLOSE, framePayload);
                            if (framePayload.length > 1) {
                                closeCode = -(((framePayload[0] & 0xFF) << 8)
                                        + (framePayload[1] & 0xFF));
                                closeReason = new String(
                                        Arrays.copyOfRange(framePayload, 2, framePayload.length - 2),
                                        StandardCharsets.UTF_8
                                );
                            } else {
                                closeCode = -NO_STATUS;
                            }
                        }
                        break;
                    }
                    default: {
                        closeDueTo(UNSUPPORTED_DATA,
                                new ProtocolException("unsupported_op"));
                    }
                }
            } catch (SocketTimeoutException e) {
                if (this.pingEnabled && !pingSent && this.closeCode == 0) {
                    pingSent = true;
                    sendPayload(OP_PING, PING_PAYLOAD);
                } else {
                    done = true;
                    closeDueTo(GOING_AWAY, e);
                }
            } catch (EOFException e) {
                done = true;
                closeDueTo(ABNORMAL_CLOSURE, e);
            } catch (Exception e) {
                done = true;
                closeDueTo(INTERNAL_ERROR, e);
            } catch (Error e) {// large messages can throw java.lang.OutOfMemoryError
                done = true;
                closeDueTo(INTERNAL_ERROR, new Exception(e.toString(), e));
            }
        }
        closeSocket();
        if (closureException != null) {
            handler.onError(this, closureException);
        }
        handler.onClose(this);
    }

    boolean isSocketOpen() {
        return !(this.socket == null || this.socket.isClosed() 
                || this.socket.isInputShutdown());
    }

    void closeSocket() {
        if (this.isSocketOpen()) {
            try {
                if (this.isSecure) {
                    ((SSLSocket) this.socket).close();
                } else {
                    this.socket.shutdownInput();
                    this.socket.close();
                }
            } catch (IOException e) {
//                e.printStackTrace();
            }
        }
    }

    private int readFully(InputStream is, byte[] buf, int off, int len)
            throws IOException {
        int bytesRead = 0;
        for (int n = is.read(buf, off, len); n > 0 && bytesRead < len;) {
            bytesRead += n;
            n = is.read(buf, off + bytesRead, len - bytesRead);
        }
        return bytesRead;
    }

    private Exception closureException = null;

    private void closeDueTo(int status, Exception e) {
        if (closeCode == 0 && closureException == null) {
            closureException = e;
        }
        close(status, "");
    }

// unmask/mask payload
    private void umaskPayload(byte[] mask, byte[] payload) {
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
        if (closeCode != 0) { // || !isSocketOpen()) {
// close handshake in progress            
            throw new SocketException("close_handshake");
        }
        boolean masked = this.isClientSide;
        OutputStream os = this.socket.getOutputStream();
//        BufferedOutputStream os
//                = new BufferedOutputStream(this.socket.getOutputStream());
        byte[] header = new byte[2];
        header[0] = (byte) opData;
        byte[] mask = {0, 0, 0, 0};
        int payloadLen = payload.length;
        if (payloadLen < 126) {
            header[1] = (byte) (payloadLen);
        } else if (payloadLen < 0x10000) {
            header[1] = (byte) 126;
            header = combine(header, header); // increase the header by 2 bytes
            header[2] = (byte) (payloadLen >>> 8);
            header[3] = (byte) payloadLen;
        } else {
            header[1] = (byte) 127;
            header = combine(header, mask);// add 4 zero bytes of 64bit payload length
            for (int i = 3; i > 0; i--) {
                mask[i] = (byte) (payloadLen & 0xFF);
                payloadLen >>>= 8;
            }
            header = combine(header, mask); // 32bit int payload length
        }
        if (masked) {
            header[1] |= MASKED_DATA;
//            (new SecureRandom()).nextBytes(mask);
            (new Random()).nextBytes(mask); // Random enough
            header = combine(header, mask); // add mask
            byte[] maskedPayload = payload.clone();
            umaskPayload(mask, maskedPayload);
            os.write(header, 0, header.length);
            os.write(maskedPayload, 0, payload.length);
        } else {
            os.write(header, 0, header.length);
            os.write(payload, 0, payload.length);
        }
        os.flush();
    }

}
