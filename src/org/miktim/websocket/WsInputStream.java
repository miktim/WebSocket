/*
 * WsInputStream. MIT (c) 2023 miktim@mail.ru
 * Converts frames of an incoming message into an InputStream.
 */
package org.miktim.websocket;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;

class WsInputStream extends InputStream {

        ArrayDeque<byte[]> payloads; // Queue?
        byte[] payload = new byte[0];
        boolean isText;
        int iByte = 0;
        long available;

        WsInputStream(ArrayDeque<byte[]> payloads, long messageLen, boolean isText) {
            super();
            this.payloads = payloads;
            this.isText = isText;
            available = messageLen;
        }

        private boolean getFrame() {
            if (payloads.size() > 0) {
                payload = payloads.poll();
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
                if (iByte < payload.length) {
                    available--;
                    return ((int) payload[iByte++]) & 0xFF;
                }
            } while (getFrame());
            return -1; // end of message
        }

        @Override
        public void close() throws IOException {
            payloads.clear();
            payload = new byte[0];
        }
    }

