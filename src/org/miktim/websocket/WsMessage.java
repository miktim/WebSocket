/*
 * WsInputStream. MIT (c) 2023 miktim@mail.ru
 * Converts frames of an incoming message into an InputStream.
 */
package org.miktim.websocket;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;

/**
 * WebSocket message.
 * 
 * Messages are input streams of binary data or UTF-8 encoded text.
 */
public class WsMessage extends InputStream {

    ArrayDeque<byte[]> payloads = new ArrayDeque<byte[]>(); // Queue?
    volatile long available = 0;
    boolean isText;
    WsConnection conn;
    
    byte[] payload = new byte[0]; // current payload
    int off = 0; // offset in the current payload
    boolean eof = false;
    boolean closed = false;

    WsMessage(WsConnection conn, boolean isText) {
        super();
        this.conn = conn;
        this.isText = isText;
    }
    WsMessage() { //  
       available = -1;
    }
    
    /**
     * Returns true if the message is UTF-8 encoded text.
     * @since 5.0
     */
    public boolean isText() {
        return isText;
    }

    @Override
    public int available() {
        return (int) Math.min(available, Integer.MAX_VALUE);
    }
// TODO read(buf,0,1)
    @Override
    synchronized public int read() throws IOException {
        while (!eof) {
            if (off < payload.length) {
                available--;
                return ((int) payload[off++]) & 0xFF;
            }
            getPayload();
        }
        return -1; // end of message
    }
    
    private void getPayload() throws EOFException {
        if (closed) throw new EOFException("Stream closed");
        if (!eof) payload = payloads.poll();
        else payload = new byte[0];
        off = 0;
        eof = payload.length == 0;
    }

    synchronized void write(byte[] buf) {
        if(!closed) {
            payloads.add(buf);
            available += buf.length;
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
        closed = true;
    }
    
    /**
     * Converts the contents of a message to a string.
     * 
     * Throws {@link java.lang.IllegalStateException} when message is binary data.
     * @return WebSocket message as String.
     * <p><b>Note:</b> {@link WsException} "hides" {@link java.io.IOException}</p>
     * @since 5.0
     */
    public String toString() {
        if(!isText()) throw new IllegalStateException("Is not a text");
        try {
            return WsIo.toByteOutStream(this, 2048).toString("UTF-8");
        } catch (IOException ex) {
            String msg = "toString() failed";
            conn.closeDueTo(WsStatus.ABNORMAL_CLOSURE,msg,ex);
            throw new WsException(msg, ex);
        }
    }

    /**
     * Converts the contents of a message to a byte array.
     * 
     * @return WebSocket message as byte array.
     * <p><b>Note:</b> {@link WsException} "hides" {@link java.io.IOException}</p>
     * @since 5.0
     */
    public byte[] toByteArray() {
        try {
            return WsIo.toByteOutStream(this, 2048).toByteArray();
        } catch (IOException ex) {
            String msg = "toByteArray() failed";
            conn.closeDueTo(WsStatus.ABNORMAL_CLOSURE,msg,ex);
            throw new WsException(msg, ex);
        }
    }
}
