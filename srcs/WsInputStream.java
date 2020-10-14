/*
 * WebSocket message stream. The MIT License,(c) 2020 miktim@mail.ru
 */
package org.samples.java.websocket;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.channels.InterruptedByTimeoutException;

public class WsInputStream extends InputStream {

    private StreamThread streamThread = null;
    private int timeout = 0;
    private boolean isClosed = false;
    private boolean EOF = false;
    private String closeMessage = "Stream Closed";

    public WsInputStream(int timeout) {
//        super();
        this.timeout = timeout;
    }

    private void checkThread() {
        if (streamThread == null) {
            (streamThread = new StreamThread()).start();
        }
    }

    @Override
    public int read() throws IOException {
        if (isClosed) {
            throw new IOException(closeMessage);
        }
        checkThread();
        try {
            return streamThread.read();
        } catch (IOException e) {
            close();
            throw e;
        }
    }

    @Override
    public void close() {
        isClosed = true;
        streamThread.interrupt();
    }

    public int write(byte[] buf) throws IOException {
        return write(buf, 0, buf.length);
    }

    public int write(byte[] buf, int off, int len) throws IOException {
        if (EOF) {
            throw new IOException("Post-EOF Writing");
        }
        if (isClosed) {
            throw new IOException(closeMessage);
        }
        if (off < 0 || len < 0 || off + len > buf.length) {
            throw new IndexOutOfBoundsException();
        }
        checkThread();
        return streamThread.write(buf, off, len);
    }

    public void writeEOF() throws IOException {
        EOF = true;
        streamThread.write(new byte[0], 0, 0);
    }

    private class StreamThread extends Thread {

        byte[] buf = new byte[0];
        int pos = 0;
        int count = 0;

        @Override
        public void run() {
            synchronized (this) {
                try {
                    long time;
                    do {
                        time = System.currentTimeMillis();
                        wait(timeout);
                    } while (System.currentTimeMillis() - time < timeout);//!isClosed && activity);
                    closeMessage += " (Timeout)";
                    isClosed = true;
                    interrupt();
                } catch (InterruptedException e) {
                }
            }
        }

        synchronized int read() throws IOException {
            while (pos >= count) {
                if (EOF) {
                    return -1;  //EOF
                }
                try {
                    notify();
                    wait();
                } catch (InterruptedException e) {
                    throw new InterruptedIOException();
                }
            }
            return buf[pos++] & 0xFF;
        }

        synchronized int write(byte[] buf, int off, int len) throws IOException {
            while (pos < count) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    return 0;
                }
            }
            this.buf = buf;
            this.pos = off;
            this.count = len;
            notify();
            return len;
        }

    }

}
