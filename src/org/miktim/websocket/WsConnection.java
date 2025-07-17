/*
 * WsConnection. WebSocket client/server connection, MIT (c) 2020-2025 miktim@mail.ru
 *
 * SSL/WebSocket handshaking. Messaging. Events handling.
 *
 * Created: 2020-03-09
 */
package org.miktim.websocket;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.ProtocolException;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingDeque;
import javax.net.ssl.SSLSocket;

/**
 * WebSocket client side or server side connection.
 */
public final class WsConnection extends Thread {

    /**
     * Max count of queued WebSocket messages.
     * <p>
     * Overflow of the incoming message queue leads to an error and connection
     * closure with status code 1008 (POLICY_VIOLATION)
     * </p>
     *
     * @see WsStatus
     */
    public static final int MESSAGE_QUEUE_CAPACITY = 3;
    private static final String SERVER_AGENT = "WsLite/" + WebSocket.VERSION;

    final Socket socket;
    private Handler handler;
    private final boolean isSecure; // SSL connection
    private final boolean isClientSide;
    private URI requestURI;
    private String subProtocol = null; // handshaked WebSocket subprotocol
    final WsParameters wsp;
    final WsStatus status = new WsStatus();
    List<WsConnection> connections = null; // list of connections to a websocket or server

    /**
     * Sending streamed data in binary format or UTF-8 encoded text.
     *
     * @param is data input stream.
     * @param isUTF8Text stream is UTF-8 encoded text.
     * @throws IOException
     */
    public void send(InputStream is, boolean isUTF8Text) throws IOException {
        syncSend(is, isUTF8Text);
    }

    /**
     * Send binary data.
     *
     * @param message array of bytes.
     * @throws IOException
     */
    public void send(byte[] message) throws IOException {
        syncSend(new ByteArrayInputStream(message), false);
    }

    /**
     * Send text message.
     *
     * @param message text message.
     * @throws IOException
     */
    public void send(String message) throws IOException {
        syncSend(new ByteArrayInputStream(message.getBytes("UTF-8")), true);
    }

    /**
     * Returns handshaked WebSocket subprotocol.
     *
     * @return WebSocket subprotocol or null.
     * @see WsParameters
     */
    public String getSubProtocol() {
        return subProtocol;
    }

    /**
     * Returns connection status.
     *
     * @return status deep clone.
     * @see WsStatus
     */
    public WsStatus getStatus() {
        synchronized (status) {
            return status.deepClone();
        }
    }

    /**
     * Is connection open.
     *
     * @return true if is.
     */
    public boolean isOpen() {
        synchronized (status) {
            return status.code == WsStatus.IS_OPEN;
        }
    }

    /**
     * Is TLS connection.
     *
     * @return true if is.
     */
    public boolean isSecure() {
        return isSecure;
    }

    /**
     * Is client side connection.
     *
     * @return true if is.
     */
    public boolean isClientSide() {
        return isClientSide;
    }

    /**
     * List connections.
     * <p>
     * The client connection returns only itself.
     * </p> 
     * @return array of connections.
     */
    public WsConnection[] listConnections() {
        return isClientSide ? new WsConnection[]{this}
                : connections.toArray(new WsConnection[0]);
    }

    /**
     * Replace handler for this connection.
     * <p>
     * Calls onClose in the old handler (conn.isOpen() returns true), then calls
     * onOpen in the new handler.
     * </p>
     * @param newHandler new connectin handler.
     */
    public synchronized void setHandler(Handler newHandler) {
        if (isOpen()) {
            try {
                handler.onClose(this, status);
                handler = newHandler;
                newHandler.onOpen(this, subProtocol);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            handler = newHandler;
        }
    }

    /**
     * Returns connection parameters.
     *
     * @return deep clone of connection parameters.
     */
    public WsParameters getParameters() {
        return wsp;
    }

    /**
     * Returns Socket object of this connection.
     *
     * @return Socket object.
     */
    public Socket getSocket() {
        return socket;
    }

    /**
     * Get remote host name.
     *
     * @return the remote hostname or null value if it is unavailable.
     */
    public String getPeerHost() {
        try {
            if (isClientSide) {
                return java.net.IDN.toUnicode(requestURI.getHost());
            }
            if (isSecure) {
                return ((SSLSocket) socket).getSession().getPeerHost();
// TODO: downgrade Android API 19 to API 16
//            } else {
//                return ((InetSocketAddress) socket.getRemoteSocketAddress()).getHostString();
            }
        } catch (Exception e) {
        }
        return null;
    }

    /**
     * Get the connected port.
     *
     * @return port number.
     */
    public int getPort() {
        return socket.getPort();
    }

    /**
     * Get http request path.
     *
     * @return requested path or null.
     */
    public String getPath() {
        if (this.requestURI != null) {
            return requestURI.getPath();
        }
        return null;
    }

    /**
     * Get http request query.
     *
     * @return requested guery or null.
     */
    public String getQuery() {
        if (this.requestURI != null) {
            return requestURI.getQuery();
        }
        return null;
    }

    /**
     * Get connection TLS protocol.
     *
     * @return protocol name or null.
     */
    public String getSSLSessionProtocol() {
        if (this.isSecure() && this.isSocketOpen()) {
            return ((SSLSocket) this.socket).getSession().getProtocol();
        }
        return null;
    }

    /*
        // allocate buffer from 1/4 to payloadBufferLength
        byte[] getBuffer(byte[] buffer) {
            if (buffer == null) {
                buffer = EMPTY_PAYLOAD;
            }
            int mpbl = WsParameters.MIN_PAYLOAD_BUFFER_LENGTH;
            int pbl = wsp.getPayloadBufferLength();
            if (buffer.length >= pbl) {
                return buffer;
            }
            if (buffer.length == 0) {
                return new byte[(pbl / 4 > mpbl * 4) ? pbl / 4 : pbl];
            }
            return new byte[Math.min(pbl, buffer.length * 2)];
        }
     */
    private synchronized void syncSend(InputStream is, boolean isText)
            throws IOException {
        int op = isText ? WsListener.OP_TEXT : WsListener.OP_BINARY;
        try {
            byte[] buf = new byte[wsp.payloadBufferLength];
            int len = 0;
            while ((len = readFully(is, buf, 0, buf.length)) == buf.length) {
                sendFrame(op, buf, buf.length);
                op = WsListener.OP_CONTINUATION;
            }
// be sure to send the final frame even if eof is detected (payload length = 0)!            
            sendFrame(op | WsListener.OP_FINAL, buf, len >= 0 ? len : 0); //
        } catch (IOException e) {
            closeDueTo(WsStatus.INTERNAL_ERROR, "Send failed", e);
            throw e;
        }
    }

    /**
     * Close connection without close code and reason.
     * <p>
     * Status code sets to 1005 (NO_STATUS) and reason sets to null.<br>
     * See close notes below.
     * </p>
     */
    public void close() {
        close(WsStatus.NO_STATUS, "");
    }

    /**
     * Close connection.
     * <p>
     * Close notes:<br>
     * - the closing code outside 1000-4999 is replaced by 1005 (NO_STATUS) and
     * the reason is ignored;<br>
     * - a reason that is longer than 123 bytes is truncated;<br>
     * - closing handshake started;<br>
     * - blocks outgoing messages (send methods throw IOException);<br>
     * - isOpen() returns false;<br>
     * - incoming messages are available until the closing handshake completed
     * or timeout is over.
     * </p>
     *
     * @param code closing code.
     * @param reason closing reason.
     * @see WsStatus
     * @see WsParameters#setHandshakeSoTimeout
     * @see WsParameters#getHandshakeSoTimeout
     */
    public void close(int code, String reason) {
        synchronized (status) {
            if (status.code == WsStatus.IS_OPEN) {
                code = (code < 1000 || code > 4999) ? WsStatus.NO_STATUS : code;
                byte[] payload = new byte[125];
                byte[] byteReason = new byte[0];
                int payloadLen = 0;
                int byteReasonLen = 0;
                if (code != WsStatus.NO_STATUS) {
                    payload[0] = (byte) (code >>> 8);
                    payload[1] = (byte) (code & 0xFF);
                    payloadLen = 2;
                    try {
// TODO: downgrade Android API 19 to API 16 here and further               
//                    byteReason = (reason == null ? "" : reason)
//                      .getBytes(StandardCharsets.UTF_8);
                        byteReason = (reason == null ? "" : reason).getBytes("UTF-8");
                    } catch (UnsupportedEncodingException e) {
//                e.printStackTrace();
                    }
                    byteReasonLen = Math.min(123, byteReason.length);
                    System.arraycopy(byteReason, 0, payload, 2, byteReasonLen);
                    payloadLen += byteReasonLen;
                }
                try {
                    status.remotely = false;
                    status.reason = new String(byteReason, 0, byteReasonLen, "UTF-8");
                    socket.setSoTimeout(wsp.handshakeSoTimeout);
                    sendControlFrame(WsListener.OP_CLOSE, payload, payloadLen);
                    status.code = code; // disable output
// force closing socket 
                    (new Timer(true)).schedule(new TimerTask() {
                        @Override
                        public void run() {
                            closeSocket();
                        }
                    }, wsp.handshakeSoTimeout);
// ???
//                    socket.shutdownOutput();
                } catch (Exception e) {
                    status.code = code;
                    closeSocket();
//                    e.printStackTrace();
                }
            }
        }
    }

    // WebSocket server side connection constructor
    WsConnection(Socket s, Handler h, boolean secure, WsParameters wsp) {
        this.isClientSide = false;
        this.socket = s;
        this.handler = h;
        this.isSecure = secure;
        this.wsp = wsp;
    }

    // WebSocket client connection constructor
    WsConnection(Socket s, Handler h, URI uri, WsParameters wsp) {
        this.isClientSide = true;
        this.socket = s;
        this.handler = h;
        this.requestURI = uri;
        this.isSecure = uri.getScheme().equals("wss");
        this.wsp = wsp;
    }

    @Override
    public void start() {
        connections.add(this);
        super.start();
    }

    @Override
    public void run() {
        try {
            if (waitConnection()) {
                this.handler.onOpen(this, subProtocol);
                waitMessages();
            } else {
                // also called from closeDueTo                
                handler.onError(this, status.error);
            }
            handler.onClose(this, getStatus());
            if (!isClientSide) {
                closeSocket();
            }
        } catch (Exception e) { // connection handler exception
            closeSocket();
            e.printStackTrace();
        }
        connections.remove(this);
    }

    InputStream inStream;
    OutputStream outStream;

    private boolean waitConnection() {
        synchronized (status) {
            try {
                inStream = new BufferedInputStream(socket.getInputStream());
                outStream = new BufferedOutputStream(socket.getOutputStream());
                if (isClientSide) {
                    handshakeServer();
                } else {
                    handshakeClient();
                }
                socket.setSoTimeout(wsp.connectionSoTimeout);
                status.code = WsStatus.IS_OPEN;
                return true;
            } catch (Exception e) {
                status.reason = "Handshake failed";
                status.error = e;
                status.code = WsStatus.PROTOCOL_ERROR;
                return false;
            }
        }
    }

    final LinkedBlockingDeque<WsInputStream> messageQueue
            = new LinkedBlockingDeque<WsInputStream>(MESSAGE_QUEUE_CAPACITY);

    void waitMessages() {
        WsListener listener = new WsListener(this);
        listener.start();
        WsInputStream is;
        while (messageQueue.size() > 0 || listener.isAlive()) {
            try {
                is = messageQueue.take();
                if (is.available() < 0) {
                    break;
                }
                handler.onMessage(this, is, is.isText());
            } catch (InterruptedException ex) {
            }
        }
    }

    private void handshakeClient()
            throws IOException, URISyntaxException, NoSuchAlgorithmException {
        HttpHead requestHead = (new HttpHead()).read(inStream);
        String[] parts = requestHead.get(HttpHead.START_LINE).split(" ");
        this.requestURI = new URI(parts[1]);
        String key = requestHead.get("Sec-WebSocket-Key");

        HttpHead responseHead = (new HttpHead()).set("Server", SERVER_AGENT);
        if (parts[0].equals("GET")
                && key != null
                && requestHead.get("Upgrade").toLowerCase().equals("websocket")
                && requestHead.get("Sec-WebSocket-Version").equals("13")
                && setSubprotocol(
                        requestHead.getValues("Sec-WebSocket-Protocol"),
                        responseHead)) {
            responseHead
                    .set(HttpHead.START_LINE, "HTTP/1.1 101 Upgrade")
                    .set("Upgrade", "websocket")
                    .set("Connection", "Upgrade,keep-alive")
                    .set("Sec-WebSocket-Accept", sha1Hash(key))
                    .write(outStream);
        } else {
            responseHead
                    .set(HttpHead.START_LINE, "HTTP/1.1 400 Bad Request")
                    .set("Connection", "close")
                    .write(outStream);
            status.remotely = false;
            throw new ProtocolException("WebSocket handshake failed");
        }
    }

    private boolean setSubprotocol(String[] requestedSubps, HttpHead rs) {
        if (requestedSubps == null) {
            return true;
        }
        if (wsp.subProtocols != null) {
            for (String agreedSubp : requestedSubps) {
                for (String subp : wsp.subProtocols) {
                    if (agreedSubp.equals(subp)) {
                        this.subProtocol = agreedSubp;
                        rs.set("Sec-WebSocket-Protocol", agreedSubp); // response headers
                        return true;
                    }
                }
            }
        }
        return true;
    }

    private void handshakeServer()
            throws IOException, URISyntaxException, NoSuchAlgorithmException {
        String key = base64Encode(randomBytes(16));

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
            requestHead.setValues("Sec-WebSocket-Protocol", wsp.subProtocols);
        }
        requestHead.write(outStream);

        HttpHead responseHead = (new HttpHead()).read(inStream);
        this.subProtocol = responseHead.get("Sec-WebSocket-Protocol");
        if (!(responseHead.get(HttpHead.START_LINE).split(" ")[1].equals("101")
                && responseHead.get("Upgrade").toLowerCase().equals("websocket")
                && responseHead.get("Sec-WebSocket-Extensions") == null
                && responseHead.get("Sec-WebSocket-Accept").equals(sha1Hash(key))
                && checkSubprotocol())) {
            status.remotely = false;
            throw new ProtocolException("WebSocket handshake failed");
        }
    }

    private boolean checkSubprotocol() {
        if (this.subProtocol == null) {
            return true; // 
        }
        if (wsp.subProtocols != null) {
            for (String subp : wsp.subProtocols) {
                if (this.subProtocol.equals(subp)) { //
                    return true;
                }
            }
        }
        return false;
    }

    // generate "random" mask/key
    byte[] randomBytes(int len) {
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

    static String base64Encode(byte[] b) {
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

    static int readFully(InputStream is, byte[] buf, int off, int len)
            throws IOException {
        int bytesCnt = 0;
        for (int n = is.read(buf, off, len); n >= 0 && bytesCnt < len;) {
            bytesCnt += n;
            n = is.read(buf, off + bytesCnt, len - bytesCnt);
        }
        return bytesCnt;
    }

    boolean isSocketOpen() {
        return !(this.socket == null || this.socket.isClosed());
    }

    void closeSocket() {
        try {
            this.socket.close();
        } catch (IOException e) {
//            e.printStackTrace();
        }
    }

    void closeDueTo(int closeCode, String reason, Throwable e) {
        if (isOpen()) {
            status.error = e;
            close(closeCode, reason);
            if (e != null) {
                try {
                    handler.onError(this, status.error);
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }
    }

    void sendControlFrame(int opFrame, byte[] payload, int payloadLen)
            throws IOException {
        sendFrame(opFrame, Arrays.copyOf(payload, payloadLen), payloadLen);
    }

    void sendFrame(int opFrame, byte[] payload, int payloadLen)
            throws IOException {
        synchronized (outStream) {
            if (status.code != WsStatus.IS_OPEN) {
                throw new SocketException("WebSocket is closed");
            }
// client MUST mask payload, server MUST NOT        
            boolean masked = this.isClientSide;
            byte[] header = new byte[14]; //hopefully initialized with zeros

            header[0] = (byte) opFrame;
            int headerLen = 2;

            int tempLen = payloadLen;
            if (tempLen < 126) {
                header[1] = (byte) (tempLen);
            } else if (tempLen < 0x10000) {
                header[1] = (byte) 126;
                header[2] = (byte) (tempLen >>> 8);
                header[3] = (byte) tempLen;
                headerLen += 2;
            } else {
                header[1] = (byte) 127;
                headerLen += 4; // skip 4 zero bytes of 64bit payload length
                for (int i = 3; i > 0; i--) {
                    header[headerLen + i] = (byte) (tempLen & 0xFF);
                    tempLen >>>= 8;
                }
                headerLen += 4;
            }

            if (masked) {
                header[1] |= WsListener.MASKED_DATA;
                byte[] mask = randomBytes(4);
                System.arraycopy(mask, 0, header, headerLen, 4);
                headerLen += 4;
                umaskPayload(mask, payload, 0, payloadLen);
            }
            try {
                outStream.write(header, 0, headerLen);
                outStream.write(payload, 0, payloadLen);
                outStream.flush();
            } catch (IOException e) {
                throw e;
            }
        }
    }

// unmask/mask payload
    static void umaskPayload(byte[] mask, byte[] payload, int off, int len) {
        for (int i = 0; i < len; i++) {
            payload[off++] ^= mask[i & 3];
        }
    }

    /**
     * WebSocket connection events handler.
     * <p>
     * There are two scenarios for handling connection events:<br>
     * - onError - onClose, when SSL/WebSocket handshake failed;<br>
     * - onOpen - [onMessage - onMessage - ...] - [onError] - onClose.
     * </p>
     */
    public interface Handler {

        /**
         * On WebSocket connection open.
         * @param conn WebSocket connection.
         * @param subProtocol the negotiated WebSocket sub protocol or null 
         * if the client did not requests it or if the server does not agree
         * to any of the client's requested sub protocols.
         */
        public void onOpen(WsConnection conn, String subProtocol);

        /**
         * On WebSocket message receive.
         * <p>
         * - the WebSocket message is represented by an input stream of binary
         * data or UTF-8 encoded text;<br>
         * - exiting method closes the stream.
         * </p>
         * @param conn WebSocket connection.
         * @param is message input stream.
         * @param isUTF8Text if true, the stream is UTF-8 encoded text.
         */
        public void onMessage(WsConnection conn, InputStream is, boolean isUTF8Text);

        /**
         * On WebSocket connection or handler error occure.
         * <p>
         * - any error closes the WebSocket connection;<br>
         * - allocating large buffers may throw an OutOfMemoryError.
         * </p>
         * @param conn WebSocket connection.
         * @param e connection error.
         */
        public void onError(WsConnection conn, Throwable e);

        /**
         * On WebSocket connection close.
         *
         * @param conn WebSocket connection.
         * @param status connection status.
         * @see WsStatus
         */
        public void onClose(WsConnection conn, WsStatus status);

    }

    /**
     * @deprecated Use WsConnection.Handler interface instead.
     * @since 4.2
     */
    @Deprecated
    public interface EventHandler extends Handler {
    }
}
