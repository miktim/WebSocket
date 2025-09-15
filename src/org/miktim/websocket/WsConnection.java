/*
 * WsConnection. WebSocket client or server connection, MIT (c) 2020-2025 miktim@mail.ru
 *
 * WebSocket handshaking. Messaging. Events handling.
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
import java.net.Socket;
import java.net.URI;
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
     * closure with status code 1008 (POLICY_VIOLATION).
     * </p>
     *
     * @see WsStatus
     */
    public static final int MESSAGE_QUEUE_CAPACITY = 3;

    final Socket socket;
    Handler handler;
    private final boolean isSecure; // SSL connection
    private final boolean isClientSide;
    URI requestURI;
    String subProtocol = null; // handshaked WebSocket subprotocol
    final WsParameters wsp;
    final WsStatus status = new WsStatus();
    List<WsConnection> connections = null; // list of connections to a websocket or server

    InputStream inStream;
    OutputStream outStream;

    /**
     * Sends streamed data in binary format or UTF-8 encoded text.
     *
     * @param is data input stream.
     * @param isUTF8Text stream is UTF-8 encoded text.
     * @throws IOException
     */
    public void send(InputStream is, boolean isUTF8Text) throws IOException {
        syncSend(is, isUTF8Text);
    }

    /**
     * Sends binary data.
     *
     * @param message array of bytes.
     * @throws IOException
     */
    public void send(byte[] message) throws IOException {
        syncSend(new ByteArrayInputStream(message), false);
    }

    /**
     * Sends text message.
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
     * @see WsParameters.setSubProtocols()
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
     * @return true if so.
     */
    public boolean isOpen() {
        synchronized (status) {
            return status.code == WsStatus.IS_OPEN;
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
     * List server connections.
     * <br> The client connection returns only itself.
     *
     * @return array of connections.
     */
    public WsConnection[] listConnections() {
        return isClientSide ? new WsConnection[]{this}
                : connections.toArray(new WsConnection[0]);
    }

    /**
     * Replaces the handler for this connection.
     * <br>
     * Sets the connection handler. Calls onClose in the old handler
     * (conn.isOpen() returns true), then calls onOpen in the new handler.
     *
     * @param newHandler new connectin handler.
     */
    boolean isPrimaryHandler = true;
    public synchronized void setHandler(Handler newHandler) { // 
        if (isOpen()) {
            handler.onClose(this, getStatus());
            handler = newHandler;
            status.remotely = false;
            isPrimaryHandler = false;
            newHandler.onOpen(this, subProtocol);
        } else {
            throw new IllegalStateException();
        }
    }

    /**
     * Returns the connection parameters.
     *
     * @return clone of the connection parameters.
     */
    public WsParameters getParameters() {
        return wsp.deepClone();
    }

    /**
     * Returns the Socket object of this connection.
     *
     * @return Socket object.
     */
    public Socket getSocket() {
        return socket;
    }

    /**
     * Returns the name of the remote host.
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
        } catch (Exception ignore) {
        }
        return null;
    }

    /**
     * Returns the connection port.
     *
     * @return port number.
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
        if (this.requestURI != null) {
            return requestURI.getPath();
        }
        return null;
    }

    /**
     * Returns the query from the http request.
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
     * Returns the TLS connection protocol.
     *
     * @return protocol name or null for plaintext connections.
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
    private synchronized void syncSend(InputStream is, boolean isText)
            throws IOException {
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
    }

    /**
     * Closes connection.
     * <br>
     * Closes connection with status code 1000 (NORMAL_CLOSURE)<br>
     * See close notes below.
     */
    public void close() {
        close(WsStatus.NORMAL_CLOSURE, "");
    }

    /**
     * Closes connection.
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
     * @see WsParameters#setHandshakeSoTimeout
     * @see WsParameters#getHandshakeSoTimeout
     */
    Timer closeTimer;

    void cancelCloseTimer() {
        if (closeTimer != null) {
            closeTimer.cancel();
        }
    }

// TODO: public synchronized void close(int code, String reason) {    
    public void close(int code, String reason) {
        synchronized (status) {
            if (status.code == WsStatus.IS_OPEN) {
// TODO: limit close codes with range 1016-4999
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
                            closeSocket();
                        }
                    }, wsp.handshakeSoTimeout);
                } catch (Exception e) {
                    status.code = code;
                    closeSocket();
//                    e.printStackTrace();
                }
            }
        }
    }
/* TODO: 4.3.0
    public void close(String reason) {
        close(WsStatus.NORMAL_CLOSURE, reason);
    }
    public boolean isPrimaryHandler() {
      return isPrimaryHandler;
    }
    public String getString(InputStream is) throws IOException {
        return new String(getBytes(is), "UTF-8");
    }

    public byte[] getBytes(InputStream is) throws IOException {
        byte[] buf = new byte[is.available()];
        if (WsIo.readFully(is, buf, 0, buf.length) != buf.length) {
            throw new IOException();
        }
        return buf;
    }

    public WsConnection ready() {
        synchronized (status) {
            while (status.code == WsStatus.IS_INACTIVE) {
                try {
                    status.wait();
                } catch (InterruptedException ignore) {
                }
            }
        }
        return this;
    }
*/
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
            inStream = new BufferedInputStream(socket.getInputStream());
            outStream = new BufferedOutputStream(socket.getOutputStream());
            if (WsHandshake.waitHandshake(this)) {
                handler.onOpen(this, subProtocol);
                waitMessages();
            }
            if (status.error != null) {
                handler.onError(this, status.error);
            }
            handler.onClose(this, getStatus());
            if (!isClientSide) {
                closeSocket();
            }
        } catch (Throwable e) { // handler exception
//            status.code = WsStatus.ABNORMAL_CLOSURE;
            status.code = WsStatus.INTERNAL_ERROR;
            status.reason = "Handler failure";
            status.error = e;
            closeSocket();
            messageQueue.clear();
            connections.remove(this);
//            cancelCloseTimer(); // cancelled by the listener
            handler.onError(this, e);
            throw new RuntimeException("Handler failure", e);
        }
//        cancelCloseTimer(); // cancelled by the listener
        connections.remove(this);
    }

    /*    
    static void callHandler(WsConnection conn, Object arg) {
        try {
            if (arg == null || arg instanceof String) {
                conn.handler.onOpen(conn, arg == null ? null : (String) arg);
            } else if (arg instanceof WsInputStream) {
                WsInputStream is = (WsInputStream) arg;
                conn.handler.onMessage(conn, is, is.isText());
            } else if (arg instanceof Throwable) {
                conn.messageQueue.clear();
                conn.handler.onError(conn, (Throwable) arg);
            } else if (arg instanceof WsStatus) {
                conn.handler.onClose(conn, (WsStatus) arg);
            } 
        } catch (Throwable t) {
            conn.connections.remove(this);
            conn.messageQueue.clear();
            conn.closeSocket();
//            conn.cancelCloseTimer();
            conn.handler.onError(conn, t);
            throw new RuntimeException("Handler failed", t);
        }
    }
     */
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
//            if (e != null) {
//                try {
//                    handler.onError(this, status.error);
//                } catch (Throwable t) {
//                    t.printStackTrace();
//                }
//            }
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
         * Called when opening a WebSocket connection.
         *
         * @param conn WebSocket connection.
         * @param subProtocol the negotiated WebSocket sub protocol or null if
         * the client did not requests it or if the server does not agree to any
         * of the client's requested sub protocols.
         */
        public void onOpen(WsConnection conn, String subProtocol);

        /**
         * Called when a WebSocket message is received.
         * <p>
         * - the WebSocket message is represented by an input stream of binary
         * data or UTF-8 encoded text.
         * </p>
         *
         * @param conn WebSocket connection.
         * @param is message input stream.
         * @param isText if true, the stream is UTF-8 encoded text.
         * Otherwise, it is a binary data stream.
         */
        public void onMessage(WsConnection conn, InputStream is, boolean isText);

        /**
         * Called when the WebSocket connection error occured.
         * <p>
         * - any error closes the WebSocket connection;<br>
         * - allocating large buffers may throw an OutOfMemoryError.
         * </p>
         *
         * @param conn WebSocket connection.
         * @param e connection error.
         */
        public void onError(WsConnection conn, Throwable e);

        /**
         * Called when the WebSocket connection closed.
         *
         * @param conn WebSocket connection.
         * @param status clone of the connection status.
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
    } // deprecated

}
