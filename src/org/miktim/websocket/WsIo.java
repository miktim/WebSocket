/*
 * WsIo. WebSocket common i/o routines, MIT (c) 2020-2025 miktim@mail.ru
 *
 * Commonly used static i/o methods moved from WsConnection.
 * Created: 2025-07-18
 */
package org.miktim.websocket;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.util.Arrays;

class WsIo {

    // generate "random" mask/key
    static byte[] randomBytes(int len) {
        byte[] b = new byte[len];
//        long l = Double.doubleToRawLongBits(Math.random());
        long l = System.currentTimeMillis();
        while (--len >= 0) {
            b[len] = (byte) l;
            l >>= 1;
        }
        return b;
    }

    static int readFully(InputStream is, byte[] buf, int off, int len)
            throws IOException {
        int bytesCnt = 0;
        for (int n = is.read(buf, off, len); n >= 0 && bytesCnt < len;) {
            bytesCnt += n;
            n = is.read(buf, off + bytesCnt, len - bytesCnt);
        }
        return bytesCnt;
    }

    static void sendControlFrame(WsConnection conn, int opFrame, byte[] payload, int payloadLen)
            throws IOException {
        sendFrame(conn, opFrame, Arrays.copyOf(payload, payloadLen), payloadLen);
    }

    static void sendFrame(WsConnection conn, int opFrame, byte[] payload, int payloadLen)
            throws IOException {
        synchronized (conn.outStream) {
            if (conn.status.code != WsStatus.IS_OPEN) { // todo: ?shutdown outputStream on close()
                throw new SocketException("WebSocket is closed");
            }
// client MUST mask payload, server MUST NOT        
            boolean masked = conn.isClientSide();
            byte[] header = new byte[14]; //hopefully initialized with zeros

            header[0] = (byte) opFrame;
            int headerLen = 2;

            int tempLen = payloadLen;
            if (tempLen < 126) {
                header[1] = (byte) (tempLen);
            } else if (tempLen < 0x10000) {
                header[1] = (byte) 126;
                header[2] = (byte) (tempLen >>> 8);
                header[3] = (byte) tempLen;
                headerLen += 2;
            } else {
                header[1] = (byte) 127;
                headerLen += 4; // skip 4 zero bytes of 64bit payload length
                for (int i = 3; i > 0; i--) {
                    header[headerLen + i] = (byte) (tempLen & 0xFF);
                    tempLen >>>= 8;
                }
                headerLen += 4;
            }

            if (masked) {
                header[1] |= WsListener.MASKED_DATA;
                byte[] mask = randomBytes(4);
                System.arraycopy(mask, 0, header, headerLen, 4);
                headerLen += 4;
                umaskPayload(mask, payload, 0, payloadLen);
            }
            conn.outStream.write(header, 0, headerLen);
            conn.outStream.write(payload, 0, payloadLen);
            conn.outStream.flush();
        }
    }

// unmask/mask payload
    static void umaskPayload(byte[] mask, byte[] payload, int off, int len) {
        for (int i = 0; i < len; i++) {
            payload[off++] ^= mask[i & 3];
        }
    }

    static ByteArrayOutputStream toByteOutStream(InputStream is, int buflen) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buffer = new byte[buflen];
        int length;
            while ((length = is.read(buffer)) != -1) {
                bos.write(buffer, 0, length);
            }
        return bos;
    }
}
