/*
 * WsInputStream. MIT (c) 2023 miktim@mail.ru
 * Converts frames of an incoming message into an InputStream.
 */
package org.miktim.websocket;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;

public class WsInputStream extends InputStream {

        ArrayDeque<byte[]> frames; // Queue?
        byte[] frame = new byte[0];
        boolean isText;
        int iByte = 0;
        long available;

        WsInputStream(ArrayDeque<byte[]> frames, long messageLen, boolean isText) {
            super();
            this.frames = frames;
            this.isText = isText;
            available = messageLen;
        }

        private boolean getFrame() {
            if (frames.size() > 0) {
                frame = frames.poll();
                iByte = 0;
                return true;
            }
            return false;
        }

        public boolean isText() {
            return isText;
        }

        @Override
        public int available() {
            return (int) available;
        }

        @Override
        public int read() {//throws IOException {
            do {
                if (iByte < frame.length) {
                    available--;
                    return ((int) frame[iByte++]) & 0xFF;
                }
            } while (getFrame());
            return -1; // end of message
        }

        @Override
        public void close() throws IOException {
            frames.clear();
            frame = new byte[0];
        }
    }

