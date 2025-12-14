/*
 * WsMessage. MIT (c) 2023-2025 miktim@mail.ru
 * Converts frames of an incoming message into an InputStream.
 */
package org.miktim.websocket;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;

/**
 * WebSocket message.
 *
 * Messages are input streams of binary data or UTF-8 encoded text.
 */
public class WsMessage extends InputStream {

    final ArrayDeque<byte[]> payloads = new ArrayDeque<byte[]>(); // Queue?
    volatile long available = 0;
    boolean isText;
    WsConnection conn;

    byte[] payload = new byte[0]; // current payload
    int off = 0; // offset in the current payload
    boolean eof = false;

    WsMessage(WsConnection conn, boolean isText) {
        super();
        this.conn = conn;
        this.isText = isText;
    }

    WsMessage() { //  
        available = -1;
    }

    /**
     * Returns true if the message is UTF-8 encoded text,
     * otherwise it is binary data.
     *
     * @since 5.0
     */
    public boolean isText() {
        return isText;
    }

    @Override
    public int available() {
        return (int) Math.min(available + payload.length - off, Integer.MAX_VALUE);
    }

    @Override
    public int read() throws IOException {
        while (!eof) {
            if (off < payload.length) {
                return ((int) payload[off++]) & 0xFF;
            }
            getPayload();
        }
        return -1; // end of message
    }

    private void getPayload() {
        synchronized (payloads) {
            if (!eof) {
                while ((payload = payloads.poll()) == null) {
                    try {
                        payloads.wait();
                    } catch (InterruptedException ignored) {
                    }
                }
                available -= payload.length;
            } else {
                payload = new byte[0];
            }
        }
        off = 0;
        eof = payload.length == 0;
    }

    void putPayload(byte[] buf) {
        synchronized (payloads) {
            payloads.add(buf);
            available += buf.length;
            payloads.notify();
        }
    }

    /**
     * Closes the input stream.
     *
     * Further reading causes an IOException.
     */
    @Override
    synchronized public void close() {
        payloads.clear();
        available = 0;
        payload = new byte[0];
        off = 0;
        eof = true;
    }

    ByteArrayOutputStream toByteOutStream() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        while (!eof) {
            getPayload();
            bos.write(payload);
        }
        return bos;
    }

    /**
     * Converts the contents of the message to a string.
     *
     * Throws {@link java.lang.IllegalStateException} when the message is binary
     * data.
     *
     * @return WebSocket message as String.
     * <p>
     * <b>Note:</b> {@link WsError} "hides" {@link java.io.IOException}</p>
     * @since 5.0
     */
    @Override
    public String toString() {
        if (!isText()) {
            throw new IllegalStateException("Is not a text");
        }
        try {
            return toByteOutStream().toString("UTF-8");
        } catch (IOException ex) {
            String msg = "toString() failed";
            conn.closeDueTo(WsStatus.ABNORMAL_CLOSURE, msg, ex);
            throw new WsError(msg, ex);
        }
    }

    /**
     * Converts the contents of the message to a byte array.
     *
     * @return WebSocket message as an array of bytes.
     * <p>
     * <b>Note:</b> {@link WsError} "hides" {@link java.io.IOException}</p>
     * @since 5.0
     */
    public byte[] toByteArray() {
        try {
            return toByteOutStream().toByteArray();
        } catch (IOException ex) {
            String msg = "toByteArray() failed";
            conn.closeDueTo(WsStatus.ABNORMAL_CLOSURE, msg, ex);
            throw new WsError(msg, ex);
        }
    }
}
