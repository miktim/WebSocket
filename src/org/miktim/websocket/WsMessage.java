/*
 * WsMessage. MIT (c) 2023-2026 miktim@mail.ru
 * Converts data frames of an incoming message into an InputStream.
 */
package org.miktim.websocket;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;

/**
 * WebSocket message.
 *
 * WebSocket messages are input streams of binary data or UTF-8 encoded text.
 *
 * @since 5.0
 *
 */
public class WsMessage extends InputStream {

    final ArrayDeque<byte[]> payloads = new ArrayDeque<byte[]>(); // Queue?
    volatile long available = 0;
    boolean isText;

    byte[] payload = new byte[0]; // current payload
    int off = 0; // offset in the current payload
    boolean eof = false;
    boolean closed = false; // stream is closed

    WsMessage(boolean isText) {
        super();
        this.isText = isText;
    }
    WsMessage() { // end of message queue. See WsListener.run, WsConnection.waitMessage
        available = -1;
    }
    /**
     * Returns true if the message is UTF-8 encoded text, otherwise it is binary
     * data.
     */
    public boolean isText() {
        return isText;
    }
/*
    @Override
    public int available() {
        return (int) Math.min(available + payload.length - off, Integer.MAX_VALUE);
    }
*/
    @Override
    public int read() throws IOException {
        while (!eof) {
            if (off < payload.length) {
                return ((int) payload[off++]) & 0xFF;
            }
            getPayload();
        }
        checkClosed();
        return -1; // end of message
    }

    private void getPayload() throws IOException {
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
        checkClosed();
    }

    void putPayload(byte[] buf) {
        synchronized (payloads) {
            if (!closed) {
                payloads.add(buf);
                available += buf.length;
            }
            payloads.notifyAll();
        }
    }

    /**
     * Closes the WebSocket message input stream and 
     * releases any resources associated with the stream.
     * <br>
     * Once the stream is closed, attempts to read will throw an IOException.
     */
    @Override
    public void close() {
        synchronized (payloads) {
            closed = true;
            payloads.clear();
            available = 0;
            eof = true;
            payloads.add(new byte[0]);
            payloads.notifyAll();
        }
    }

    private void checkClosed() throws IOException {
        if (closed) {
            throw new IOException("WebSocket stream closed");
        }
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
     * Reads the contents of the message as a string.
     *
     * @return WebSocket message as a String.
     * @throws WsError on any exception
     */
    public String asString() {
        try {
//            if (!isText()) {
//                throw new IOException("Is not a text");
//            }
            return toByteOutStream().toString("UTF-8");
        } catch (Throwable th) {
            throw new WsError("asString error", th);
        }
    }

    /**
     * Reads the contents of the message as an array of bytes.
     *
     * @return WebSocket message as an array of bytes.
     * @throws WsError on any exception
     */
    public byte[] asByteArray() {
        try {
            return toByteOutStream().toByteArray();
        } catch (Throwable th) {
            throw new WsError("asByteArray error", th);
        }
    }
    
}
