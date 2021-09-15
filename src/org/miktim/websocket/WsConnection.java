/*
 * WsConnection. WebSocket connection, MIT (c) 2020-2021 miktim@mail.ru
 *
 * SSL/WebSocket handshaking. Messaging. Events handling.
 *
 * Created: 2020-03-09
 */
package org.miktim.websocket;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
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
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;

public class WsConnection extends Thread {

    public static final String VERSION = "2.4.1";
    private static final String SERVER_AGENT = "WsLite/" + VERSION;

    private final Socket socket;
    private WsHandler handler;
    private final boolean isSecure; // SSL connection
    private final boolean isClientSide;
    private URI requestURI;
    private String subProtocol = null; // handshaked WebSocket subprotocol
    private final WsParameters wsp;
    private final WsStatus status = new WsStatus();

// send binary data    
    public void send(InputStream is) throws IOException {
        syncSend(is, false);
    }

// send UTF-8 text
    public void send(Reader rd) throws IOException {
        syncSend(new ReaderInputStream(rd), true);
    }

// send binary data
    public void send(byte[] message) throws IOException {
        syncSend(new ByteArrayInputStream(message), false);
    }

// send UTF-8 text
    public void send(String message) throws IOException {
        syncSend(new ReaderInputStream(new StringReader(message)), true);
    }

// Returns handshaked WebSocket subprotocol
    public String getSubProtocol() {
        return subProtocol;
    }

    public WsStatus getStatus() {
        return status.clone();
    }

    public boolean isOpen() {
        return status.code == WsStatus.IS_OPEN;
    }

    public boolean isSecure() {
        return isSecure;
    }

    public boolean isClientSide() {
        return isClientSide;
    }

    public synchronized void setHandler(WsHandler h) {
        handler = h;
        if (isOpen()) {
            h.onOpen(this, subProtocol);
        }
    }

    public WsParameters getParameters() {
        SSLParameters sslp = null;
        if (isSecure) {
            try {
                sslp = ((SSLSocket) socket).getSSLParameters();
                sslp.setProtocols(new String[]{
                    ((SSLSocket) socket).getSession().getProtocol()});
                sslp.setCipherSuites(new String[]{
                    ((SSLSocket) socket).getSession().getCipherSuite()});
            } catch (Exception e) {
            }
        }
        wsp.sslParameters = sslp;
        return wsp;
    }

// Returns remote host name
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

// Returns the bound/connected port
    public int getPort() {
        return socket.getPort();
    }

// Returns http request path
    public String getPath() {
        if (this.requestURI != null) {
            return requestURI.getPath();
        }
        return null;
    }

// Returns http request query
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

    public class ReaderInputStream extends InputStream {

        private final Reader rd;
        private int c = 0;

        public ReaderInputStream(Reader reader) {
            super();
            this.rd = reader;
        }

        @Override
        public int read() throws IOException {
            if (c < 0x100) {
                while ((c = rd.read()) != -1) {
                    return c > 0xFF ? (c >> 8) : c;
                }
                c = 0; // prevent eof from being recognized as char
                return -1; // eof
            }
            c &= 0xFF;
            return c;
        }

        @Override
        public boolean markSupported() {
            return false;
        }
    }

    private synchronized void syncSend(InputStream is, boolean isText)
            throws IOException {
        int op = isText ? OP_TEXT : OP_BINARY;
        try {
            byte[] buf = new byte[wsp.payloadBufferLength];
            int len = 0;
            while ((len = this.readFully(is, buf, 0, buf.length)) == buf.length) {
                sendFrame(op, buf, buf.length);
                op = OP_CONTINUATION;
            }
// be sure to send the final frame even if eof is detected (payload length = 0)!            
            sendFrame(op | OP_FINAL, buf, len >= 0 ? len : 0);
        } catch (IOException e) {
            closeDueTo(WsStatus.INTERNAL_ERROR, e);
            throw e;
        }
    }

// Close notes:
// - the closing code outside 1000-4999 is replaced by 1005 (NO_STATUS)
//   and the reason is ignored; 
// - a reason that is longer than 123 bytes is truncated;
// - closing the connection blocks outgoing messages (send methods throw IOException);
// - isOpen() returns false;
// - incoming messages are available until the closing handshake completed.
    public void close() {
        close(WsStatus.NO_STATUS, "");
    }

    public synchronized void close(int code, String reason) {
        if (this.status.code == 0) {
            code = (code < 1000 || code > 4999) ? WsStatus.NO_STATUS : code;
            byte[] payload = new byte[125];
            byte[] byteReason = (reason == null ? "" : reason)
                    .getBytes(StandardCharsets.UTF_8);
            int payloadLen = 0;
            if (code != WsStatus.NO_STATUS) {
                payload[0] = (byte) (code >>> 8);
                payload[1] = (byte) (code & 0xFF);
                byteReason = Arrays.copyOf(
                        byteReason, Math.min(byteReason.length, 123));
                System.arraycopy(byteReason, 0, payload, 2, byteReason.length);
                payloadLen = 2 + byteReason.length;
            }
            try {
                this.socket.setSoTimeout(wsp.handshakeSoTimeout);
                sendFrame(OP_CLOSE, payload, payloadLen);
                status.code = code; // disable sending data
                status.reason = new String(byteReason, StandardCharsets.UTF_8);
            } catch (IOException e) {
                status.reason = "WebSocket close error";
            }
        }
    }

// WebSocket listener connection constructor
    WsConnection(Socket s, WsHandler h, boolean secure, WsParameters wsp)
            throws CloneNotSupportedException {
        this.isClientSide = false;
        this.socket = s;
        this.handler = h;
        this.isSecure = secure;
        this.wsp = wsp.clone();
    }

// WebSocket client connection constructor
    WsConnection(Socket s, WsHandler h, URI uri, WsParameters wsp)
            throws CloneNotSupportedException {
        this.isClientSide = true;
        this.socket = s;
        this.handler = h;
        this.requestURI = uri;
        this.isSecure = uri.getScheme().equals("wss");
        this.wsp = wsp.clone();
    }

    private InputStream inStream;
    private OutputStream outStream;

    @Override
    public void run() {
        try {
            inStream = socket.getInputStream();
            outStream = socket.getOutputStream();
            status.code = WsStatus.PROTOCOL_ERROR;
            if (isClientSide) {
                handshakeServer();
            } else {
                handshakeClient();
            }
            socket.setSoTimeout(wsp.connectionSoTimeout);
            startMessaging();
        } catch (Throwable e) {
            closeSocket();
            status.error = e;
            handler.onError(this, e);
//            e.printStackTrace();
            if (isOpen()) {
                status.code = WsStatus.INTERNAL_ERROR;
                handler.onClose(this, getStatus());
            }
        }
    }

    private void handshakeClient()
            throws IOException, URISyntaxException, NoSuchAlgorithmException {
        HttpHead requestHead = (new HttpHead()).read(inStream);
        String[] parts = requestHead.get(HttpHead.START_LINE).split(" ");
        this.requestURI = new URI(parts[1]);
        String upgrade = requestHead.get("Upgrade");
        String key = requestHead.get("Sec-WebSocket-Key");

        HttpHead responseHead = (new HttpHead()).set("Server", SERVER_AGENT);
        if (parts[0].equals("GET")
                && upgrade != null && upgrade.equals("websocket")
                && key != null) {
            setSubprotocol(requestHead.get("Sec-WebSocket-Protocol"), responseHead);
            responseHead
                    .set(HttpHead.START_LINE, "HTTP/1.1 101 Upgrade")
                    .set("Upgrade", "websocket")
                    .set("Connection", "Upgrade,keep-alive")
                    .set("Sec-WebSocket-Accept", sha1Hash(key))
                    .set("Sec-WebSocket-Version", "13")
                    .write(outStream);
        } else {
            responseHead
                    .set(HttpHead.START_LINE, "HTTP/1.1 400 Bad Request")
                    .set("Connection", "close")
                    .write(outStream);
            throw new ProtocolException("WebSocket handshake failed");
        }
    }

    private void setSubprotocol(String requestedSubps, HttpHead rs) {
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

    private void handshakeServer()
            throws IOException, URISyntaxException, NoSuchAlgorithmException {

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
        HttpHead requestHead = (new HttpHead())
                .set(HttpHead.START_LINE, "GET " + requestPath + " HTTP/1.1")
                .set("Host", host)
                .set("Origin", requestURI.getScheme() + "://" + host)
                .set("Upgrade", "websocket")
                .set("Connection", "Upgrade,keep-alive")
                .set("Sec-WebSocket-Key", key)
                .set("Sec-WebSocket-Version", "13")
                .set("User-Agent", SERVER_AGENT);
        if (wsp.subProtocols != null) {
            requestHead.set("Sec-WebSocket-Protocol", wsp.join(wsp.subProtocols));
        }
        requestHead.write(outStream);

        HttpHead responseHead = (new HttpHead()).read(inStream);
        this.subProtocol = responseHead.get("Sec-WebSocket-Protocol");
        if (!(responseHead.get(HttpHead.START_LINE).split(" ")[1].equals("101")
                && responseHead.get("Sec-WebSocket-Accept").equals(sha1Hash(key))
                && checkSubprotocol())) {
            throw new ProtocolException("WebSocket handshake failed");
        }
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

    private static final byte[] B64_BYTES = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".getBytes();

    public static String base64Encode(byte[] b) {
        byte[] s = new byte[((b.length + 2) / 3 * 4)];
        int bi = 0;
        int si = 0;
        while (bi < b.length) {
            int k = Math.min(3, b.length - bi);
            int bits = 0;
            for (int j = 0, shift = 16; j < k; j++, shift -= 8) {
                bits += ((b[bi++] & 0xFF) << shift);
            }
            for (int j = 0, shift = 18; j <= k; j++, shift -= 6) {
                s[si++] = B64_BYTES[(bits >> shift) & 0x3F];
            }
        }
        while (si < s.length) {
            s[si++] = (byte) '=';
        }
        return new String(s);
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

// variables are filled with waitDataFrame() function
    private int opData; // data frame opcode
    private final byte[] payloadMask = new byte[8]; // mask & temp buffer for payloadLength
    private long payloadLength = 0;
    private boolean pingFrameSent = false;

    private void startMessaging() throws IOException {
        this.status.code = WsStatus.IS_OPEN;
        this.handler.onOpen(this, subProtocol);
        while (waitDataFrame()) {
            InputStream wis = new WsInputStream();
            handler.onMessage(this, wis, (opData & 0x7F) == OP_TEXT);
            wis.close();
        }
        closeSocket();
        if (status.error != null) {
            handler.onError(this, status.error);
        }
        handler.onClose(this, getStatus());
    }

// Sets WsConnection properties: opData, payloadMask, payloadLegth
// Returns true when a data frame arrives
    private boolean waitDataFrame() {
        boolean maskedPayload;
        while (!status.clean) { // !closing hanshake completed
            try {
                int b1 = inStream.read();
                int b2 = inStream.read();
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
// check frame op    
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
                int toRead = 0;
                if (payloadLength == 126L) {
                    toRead = 2;
                } else if (payloadLength == 127L) {
                    toRead = 8;
                }
                if (toRead > 0) {
                    if (this.readFully(inStream, payloadMask, 0, toRead) != toRead) {
                        throw new EOFException("Unexpected EOF (payload length)");
                    }
                    payloadLength = 0;
                    for (int i = 0; i < toRead; i++) {
                        payloadLength <<= 8;
                        payloadLength += (payloadMask[i] & 0xFF);
                    }
                }
// get payload mask 
                maskedPayload = (b2 & MASKED_DATA) != 0;
                if (maskedPayload) {
                    toRead = 4;
                    if (this.readFully(inStream, payloadMask, 0, toRead) != toRead) {
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
                        }
                    }
                    case OP_PING:
                    case OP_PONG:
                    case OP_CLOSE: {
                        if (payloadLength <= 125L) {
                            break;
                        }
                    }
                    default: {
                        closeDueTo(WsStatus.PROTOCOL_ERROR,
                                new ProtocolException("Payload length exceeded"));
                        inStream.skip(payloadLength);
                        payloadLength = 0;
                        if (b1 != OP_CLOSE) {
                            continue;
                        }
                    }
                }
// read control frame payload                
                byte[] framePayload = new byte[(int) payloadLength];
                if (this.readFully(inStream, framePayload, 0, (int) payloadLength)
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
                        if (status.code == 0) {
                            sendFrame(OP_PONG, framePayload, framePayload.length);
                        }
                        break;
                    }
                    case OP_CLOSE: { // close handshake
                        if (status.code == 0) {
                            status.remote = true;
                            sendFrame(OP_CLOSE, framePayload, framePayload.length);
                            if (framePayload.length > 1) {
                                status.code = ((framePayload[0] & 0xFF) << 8)
                                        + (framePayload[1] & 0xFF);
                                status.reason = new String(framePayload,
                                        2, framePayload.length - 2,
                                        StandardCharsets.UTF_8
                                );
                            }
                            if (status.code == 0) {
                                status.code = WsStatus.NO_STATUS;
                            }
                        }
                        status.clean = true;
                        return false;
                    }
                    default: {
                        closeDueTo(WsStatus.PROTOCOL_ERROR,
                                new ProtocolException("Unsupported opcode"));
                    }
                }
            } catch (SocketTimeoutException e) {
                if (this.status.code == 0 && this.wsp.pingEnabled && !pingFrameSent) {
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
            }
        }
        return false;
    }

    private static final int IS_READY = 0;
    private static final int IS_EOF = 1;
    private static final int IS_CLOSED = 2;
    private static final int IS_ERROR = 3;

    private class WsInputStream extends InputStream {

        private int state = IS_READY;
        private int len = 0;
        private int pos = 0;
        private final byte[] buf;

        WsInputStream() {
            super();
// alloc buffer aligned to mask length, prevent zero length buffer         
            this.buf = new byte[(Math.max(WsParameters.MIN_PAYLOAD_BUFFER_LENGTH,
                    (int) Math.min(payloadLength,
                            (long) wsp.payloadBufferLength))
                    + 3 & 0xFFFFFFFC)];
        }

        private void waitMessageFrame(boolean closing) {
            try {
                while (payloadLength > 0
                        || ((opData & OP_FINAL) == 0 && waitDataFrame())) {
                    if (closing) {
                        inStream.skip(payloadLength);
                        payloadLength = 0;
                        continue;
                    }
                    this.len = readFully(inStream, this.buf, 0,
                            (int) Math.min(payloadLength, (long) this.buf.length));
                    payloadLength -= this.len;
                    this.pos = 0;
                    if (!isClientSide) {
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
        /* ??? can speed up     
        @Override
        public int read(byte[] b, int off, int len) throws IOException {
           System.arraycopy 
        }
         */
    }

    boolean isSocketOpen() {
        return !(this.socket == null || this.socket.isClosed());
//                || this.socket.isInputShutdown());
    }

    void closeSocket() {
        if (this.isSocketOpen()) {
            try {
                if (this.isSecure) {
                    ((SSLSocket) this.socket).close();
                } else {
//                    this.socket.shutdownInput(); // ???
                    this.socket.close();
                }
            } catch (IOException e) {
//                e.printStackTrace();
            }
        }
    }

    private int readFully(InputStream is, byte[] buf, int off, int len)
            throws IOException {
        int bytesCnt = 0;
        for (int n = is.read(buf, off, len); n >= 0 && bytesCnt < len;) {
            bytesCnt += n;
            n = is.read(buf, off + bytesCnt, len - bytesCnt);
        }
        return bytesCnt;
    }

    private void closeDueTo(int closeCode, Exception e) {
        if (status.code == 0 && status.error == null) {
            status.error = e;
        }
        close(closeCode, "");
    }

// unmask/mask payload
    private void umaskPayload(byte[] mask, byte[] payload, int len) {
        for (int i = 0; i < len; i++) {
//            payload[i] ^= mask[i % 4]; 
            payload[i] ^= mask[i & 3];
        }
    }

    private synchronized void sendFrame(int opFrame, byte[] payload, int len)
            throws IOException {
        if (status.code != 0) { // !isOpen()
// WebSocket closed or close handshake in progress            
            throw new SocketException("WebSocket closed");
        }
        boolean masked = this.isClientSide;
        byte[] header = new byte[14]; //hopefully initialized with zeros

        header[0] = (byte) opFrame;
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
                outStream.write(header, 0, headerLen);
                outStream.write(maskedPayload, 0, len);
            } else {
                outStream.write(header, 0, headerLen);
                outStream.write(payload, 0, len);
            }
            outStream.flush();
        } catch (IOException e) {
            this.status.code = WsStatus.ABNORMAL_CLOSURE;
            throw e;
        }
    }

}
