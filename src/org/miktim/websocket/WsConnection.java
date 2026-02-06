/*
 * WsConnection. MIT (c) 2020-2026 miktim@mail.ru
 * WebSocket client-side or server-side connection.
 * WebSocket handshaking. Messaging. Events handling.
 *
 * Created: 2020-03-09
 */
package org.miktim.websocket;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.URI;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.LinkedBlockingDeque;
import javax.net.ssl.SSLSocket;

/**
 * WebSocket client-side or server-side connection.
 */
public final class WsConnection extends Thread {

    final Socket socket;
    Handler handler;
    final WsParameters wsp;
    private final boolean isSecure; // SSL connection
    private final boolean isClientSide;
//    private final byte[] payloadBuffer;

    final WsStatus status = new WsStatus();
    InputStream inStream; // initialized in WsHandshake.waitHandshake()
    OutputStream outStream;  // initialized in WsHandshake.waitHandshake()
    URI requestURI;
    String subProtocol = null; // handshaked WebSocket subprotocol
    List<WsConnection> connections = null; // backlink to the list of WebSocket or WsServer connections
    HttpHead requestHead = new HttpHead();
    HttpHead responseHead = new HttpHead();

    /**
     * Sends streamed binary data or UTF-8 encoded text.
     *
     * @param is data input stream.
     * @param isText if true, input stream is UTF-8 encoded text. Otherwise -
     * binary data.
     * @throws IOException
     * @throws NullPointerException
     */
    public void send(InputStream is, boolean isText) throws IOException {
        syncSend(is, isText);
    }

    /**
     * Sends binary data.
     *
     * @param message array of bytes.
     * @throws WsError on any exception
     *
     */
    public void send(byte[] message) {
//        throws IOException {
        try {
            syncSend(new ByteArrayInputStream(message), false);
        } catch (Throwable th) {
            throw new WsError("send(byte[]) error", th);
        }
    }

    /**
     * Sends text message.
     *
     * @param message text message.
     * @throws WsError on any exception
     */
    public void send(String message) {
//        throws IOException {
        try {
            syncSend(new ByteArrayInputStream(message.getBytes("UTF-8")), true);
        } catch (Throwable th) {
            throw new WsError("send(String) error", th);
        }
    }

    /**
     * Returns handshaked WebSocket subprotocol.
     *
     * @return WebSocket subprotocol or null.
     * @see WsParameters#setSubProtocols(String[])
     */
    public String getSubProtocol() {
        return subProtocol;
    }

    /**
     * Returns connection status.
     *
     * @return clone of the connection status
     */
    synchronized public WsStatus getStatus() {
        return status.deepClone();
    }

    /**
     * Checks connection is open.
     *
     * @return true if so.
     */
    public boolean isOpen() {
        return isAlive() && status.code == WsStatus.IS_OPEN;
    }

    /**
     * Checks for TLS connection.
     *
     * @return true if so.
     */
    public boolean isSecure() {
        return isSecure;
    }

    /**
     * Checks whether this is a client-side connection.
     *
     * @return true if so.
     */
    public boolean isClientSide() {
        return isClientSide;
    }

    /**
     * Sets the secondary handler for this connection.
     * <br>
     * Sets the connection handler. Calls onClose in the old handler
     * (conn.isOpen() returns true), then calls onOpen in the new handler.
     *
     * @param newHandler new connectin handler.
     */
    boolean isPrimaryHandler = true;

    public synchronized void setHandler(Handler newHandler) { // 
        if (!handler.equals(newHandler) && Thread.currentThread().equals(this)) {
            status.remotely = false;
            callHandler(this, getStatus());
            handler = newHandler;
            isPrimaryHandler = false;
            callHandler(this, subProtocol);
        } else {
            throw new IllegalStateException();
        }
    }

    /**
     * Checks if the connection handler is the primary handler.
     *
     * @return true if so
     * @since 4.3
     */
    public boolean isPrimaryHandler() {
        return isPrimaryHandler;
    }

    /**
     * Returns a clone of the connection parameters.
     */
    public WsParameters getParameters() {
        return wsp.deepClone();
    }

    /**
     * Returns the Socket object of this connection.
     *
     * @deprecated
     */
    @Deprecated
    public Socket getSocket() {
        return socket;
    }

    /**
     * Returns the name of the remote host.
     *
     * @return the remote hostname or null if it is unavailable.
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
        } catch (Exception ignore) {
        }
        return null;
    }

    /**
     * Returns the connection port number.
     *
     * @return port number. The listening port (on the server-side) or
     * connecting port (on the client-side)
     */
    public int getPort() {
        if (isClientSide) {
            return socket.getPort();
        }
        return socket.getLocalPort();
    }

    /**
     * Returns the path from the http request.
     *
     * @return requested path or null.
     */
    public String getPath() {
        return requestURI == null ? null : requestURI.getPath();
    }

    /**
     * Returns the query from the http request.
     *
     * @return requested guery or null.
     */
    public String getQuery() {
        return requestURI == null ? null : requestURI.getQuery();
    }

    /**
     * Returns the TLS connection protocol.
     *
     * @return protocol name or null for insecure connections.
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
    private final Object syncSend = new Object();

    private void syncSend(InputStream is, boolean isText)
            throws IOException {
        synchronized (syncSend) {
            int op = isText ? WsListener.OP_TEXT : WsListener.OP_BINARY;
            byte[] payloadBuffer = new byte[wsp.payloadBufferLength];
            int len = 0;
            while ((len = WsIo.readFully(is, payloadBuffer, 0, payloadBuffer.length)) == payloadBuffer.length) {
                WsIo.sendFrame(this, op, payloadBuffer, payloadBuffer.length);
                op = WsListener.OP_CONTINUATION;
            }
// be sure to send the final frame even if eof is detected (payload length = 0)!            
            WsIo.sendFrame(this, op | WsListener.OP_FINAL, payloadBuffer, len >= 0 ? len : 0); //
        } // synchronized
    }

    /**
     * Closes this connection with status code 1000 (NORMAL_CLOSURE) and empty
     * reason.
     * <br><p>
     * See: <b></b></p> {@link WsConnection#close(int, String)}
     */
    public void close() {
        close("");
    }

    /**
     * Closes this connection with status code 1000 (NORMAL_CLOSURE) and reason.
     * <br><p>
     * <b>See: </b></p> {@link WsConnection#close(int, String)}
     */
    public void close(String reason) {
        close(WsStatus.NORMAL_CLOSURE, reason);
    }

    Timer closeTimer;

    void cancelCloseTimer() {
        if (closeTimer != null) {
            closeTimer.cancel();
        }
    }

    /**
     * Closes this connection with specified code and reason.
     * <p>
     * Close notes:<br>
     * - the closing code outside 1000-4999 is replaced by 1005 (NO_STATUS);<br>
     * - status code 1005 sends close frame without code an reason;<br>
     * - status code 1006 (ABNORMAL_CLOSURE) close connection immediately
     * without sending close frame;<br>
     * - a reason that is longer than 123 BYTES is truncated;<br>
     * - method blocks outgoing messages (send methods throw IOException);<br>
     * - isOpen() returns false;<br>
     * - incoming messages are available until the closing handshake completed
     * or timeout is over.
     * </p>
     *
     * @param code closing code.
     * @param reason closing reason.
     * @see WsStatus
     */
    synchronized public void close(int code, String reason) {
        if (status.code == WsStatus.IS_OPEN) {
            status.remotely = false;
            if (reason == null) {
                reason = "";
            }
            code = (code < 1000 || code > 4999) ? WsStatus.NO_STATUS : code;
            byte[] payload = new byte[125];
            int payloadLen = 2;
            int byteReasonLen = 0;
            payload[0] = (byte) (code >>> 8);
            payload[1] = (byte) (code & 0xFF);
            payloadLen = 2;
            try {
// TODO: downgrade Android API 19 to API 16 here and further               
//                    byteReason = (reason == null ? "" : reason)
//                      .getBytes(StandardCharsets.UTF_8);
                byte[] byteReason = reason.getBytes("UTF-8");
                byteReasonLen = Math.min(123, byteReason.length);
                System.arraycopy(byteReason, 0, payload, 2, byteReasonLen);
                payloadLen += byteReasonLen;
                reason = new String(byteReason, 0, byteReasonLen, "UTF-8");
            } catch (UnsupportedEncodingException ignored) {
            }

            if (code == WsStatus.ABNORMAL_CLOSURE) {
                closeSocket();
            }

            try {
                socket.setSoTimeout(wsp.handshakeSoTimeout);
                WsIo.sendControlFrame(this, WsListener.OP_CLOSE, payload,
                        code == WsStatus.NO_STATUS ? 0 : payloadLen);
//                    status.code = code; // disable output
//                    socket.shutdownOutput(); // not good for SSLSocket
// force closing socket
                closeTimer = new Timer(true);
                closeTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        setName("WsClose" + getName());
                        closeSocket();
                    }
                }, wsp.handshakeSoTimeout);
            } catch (Exception e) {
            }
            status.set(code, reason, false);
        }
    }

    /**
     * Waiting for the WebSocket handshake to complete.
     *
     * @return this connection or null if timeout is over
     * @see WsParameters#setHandshakeSoTimeout(int)
     */
    public WsConnection ready() {
        synchronized (this) {
            if (status.code == WsStatus.IS_INACTIVE) {
                try {
                    wait(wsp.getHandshakeSoTimeout());
                } catch (InterruptedException ex) {
                }
                if (status.code == WsStatus.IS_INACTIVE) {
                    return null;
                }
            }
        }
        return this;
    }

    private WsConnection(boolean side, Socket s, Handler h, WsParameters wsp) {
        socket = s;
        handler = h;
        this.wsp = wsp;
        isSecure = (s instanceof SSLSocket);
        isClientSide = side;
//        payloadBuffer = new byte[wsp.payloadBufferLength];
    }

    // WebSocket server side connection constructor
    WsConnection(Socket s, Handler h, WsParameters wsp, boolean secure) {
        this(false, s, h, wsp);
//        this.isClientSide = false;
//        this.handler = h;
//        this.isSecure = secure;
    }

    // WebSocket client connection constructor
    WsConnection(Socket s, Handler h, WsParameters wsp, URI uri) {
        this(true, s, h, wsp);
//        this.isClientSide = true;
        this.requestURI = uri;
//        this.isSecure = uri.getScheme().equals("wss");
    }

    @Override
    public void start() {
        connections.add(this);
        super.start();
    }

    @Override
    public void run() {
        synchronized (this) {
            setName("WsConnection" + getName());
            WsHandshake.waitHandshake(this);
            this.notifyAll();
        }
        if (status.code == WsStatus.IS_OPEN) { // WebSocket handshake Ok?
            callHandler(this, subProtocol); // onOpen
            waitMessages();
        }
        callHandler(this, getStatus()); // onClose
        if (!isClientSide) {
            closeSocket();
        }
        connections.remove(this);
    }

    static void callHandler(WsConnection conn, Object arg) {
        String handlerName = "";
        try {
            if (arg == null || arg instanceof String) {
                handlerName = "onOpen";
                conn.handler.onOpen(conn, arg == null ? null : (String) arg);
            } else if (arg instanceof WsMessage) {
                handlerName = "onMessage";
                conn.handler.onMessage(conn, (WsMessage) arg);
            } else if (arg instanceof Throwable) {
                handlerName = "onError";
                conn.handler.onError(conn, (Throwable) arg);
            } else if (arg instanceof WsStatus) {
                handlerName = "onClose";
                conn.handler.onClose(conn, conn.getStatus());
            } else {
                throw new RuntimeException("Unknown handler arg: "
                        + arg.getClass().getName());
            }
        } catch (WsError err) {
            conn.closeDueTo(WsStatus.ABNORMAL_CLOSURE, err.getMessage(), err.getCause());
        } catch (Throwable err) {
            synchronized (conn) {
                conn.status.set(WsStatus.ABNORMAL_CLOSURE,
                        handlerName + " handler crashed",
                        false);
                conn.status.error = err;
                conn.closeSocket();
                conn.clearQueue(conn);
            }
            if (!handlerName.equals("onError")) {
                callHandler(conn, err);
            }
            err.printStackTrace();
        }
    }

    LinkedBlockingDeque<WsMessage> messageQueue;

    void clearQueue(WsConnection conn) {
        if (messageQueue != null) {
            messageQueue.clear();
        }
        conn.interrupt();
    }

    void waitMessages() {
        messageQueue = new LinkedBlockingDeque<WsMessage>(wsp.maxMessages);
        WsListener listener = new WsListener(this);
        listener.start();
        do {
            try {
                WsMessage msg = messageQueue.take();
                if (msg.available < 0) {
                    break;
                }
                callHandler(this, msg);
            } catch (InterruptedException ex) {
                closeDueTo(WsStatus.ABNORMAL_CLOSURE, "WebSocket connection interrupted", ex);
                break;
            }
        } while (messageQueue.size() > 0 || listener.isAlive());
    }

    boolean isSocketOpen() {
        return !(this.socket == null || this.socket.isClosed());
    }

    void closeSocket() {
        if (socket.isClosed()) {
            return;
        }
        try {
//            connections.remove(this);
            socket.close();
        } catch (IOException ignore) {
        }
    }

    void closeDueTo(int closeCode, String reason, Throwable err) {
        if (status.code == WsStatus.IS_OPEN) {
            status.error = err;
            close(closeCode, reason);
            if (err != null) {
                callHandler(this, err);
            }
        }
    }

    /*/*
     * Returns WebSocket handshake HTTP request headers.
     *
     * @return clone of HTTP headers. Multiple header values are comma
     * separated.
     */
//    public Map<String, String> getRequest() {
//        return new HttpHead(requestHead);
//    }
    /*/*
     * Returns WebSocket handshake HTTP response headers.
     *
     * @return clone of HTTP headers. Multiple header values are comma
     * separated.
     */
//    public Map<String, String> getResponse() {
//        return new HttpHead(responseHead);
//    }
    /**
     * This interface allows adding user-defined HTTP headers.
     * <br>
     * Extend handlers using this interface as needed. For example:
     * <pre>
     * interface MyHandler
     *     extends WsConnection.Handler, WsConnection.OnRequest{};
     * </pre>
     */
//    public interface OnRequest {

        /*/*
         * Method called during the WebSocket HTTP handshake.
         * <p>
         * On the client side, the returned headers are added to the WebSocket
         * request.<br>
         * On the server side, the returned headers are added to the WebSocket
         * response.
         * </p>
         *
         * @param conn WebSocket connection.
         * @param request clone of the HTTP WebSocket request headers.
         * @return user-defined HTTP headers (RFC 2616) or null:<br>
         * Map entries is:<br>
         * key - the header name,<br>
         * value - the header value.<br>
         * Multiple header values ​​must be separated by commas.
         * @see WsConnection#getRequest()
         * @see WsConnection#getResponse()
         */
//        public Map<String, String> onRequest(WsConnection conn, Map<String, String> request);
//    };
    
    /**
     * WebSocket connection event handler.
     * <p>
     * Typical connection event scenarios:<br>
     * - onError - onClose, when TLS/WebSocket handshake failed;<br>
     * - onOpen [- onMessage - onMessage - ...] [- onError] - onClose.<br>
     * Any WebSocket error closes the connection.
     * </p>
     */
    public interface Handler {

        /**
         * Called when opening a WebSocket connection.
         *
         * @param conn WebSocket connection.
         * @param subProtocol the negotiated WebSocket sub protocol or null if
         * the client did not requests it or if the server does not agree to any
         * of the client's requested sub protocols.
         * @see WsParameters#setSubProtocols(String[])
         */
        public void onOpen(WsConnection conn, String subProtocol);

        /**
         * Called when a WebSocket message is received.
         * <p>
         * The WebSocket message is an input stream of binary data or UTF-8
         * encoded text.
         * </p>
         *
         * @param conn WebSocket connection.
         * @param msg WebSocket message input stream.
         * @since 5.0
         */
        public void onMessage(WsConnection conn, WsMessage msg);

        /**
         * Called when the WebSocket connection error occurs.
         *
         * @param conn WebSocket connection.
         * @param err connection error.
         */
        public void onError(WsConnection conn, Throwable err);

        /**
         * Called when the WebSocket connection closed.
         *
         * @param conn WebSocket connection.
         * @param status clone of the connection status.
         */
        public void onClose(WsConnection conn, WsStatus status);

    }

}
