/*
 * WsConnection. WebSocket connection, MIT (c) 2020-2021 miktim@mail.ru
 *
 * Release notes:
 * - java SE 7+;
 * - RFC-6455: https://tools.ietf.org/html/rfc6455;
 * - WebSocket protocol version: 13;
 * - WebSocket extensions not supported
 *
 * Created: 2020-03-09
 */
package org.miktim.websocket;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ProtocolException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
//import java.util.Base64; // java 8+

public class WsConnection extends Thread {

    public static final String VERSION = "2.4.0";
    private static final String CONNECTION_NAME = "WsLite/" + VERSION; // Server/UserAgent

// request line header name
    private static final String REQUEST_LINE_HEADER = "_RequestLine";

    private final Socket socket;
    private final WsHandler handler;
    private final boolean isClientSide;
    private final boolean isSecure;
    private Headers peerHeaders;
    private URI requestURI;
    private int closeCode = WsStatus.GOING_AWAY;
    private String closeReason = "";
    private boolean closeClean = false;
    private Exception closeException;
    private String subProtocol = null;
    private WsParameters wsp = new WsParameters();

    public boolean isOpen() {
        return closeCode == 0;
    }

    public boolean isSecure() {
        return isSecure;
    }

    void setWsParameters(WsParameters parm) throws CloneNotSupportedException {
        this.wsp = parm.clone();
    }

    public WsParameters getWsParameters() {
        if (isSecure) {
            try {
                wsp.sslParameters.setProtocols(
                        ((SSLSocket) socket).getSession().getProtocol().split(","));
                wsp.sslParameters.setCipherSuites(
                        ((SSLSocket) socket).getSession().getCipherSuite().split(","));
            } catch (Exception e) {
            }
        }
        return this.wsp;
    }

    public String getSubProtocol() { // get handshaked subprotocol
        return subProtocol;
    }

    public String getPeerHost() {
        try {
            if (isClientSide) {
                return requestURI.getHost();
            }
            if (isSecure) {
                return ((SSLSocket) socket).getSession().getPeerHost();
            } else {
                return ((InetSocketAddress) socket.getRemoteSocketAddress()).getHostString();
            }
        } catch (Exception e) {
            return null;
        }
    }

    public int getPort() {
        return socket.getPort();
    }

    public String getPath() {
        if (this.requestURI != null) {
            return requestURI.getPath();
        }
        return null;
    }

    public String getQuery() {
        if (this.requestURI != null) {
            return requestURI.getQuery();
        }
        return null;
    }

    public String getSSLSessionProtocol() {
        if (this.isSecure() && this.isSocketOpen()) {
            return ((SSLSocket) this.socket).getSession().getProtocol();
        }
        return null;
    }

    public void send(byte[] message) throws IOException {
//        syncSend(message, false);
        syncSend(new ByteArrayInputStream(message), false);
    }

    public void send(String message) throws IOException {
//        syncSend(message.getBytes(StandardCharsets.UTF_8), true);
        syncSend(new ByteArrayInputStream(
                message.getBytes(StandardCharsets.UTF_8)), true);
    }

    public void send(InputStream is, boolean isText) throws IOException {
        syncSend(is, isText);
    }

    private synchronized void syncSend(InputStream is, boolean isText)
            throws IOException {
        int op = isText ? OP_TEXT : OP_BINARY;
        try {
// BufferedInputStream is not good for large payloads?
            byte[] buf = new byte[wsp.payloadBufferLength];
            int len = 0;
            while ((len = this.readFully(is, buf, 0, buf.length)) == buf.length) {
                sendFrame(op, buf, buf.length);
                op = OP_CONTINUATION;
            }
// be sure to send the final frame even if eof is detected!            
            sendFrame(op | OP_FINAL, buf, len >= 0 ? len : 0);
        } catch (IOException e) {
            this.close(WsStatus.INTERNAL_ERROR, "");
            throw e;
        }
    }

    public WsStatus getStatus() {
        return new WsStatus(Math.abs(closeCode), closeReason, closeClean, closeCode < 0);
    }

    public void close() {
        close(WsStatus.NO_STATUS, "");
    }

    public synchronized void close(int code, String reason) {
        if (this.closeCode == 0) {
            code = (code < 1000 || code > 4999) ? WsStatus.NO_STATUS : code;
            byte[] payload = new byte[125];
            byte[] byteReason
                    = (reason == null ? "" : reason).getBytes(StandardCharsets.UTF_8);
            int payloadLen = 0;
            if (code != WsStatus.NO_STATUS) {
                payload[0] = (byte) (code >>> 8);
                payload[1] = (byte) (code & 0xFF);
                byteReason = Arrays.copyOf(byteReason, Math.min(byteReason.length, 123));
                System.arraycopy(byteReason, 0, payload, 2, byteReason.length);
                payloadLen = 2 + byteReason.length;
            }
            try {
                this.socket.setSoTimeout(wsp.handshakeSoTimeout);
                sendFrame(OP_CLOSE, payload, payloadLen);
                this.closeCode = code; // disable sending data
                this.closeReason = new String(byteReason, StandardCharsets.UTF_8);
            } catch (IOException e) {
                this.closeCode = WsStatus.ABNORMAL_CLOSURE;
                this.closeReason = "WebSocket close error";
                handler.onError(this, e);
            }
        }
    }

// WebSocket Listener connection
    WsConnection(Socket s, WsHandler h, boolean secure) {
        this.isClientSide = false;
        this.socket = s;
        this.handler = h;
        this.isSecure = secure;
    }

    private void handshakeClient()
            throws IOException, URISyntaxException, NoSuchAlgorithmException {
        closeCode = WsStatus.PROTOCOL_ERROR;
        this.peerHeaders = readHeaders(this.socket);
        String[] parts = peerHeaders.get(REQUEST_LINE_HEADER).split(" ");
        this.requestURI = new URI(parts[1]);
        String upgrade = peerHeaders.get("Upgrade");
        String key = peerHeaders.get("Sec-WebSocket-Key");
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
            responseHeaders.set("Server", CONNECTION_NAME);
            setSubprotocol(this.peerHeaders.get("Sec-WebSocket-Protocol"), responseHeaders);
            writeHeaders(this.socket, responseHeaders, "HTTP/1.1 101 Upgrade");
        } else {
            writeHeaders(this.socket, null, "HTTP/1.1 400 Bad Request");
            throw new ProtocolException("WebSocket handshake failed");
        }
        closeCode = WsStatus.IS_OPEN;
    }

    private void setSubprotocol(String requestedSubps, Headers rs) {
        if (requestedSubps == null || wsp.subProtocols == null) {
            return;
        }
        String[] rqSubps = requestedSubps.replaceAll(" ", "").split(",");
        for (String agreedSubp : rqSubps) {
            for (String subp : wsp.subProtocols) {
                if (agreedSubp.equals(subp)) {
                    this.subProtocol = agreedSubp;
                    rs.set("Sec-WebSocket-Protocol", agreedSubp); // response headers
                    return;
                }
            }
        }
    }

//  WebSocket client connection
    WsConnection(String uri, WsHandler handler, InetAddress bindAddr) throws // ??? bind
            URISyntaxException, NullPointerException, IOException {
        this.isClientSide = true;
        if (uri == null || handler == null) {
            throw new NullPointerException();
        }
        this.handler = handler;
        this.requestURI = new URI(uri);
        String scheme = requestURI.getScheme();
        String host = requestURI.getHost();
        if (host == null || scheme == null) {
            throw new URISyntaxException(uri, "Scheme and host required");
        }
        if (!(scheme.equals("ws") || scheme.equals("wss"))) {
            throw new URISyntaxException(uri, "Unsupported scheme");
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
        socket.bind(new InetSocketAddress(bindAddr, 0));
    }

    /* v1.x.x backward compatibility
    void open() throws IOException {
        if (!isClientSide || this.socket.isConnected()) {
            throw new SocketException();
        }
        connectSocket();
        this.start();
    }
     */
    private void connectSocket() throws IOException {
        try {
            int port = requestURI.getPort();
            if (port < 0) {
                port = isSecure ? 443 : 80;
            }
            if (isSecure && wsp.sslParameters != null) {
                ((SSLSocket) this.socket).setSSLParameters(wsp.sslParameters);
            }
            this.socket.connect(
                    new InetSocketAddress(requestURI.getHost(), port), wsp.handshakeSoTimeout);
        } catch (IOException e) {
            closeSocket();
            throw e;
        }
    }

    private void handshakeServer()
            throws IOException, URISyntaxException, NoSuchAlgorithmException {
        this.closeCode = WsStatus.PROTOCOL_ERROR;

        String key = base64Encode(nextBytes(16));

        String requestPath = (requestURI.getPath() == null ? "/" : requestURI.getPath())
                + (requestURI.getQuery() == null ? "" : "?" + requestURI.getQuery());
        requestPath = (new URI(requestPath)).toASCIIString();
        if (!requestPath.startsWith("/")) {
            requestPath = "/" + requestPath;
        }
        String host = requestURI.getHost()
                + (requestURI.getPort() > 0 ? ":" + requestURI.getPort() : "");
//        host = (new URI(host)).toASCIIString(); // URISyntaxException on IP addr
        String requestLine = "GET " + requestPath + " HTTP/1.1";
        Headers headers = new Headers();
        headers.add("Host", host);
        headers.add("Origin", requestURI.getScheme() + "://" + host);
        headers.add("Upgrade", "websocket");
        headers.add("Connection", "Upgrade,keep-alive");
        headers.add("Sec-WebSocket-Key", key);
        headers.add("Sec-WebSocket-Version", "13");
        headers.set("User-Agent", CONNECTION_NAME);
        if (wsp.subProtocols != null) {
            headers.add("Sec-WebSocket-Protocol", wsp.join(wsp.subProtocols));
        }
        writeHeaders(this.socket, headers, requestLine);

        this.peerHeaders = readHeaders(this.socket);
        this.subProtocol = peerHeaders.get("Sec-WebSocket-Protocol");
        if (!(peerHeaders.get(REQUEST_LINE_HEADER).split(" ")[1].equals("101")
                && peerHeaders.get("Sec-WebSocket-Accept").equals(sha1Hash(key))
                && checkSubprotocol())) {
            throw new ProtocolException("WebSocket handshake failed");
        }
        this.closeCode = WsStatus.IS_OPEN;
    }

    private boolean checkSubprotocol() {
// rfc 6455 4.1 server response 6.:       
// 'close the connection if the negotiated subProtocol is not in the client's 
// subProtocol list'.  What about returned null??? FireFox accepts null.
        if (this.subProtocol == null) {
            return true; // 
        } else if (wsp.subProtocols == null) {
            return false;
        }
        for (String sub : wsp.subProtocols) {
            if (String.valueOf(sub).equals(subProtocol)) { // sub can be null
                return true;
            }
        }
        return false;
    }

    private static Headers readHeaders(Socket socket) throws IOException {
        Headers headers = new Headers();
        BufferedReader br = new BufferedReader(
                new InputStreamReader(socket.getInputStream()));
        String line = br.readLine();
        if (line.startsWith("\u0016\u0003\u0003")) {
            throw new javax.net.ssl.SSLHandshakeException("Plain socket");
        }
        String[] parts = line.split(" ");
        if (!(parts.length > 2
                && (parts[0].equals("HTTP/1.1") || parts[2].equals("HTTP/1.1")))) {
            throw new ProtocolException("SSL required or invalid HTTP request");
        }
        headers.set(REQUEST_LINE_HEADER, line);
        String key = null;
        while (true) {
            line = br.readLine();
            if (line == null || line.isEmpty()) {
                break;
            }
            if (line.startsWith(" ") || line.startsWith("\t")) { // continued
                headers.set(key, headers.get(key) + line.trim());
                continue;
            }
            key = line.substring(0, line.indexOf(":"));
            headers.add(key, line.substring(key.length() + 1).trim());
        }
        return headers;
    }

    private void writeHeaders(Socket socket, Headers hs, String statusLine)
            throws IOException {
        StringBuilder sb = new StringBuilder(statusLine);
        sb.append("\r\n");
        if (hs != null) {
            for (String hn : hs.keySet()) {
                sb.append(hn).append(": ").append(hs.get(hn)).append("\r\n");
            }
        } else {
            sb.append("Connection: close").append("\r\n");
        }
        sb.append("\r\n");
        socket.getOutputStream().write(sb.toString().getBytes());
        socket.getOutputStream().flush();
    }

// generate "random" mask/key
    private byte[] nextBytes(int len) {
        byte[] b = new byte[len];
        long l = Double.doubleToRawLongBits(Math.random());
        while (--len >= 0) {
            b[len] = (byte) l;
            l >>= 1;
        }
        return b;
    }

    private String sha1Hash(String key) throws NoSuchAlgorithmException {
        return base64Encode(MessageDigest.getInstance("SHA-1").digest(
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
    }

    @Override
    public void run() {
        try {
            if (isClientSide) {
                connectSocket();
                handshakeServer();
            } else {
                handshakeClient();
            }
            this.socket.setSoTimeout(wsp.connectionSoTimeout);
            this.startMessaging();
        } catch (Exception e) {
            this.handler.onError(this, e);
//            e.printStackTrace();
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
    static final int OP_EXTENSIONS = 0x70;
    static final int MASKED_DATA = 0x80;
    static final byte[] PING_PAYLOAD = "PingPong".getBytes();
    static final byte[] EMPTY_PAYLOAD = new byte[0];

    private int opData;
    private final byte[] payloadMask = new byte[8]; //buffer for frame length and mask
    private long payloadLength = 0;
    private boolean pingFrameSent = false;
    private InputStream is;

    private void startMessaging() throws IOException {
// BufferedInputStream is not good for large payloads?
//        BufferedInputStream is = new BufferedInputStream(socket.getInputStream());
        this.closeCode = WsStatus.INTERNAL_ERROR; // getInputStream() exception
        is = socket.getInputStream();
        this.closeCode = 0;
        this.handler.onOpen(this, getSubProtocol());
        while (waitDataFrame()) {
            InputStream wis = new WsInputStream(this);
            handler.onMessage(this, wis, (opData & 0x7F) == OP_TEXT);
            wis.close();
        }
        closeSocket();
        if (closeException != null) {
            handler.onError(this, closeException);
        }
        handler.onClose(this, getStatus());
    }

    private boolean waitDataFrame() {
        boolean maskedPayload;
        while (!closeClean) {
            try {
                int b1 = is.read();
                int b2 = is.read();
                if ((b1 | b2) == -1) {
                    throw new EOFException("Unexpected EOF (header)");
                }
                if ((b1 & OP_EXTENSIONS) != 0) {
                    closeDueTo(WsStatus.UNSUPPORTED_EXTENSION,
                            new ProtocolException("Unsupported extension"));
                }
                if ((opData & OP_FINAL) != 0) {
                    opData = 0; // reset prev opData
                }
// check op    
                switch (b1) {
                    case OP_TEXT_FINAL:
                    case OP_TEXT:
                    case OP_BINARY_FINAL:
                    case OP_BINARY: {
                        if (opData == 0) {
                            opData = b1;
                            break;
                        }
                    }
                    case OP_CONTINUATION:
                        if (opData != 0) {
                            break;
                        }
                    case OP_FINAL: {
                        if (opData != 0) {
                            opData |= OP_FINAL;
                            break;
                        }
                    }
                    case OP_PING:
                    case OP_PONG:
                    case OP_CLOSE: {
                        break;
                    }
                    default: {
                        closeDueTo(WsStatus.PROTOCOL_ERROR,
                                new ProtocolException("Unexpected opcode"));
                    }
                }
// get frame payload length
                payloadLength = b2 & 0x7F;
                int serviceLen = 0;
                if (payloadLength == 126L) {
                    serviceLen = 2;
                } else if (payloadLength == 127L) {
                    serviceLen = 8;
                }
                if (serviceLen > 0) {
                    if (this.readFully(is, payloadMask, 0, serviceLen) != serviceLen) {
                        throw new EOFException("Unexpected EOF (frame length)");
                    }
                    payloadLength = 0;
                    for (int i = 0; i < serviceLen; i++) {
                        payloadLength <<= 8;
                        payloadLength += (payloadMask[i] & 0xFF);
                    }
                }
// get mask 
                maskedPayload = (b2 & MASKED_DATA) != 0;
                if (maskedPayload) {
                    serviceLen = 4;
                    if (this.readFully(is, payloadMask, 0, serviceLen) != serviceLen) {
                        throw new EOFException("Unexpected EOF (mask)");
                    }
                }
// client MUST mask the data, server MUST NOT
                if (Boolean.compare(this.isClientSide, maskedPayload) == 0) {
                    closeDueTo(WsStatus.PROTOCOL_ERROR,
                            new ProtocolException("Mask mismatch"));
                }
// check frame payload length
                switch (b1) {
                    case OP_TEXT:
                    case OP_BINARY:
                    case OP_TEXT_FINAL:
                    case OP_BINARY_FINAL:
                    case OP_CONTINUATION:
                    case OP_FINAL: {
                        if (payloadLength >= 0L) {
                            return true;
                        } else {
                            closeDueTo(WsStatus.PROTOCOL_ERROR,
                                    new ProtocolException("Payload length exceeded"));
                        }
                    }
                    case OP_PING:
                    case OP_PONG:
                    case OP_CLOSE: {
                        if (payloadLength <= 125L) {
                            break;
                        }
                        closeDueTo(WsStatus.PROTOCOL_ERROR,
                                new ProtocolException("Control frame payload length exceeded"));
                    }
                    default: {
                        is.skip(payloadLength);
                        payloadLength = 0;
                        if (b1 != OP_CLOSE) {
                            continue;
                        }
                    }
                }
// read control frame payload                
                byte[] framePayload = new byte[(int) payloadLength];
                if (this.readFully(is, framePayload, 0, (int) payloadLength)
                        != framePayload.length) {
                    throw new EOFException("Unexpected EOF (payload)");
                }
// unmask frame payload                
                if (maskedPayload) {
                    umaskPayload(payloadMask, framePayload, framePayload.length);
                }
// perform control frame op
                switch (b1) {
                    case OP_PONG: {
                        if (pingFrameSent
                                && Arrays.equals(framePayload, PING_PAYLOAD)) {
                        } else {
                            closeDueTo(WsStatus.PROTOCOL_ERROR,
                                    new ProtocolException("Unexpected pong"));
                        }
                        pingFrameSent = false;
                        break;
                    }
                    case OP_PING: {
                        if (closeCode == 0) {
                            sendFrame(OP_PONG, framePayload, framePayload.length);
                        }
                        break;
                    }
                    case OP_CLOSE: { // close handshake
                        if (closeCode == 0) {
                            sendFrame(OP_CLOSE, framePayload, framePayload.length);
                            if (framePayload.length > 1) {
                                closeCode = -(((framePayload[0] & 0xFF) << 8)
                                        + (framePayload[1] & 0xFF));
                                closeReason = new String(framePayload,
                                        2, framePayload.length - 2,
                                        StandardCharsets.UTF_8
                                );
                            }
                            if (closeCode == 0) {
                                closeCode = -WsStatus.NO_STATUS;
                            }
                        }
                        this.closeClean = true;
                        return false;
                    }
                    default: {
                        closeDueTo(WsStatus.PROTOCOL_ERROR,
                                new ProtocolException("Unsupported opcode"));
                    }
                }
            } catch (SocketTimeoutException e) {
                if (this.closeCode == 0 && this.wsp.pingEnabled && !pingFrameSent) {
                    pingFrameSent = true;
                    try {
                        sendFrame(OP_PING, PING_PAYLOAD, PING_PAYLOAD.length);
                    } catch (IOException ep) {
                        break;
                    }
                } else {
                    closeDueTo(WsStatus.ABNORMAL_CLOSURE, e);
                    break;
                }
            } catch (EOFException e) {
                closeDueTo(WsStatus.ABNORMAL_CLOSURE, e);
                break;
            } catch (Exception e) {
//e.printStackTrace();
                closeDueTo(WsStatus.INTERNAL_ERROR, e);
                break;
            } catch (Error e) {// large messages can throw java.lang.OutOfMemoryError
                closeDueTo(WsStatus.INTERNAL_ERROR, new Exception(e.toString(), e));
                break;
            }
        }
        return false;
    }

    private static final int IS_READY = 0;
    private static final int IS_EOF = 1;
    private static final int IS_CLOSED = 2;
    private static final int IS_ERROR = 3;

    private class WsInputStream extends InputStream {

        private final WsConnection conn;
        private int state = IS_READY;
        private int len = 0;
        private int pos = 0;
        private final byte[] buf;

        WsInputStream(WsConnection conn) {
            super();
            this.conn = conn;
// align buffer to mask length, prevent zero length buffer         
            this.buf = new byte[(Math.max(WsParameters.MIN_PAYLOAD_BUFFER_LENGTH,
                    (int) Math.min(payloadLength,
                            (long) conn.wsp.payloadBufferLength))
                    + 3 & 0xFFFFFFFC)];
        }

        private void waitMessageFrame(boolean closing) {
            try {
                while (payloadLength > 0
                        || ((opData & OP_FINAL) == 0 && waitDataFrame())) {
                    if (closing) {
                        is.skip(payloadLength);
                        payloadLength = 0;
                        continue;
                    }
                    this.len = readFully(is, this.buf, 0,
                            (int) Math.min(payloadLength, (long) this.buf.length));
                    payloadLength -= this.len;
                    this.pos = 0;
                    if (!isClientSide) { //maskedPayload) {
                        umaskPayload(payloadMask, this.buf, this.len);
                    }
                    return;
                }
//            } catch (SocketTimeoutException e) {
            } catch (IOException e) {
                closeDueTo(WsStatus.ABNORMAL_CLOSURE, e);
            }
            if ((opData & OP_FINAL) != 0) {
                this.state = IS_EOF;
            } else {
                this.state = IS_ERROR;
            }
        }

        @Override
        public int read() throws IOException {
            while (this.state == IS_READY) {
                while (this.pos < this.len) {
                    return this.buf[this.pos++] & 0xFF;
                }
                waitMessageFrame(false);
            }
            switch (this.state) {
                case IS_CLOSED:
                    throw new IOException("Stream closed");
                case IS_ERROR:
                    throw new SocketException("WebSocket closed");
            }
            return -1; // eof
        }

        @Override
        public boolean markSupported() {
            return false;
        }

        @Override
        public void close() throws IOException {
            if (this.state == IS_READY) {
                waitMessageFrame(true);
            }
            if (this.state == IS_EOF) {
                this.state = IS_CLOSED;
            }
        }
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
//                    this.socket.shutdownInput();
                    this.socket.close();
                }
            } catch (IOException e) {
//                e.printStackTrace();
            }
        }
    }

    public int readFully(InputStream is, byte[] buf, int off, int len)
            throws IOException {
        int bytesCnt = 0;
        for (int n = is.read(buf, off, len); n >= 0 && bytesCnt < len;) {
            bytesCnt += n;
            n = is.read(buf, off + bytesCnt, len - bytesCnt);
        }
        return bytesCnt;
    }

    private void closeDueTo(int status, Exception e) {
        if (closeCode == 0 && closeException == null) {
            closeException = e;
        }
        close(status, "");
    }

// unmask/mask payload
    private void umaskPayload(byte[] mask, byte[] payload, int len) {
        for (int i = 0; i < len; i++) {
            payload[i] ^= mask[i % 4];
        }
    }

    private synchronized void sendFrame(int opData, byte[] payload, int len)
            throws IOException {
        if (closeCode != 0) { // || !isSocketOpen()) {
// socket closed or close handshake in progress            
            throw new SocketException("WebSocket closed");
        }
        boolean masked = this.isClientSide;
        OutputStream os = this.socket.getOutputStream();
//        BufferedOutputStream os
//                = new BufferedOutputStream(this.socket.getOutputStream());
        byte[] header = new byte[14]; //hopefully initialized with zeros

        header[0] = (byte) opData;
        int headerLen = 2;

        int payloadLen = len;
        if (payloadLen < 126) {
            header[1] = (byte) (payloadLen);
        } else if (payloadLen < 0x10000) {
            header[1] = (byte) 126;
            header[2] = (byte) (payloadLen >>> 8);
            header[3] = (byte) payloadLen;
            headerLen += 2;
        } else {
            header[1] = (byte) 127;
            headerLen += 4; // skip 4 zero bytes of 64bit payload length
            for (int i = 3; i > 0; i--) {
                header[headerLen + i] = (byte) (payloadLen & 0xFF);
                payloadLen >>>= 8;
            }
            headerLen += 4;
        }
        try {
            if (masked) {
                header[1] |= MASKED_DATA;
                byte[] mask = nextBytes(4);
                System.arraycopy(mask, 0, header, headerLen, 4);
                headerLen += 4;
                byte[] maskedPayload = payload.clone();
                umaskPayload(mask, maskedPayload, len);
                os.write(header, 0, headerLen);
                os.write(maskedPayload, 0, len);
            } else {
                os.write(header, 0, headerLen);
                os.write(payload, 0, len);
            }
            os.flush();
        } catch (IOException e) {
            this.closeCode = WsStatus.ABNORMAL_CLOSURE;
            throw e;
        }
    }
}
