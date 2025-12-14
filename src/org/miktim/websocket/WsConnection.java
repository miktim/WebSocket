/*
 * WsConnection. MIT (c) 2020-2025 miktim@mail.ru
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
    private final boolean isSecure; // SSL connection
    private final boolean isClientSide;
    URI requestURI;
    String subProtocol = null; // handshaked WebSocket subprotocol
    final WsParameters wsp;
    final WsStatus status = new WsStatus();
    List<WsConnection> connections = null; // backlink to the list of WebSocket or WsServer connections
    HttpHead requestHead = new HttpHead();
    HttpHead responseHead = new HttpHead();
    InputStream inStream;
    OutputStream outStream;

    /**
     * Sends streamed data in binary format or UTF-8 encoded text.
     *
     * @param is data input stream.
     * @param isText if true, inputstream is UTF-8 encoded text. Otherwise -
     * binary data.
     * @throws IOException
     */
    public void send(InputStream is, boolean isText) throws IOException {
        syncSend(is, isText);
    }

    /**
     * Sends binary data.
     *
     * @param message array of bytes.
     * <p>
     * <b>Note:</b> {@link WsError} "hides" {@link java.io.IOException}}</p>
     */
    public void send(byte[] message) {
        // throws IOException {
        try {
            syncSend(new ByteArrayInputStream(message), false);
        } catch (java.net.SocketException ex) {
            String msg = "send(byte[]) failed";
            closeDueTo(WsStatus.ABNORMAL_CLOSURE, msg, ex);
            throw new WsError(msg, ex);
        } catch (IOException ex) {
            String msg = "send(byte[]) failed";
            closeDueTo(WsStatus.ABNORMAL_CLOSURE, msg, ex);
            throw new WsError(msg, ex);
        }
    }

    /**
     * Sends text message.
     *
     * @param message text message. // * @throws IOException
     * <p>
     * <b>Note:</b> {@link WsError} "hides" {@link java.io.IOException}</p>
     */
    public void send(String message) {
        // throws IOException {
        try {
            syncSend(new ByteArrayInputStream(message.getBytes("UTF-8")), true);
        } catch (IOException ex) {
            String msg = "send(String) failed";
            closeDueTo(WsStatus.ABNORMAL_CLOSURE, msg, ex);
            throw new WsError(msg, ex);
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
     * Synchronized with WebSocket handshake
     *
     * @return true if so.
     */
    public boolean isOpen() {
        synchronized (status) {
            return isAlive() && status.code == WsStatus.IS_OPEN;
        }
    }

    /**
     * Is TLS connection.
     *
     * @return true if so.
     */
    public boolean isSecure() {
        return isSecure;
    }

    /**
     * Is client side connection.
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
        if (!handler.equals(newHandler)) {
            handler.onClose(this, getStatus());
            handler = newHandler;
            status.remotely = false;
            isPrimaryHandler = false;
            newHandler.onOpen(this, subProtocol);
        } else {
            throw new IllegalArgumentException();
        }
    }

    /**
     * Is the connection handler the primary handler.
     *
     * @return true if so
     * @since 4.3
     */
    public boolean isPrimaryHandler() {
        return isPrimaryHandler;
    }

    /**
     * Returns the connection parameters.
     *
     * @return the connection parameters.
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
     * @return port number. The listening port (on the server side)
     * or connecting port (on the client side)
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
// TODO: soften the synchronization object    
    private void syncSend(InputStream is, boolean isText)
            throws IOException {
        synchronized(this) {
        int op = isText ? WsListener.OP_TEXT : WsListener.OP_BINARY;
        try {
            byte[] buf = new byte[wsp.payloadBufferLength];
            int len = 0;
            while ((len = WsIo.readFully(is, buf, 0, buf.length)) == buf.length) {
                WsIo.sendFrame(this, op, buf, buf.length);
                op = WsListener.OP_CONTINUATION;
            }
// be sure to send the final frame even if eof is detected (payload length = 0)!            
            WsIo.sendFrame(this, op | WsListener.OP_FINAL, buf, len >= 0 ? len : 0); //
        } catch (IOException e) {
            closeDueTo(WsStatus.ABNORMAL_CLOSURE, "Send failed", e);
            throw e;
        }
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
     * - the closing code outside 1000-4999 is replaced by 1005 (NO_STATUS) and
     * the reason is ignored;<br>
     * - a reason that is longer than 123 BYTES is truncated;<br>
     * - closing handshake started;<br>
     * - blocks outgoing messages (send methods throw IOException);<br>
     * - isOpen() returns false;<br>
     * - calls onClose handler method;<br>
     * - incoming messages are available until the closing handshake completed
     * or timeout is over.
     * </p>
     *
     * @param code closing code.
     * @param reason closing reason.
     * @see WsStatus
     */
    public void close(int code, String reason) {
        synchronized (this) {
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
                    } catch (UnsupportedEncodingException ignored) {
                    }
                    byteReasonLen = Math.min(123, byteReason.length);
                    System.arraycopy(byteReason, 0, payload, 2, byteReasonLen);
                    payloadLen += byteReasonLen;
                }
                try {
                    status.remotely = false;
                    status.reason = new String(byteReason, 0, byteReasonLen, "UTF-8");
                    socket.setSoTimeout(wsp.handshakeSoTimeout);
                    WsIo.sendControlFrame(this, WsListener.OP_CLOSE, payload, payloadLen);
                    status.code = code; // disable output
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
                    status.code = code;
                    closeSocket();
//                    e.printStackTrace();
                }
            }
        } //synchronized
    }

    /**
     * Waiting for the WebSocket handshake to complete
     *
     * @return this connection
     */
    public WsConnection ready() {
        synchronized (this) {
            while (status.code == WsStatus.IS_INACTIVE) {
                try {
                    wait(wsp.getHandshakeSoTimeout());
                } catch (Exception ex) {
                    return null;
                }
            }
        }
        return this;
    }

    // WebSocket server side connection constructor
    WsConnection(Socket s, Handler h, WsParameters wsp, boolean secure) {
        this.isClientSide = false;
        this.socket = s;
        this.handler = h;
        this.isSecure = secure;
        this.wsp = wsp;
    }

    // WebSocket client connection constructor
    WsConnection(Socket s, Handler h, WsParameters wsp, URI uri) {
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
        setName("WsConnection" + getName());
        if (WsHandshake.waitHandshake(this)) { // WebSocket handshake Ok?
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
// nothing to do
        } catch (Throwable err) {
            conn.status.code = WsStatus.ABNORMAL_CLOSURE;
            conn.status.reason = handlerName + " handler failed";
            conn.status.error = err;
            conn.closeSocket();
            conn.connections.remove(conn);
            conn.clearQueue(conn);
            if (!handlerName.equals("onError")) {
                callHandler(conn, err);
            }
            throw new WsError(conn.status.reason, err);
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
        WsMessage is;
        while (messageQueue.size() > 0 || listener.isAlive()) {
            try {
                is = messageQueue.take();
                if (is.available() < 0) {
                    break;
                }
                callHandler(this, is);
            } catch (InterruptedException ex) {
                closeSocket();
                break;
            }
        }
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

    /**
     * *
     * Returns WebSocket handshake HTTP request headers.
     *
     * @return clone of HTTP headers. Multiple header values are comma
     * separated.
     */
//    public Map<String, String> getRequestHead() {
//        return new HttpHead(requestHead);
//    }
    /**
     * *
     * Returns WebSocket handshake HTTP response headers.
     *
     * @return clone of HTTP headers. Multiple header values are comma
     * separated.
     */
//    public Map<String, String> getResponseHead() {
//        return new HttpHead(responseHead);
//    }
    /**
     * *
     * The interface allows adding user-defined HTTP headers.
     *
     * Extend {@link Handler} with this interface if necessary. For example:
     * <pre>
     * interface MyHandler
     *     extends WsConnection.Handler, WsConnection.OnRequest{};
     * </pre>
     */
//    public interface OnRequest {
    /**
     * *
     * Method called during the WebSocket HTTP handshake.
     * <p>
     * On the client side called before sending WebSocket request;<br>
     * On the server side called before sending WebSocket response.
     * </p>
     *
     * @param conn WebSocket connection.
     * @param head clone of the HTTP WebSocket request headers.
     * @return user-defined HTTP headers or null:<br>
     * - on the client side, the returned headers are added to the WebSocket
     * request;<br>
     * - on the server side, the returned headers are added to the WebSocket
     * response.<br>
     * Map entries is: key - the header name, value - a single-line header value
     * or an empty string.<br>
     * Multiple header values ​​must be separated by commas.
     * @see WsConnection#getRequestHead()
     * @see WsConnection#getResponseHead()
     */
//        public Map<String, String> onRequest(WsConnection conn, Map<String, String> head);
//    };
    /**
     * WebSocket connection event handler.
     * <p>
     * There are several scenarios for handling connection events:<br>
     * - onError - onClose, when TLS/WebSocket handshake failed;<br>
     * - onOpen - [onMessage - onMessage - ...] [- onError] - onClose.<br>
     * Any WebSocket error closes the connection and throws a {@link WsError}.
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
         * The WebSocket message is represented by an input stream of binary
         * data or UTF-8 encoded text.
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
