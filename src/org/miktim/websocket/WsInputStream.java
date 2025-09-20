/*
 * WsInputStream. MIT (c) 2023 miktim@mail.ru
 * Converts frames of an incoming message into an InputStream.
 */
package org.miktim.websocket;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;

class WsInputStream extends InputStream {

    ArrayDeque<byte[]> payloads = new ArrayDeque<byte[]>(); // Queue?
    volatile long available = 0;
    boolean isText;
    
    byte[] payload = new byte[0]; // current payload
    int off = 0; // offset in the current payload
    boolean eof = false;
    boolean closed = false;

    WsInputStream(boolean isText) {
        super();
        this.isText = isText;
    }
    WsInputStream() { //  
       available = -1;
    }
    
    public boolean isText() {
        return isText;
    }

    @Override
    public int available() {
        return (int) Math.min(available, Integer.MAX_VALUE);
    }

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
    
    @Override
    synchronized public void close() {// throws IOException {
        payloads.clear();
        available = 0;
        payload = new byte[0];
        off = 0;
        closed = true;
    }
/*
    public String getString() throws IOException  {
        if (!isText()) {
            throw new IOException("Illegal state exception");
        }
        return new String(getByteArray(), "UTF-8");
    }

    public byte[] getByteArray() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096]; 
        int length;
        while ((length = this.read(buffer)) != -1) {
            bos.write(buffer, 0, length); 
        }
        return bos.toByteArray();
    }
*/
}
