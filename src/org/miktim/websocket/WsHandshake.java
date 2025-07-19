/*
 * WsHandshake. WebSocket client/server handshake, MIT (c) 2020-2025 miktim@mail.ru
 *
 * Static handshaking methods moved from WsConnection.
 * Created: 2025-07-18
 */
package org.miktim.websocket;

import java.io.IOException;
import java.net.ProtocolException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

class WsHandshake {
    static final String SERVER_AGENT = "WsLite/" + WebSocket.VERSION;

    static boolean waitHandshake(WsConnection conn) {
        synchronized (conn.status) {
            try {
                if (conn.isClientSide()) {
                    handshakeServer(conn);
                } else {
                    handshakeClient(conn);
                }
                conn.socket.setSoTimeout(conn.wsp.connectionSoTimeout);
                conn.status.code = WsStatus.IS_OPEN;
                return true;
            } catch (Exception e) {
                conn.status.reason = "Handshake failed";
                conn.status.error = e;
                conn.status.code = WsStatus.PROTOCOL_ERROR;
                return false;
            }
        }
    }

    static void handshakeClient(WsConnection conn)
            throws IOException, URISyntaxException, NoSuchAlgorithmException {
        HttpHead requestHead = (new HttpHead()).read(conn.inStream);
        String[] parts = requestHead.get(HttpHead.START_LINE).split(" ");
        conn.requestURI = new URI(parts[1]);
        String key = requestHead.get("Sec-WebSocket-Key");

        HttpHead responseHead = (new HttpHead()).set("Server", SERVER_AGENT);
        if (parts[0].equals("GET")
                && key != null
                && requestHead.get("Upgrade").toLowerCase().equals("websocket")
                && requestHead.get("Sec-WebSocket-Version").equals("13")
                && setSubprotocol(conn,
                        requestHead.getValues("Sec-WebSocket-Protocol"),
                        responseHead)) {
            responseHead
                    .set(HttpHead.START_LINE, "HTTP/1.1 101 Upgrade")
                    .set("Upgrade", "websocket")
                    .set("Connection", "Upgrade,keep-alive")
                    .set("Sec-WebSocket-Accept", sha1Hash(key))
                    .write(conn.outStream);
        } else {
            responseHead
                    .set(HttpHead.START_LINE, "HTTP/1.1 400 Bad Request")
                    .set("Connection", "close")
                    .write(conn.outStream);
            conn.status.remotely = false;
            throw new ProtocolException("WebSocket handshake failed");
        }
    }

    private static boolean setSubprotocol(WsConnection conn, String[] requestedSubps, HttpHead rs) {
        if (requestedSubps == null) {
            return true;
        }
        if (conn.wsp.subProtocols != null) {
            for (String agreedSubp : requestedSubps) {
                for (String subp : conn.wsp.subProtocols) {
                    if (agreedSubp.equals(subp)) {
                        conn.subProtocol = agreedSubp;
                        rs.set("Sec-WebSocket-Protocol", agreedSubp); // response headers
                        return true;
                    }
                }
            }
        }
        return true;
    }

    private static void handshakeServer(WsConnection conn)
            throws IOException, URISyntaxException, NoSuchAlgorithmException {
        String key = base64Encode(WsIo.randomBytes(16));

        String requestPath = (conn.requestURI.getPath() == null ? "/" : conn.requestURI.getPath())
                + (conn.requestURI.getQuery() == null ? "" : "?" + conn.requestURI.getQuery());
        requestPath = (new URI(requestPath)).toASCIIString();
        if (!requestPath.startsWith("/")) {
            requestPath = "/" + requestPath;
        }
        String host = conn.requestURI.getHost()
                + (conn.requestURI.getPort() > 0 ? ":" + conn.requestURI.getPort() : "");
//        host = (new URI(host)).toASCIIString(); // URISyntaxException on IP addr
        HttpHead requestHead = (new HttpHead())
                .set(HttpHead.START_LINE, "GET " + requestPath + " HTTP/1.1")
                .set("Host", host)
                .set("Origin", conn.requestURI.getScheme() + "://" + host)
                .set("Upgrade", "websocket")
                .set("Connection", "Upgrade,keep-alive")
                .set("Sec-WebSocket-Key", key)
                .set("Sec-WebSocket-Version", "13")
                .set("User-Agent", SERVER_AGENT);
        if (conn.wsp.subProtocols != null) {
            requestHead.setValues("Sec-WebSocket-Protocol", conn.wsp.subProtocols);
        }
        requestHead.write(conn.outStream);

        HttpHead responseHead = (new HttpHead()).read(conn.inStream);
        conn.subProtocol = responseHead.get("Sec-WebSocket-Protocol");
        if (!(responseHead.get(HttpHead.START_LINE).split(" ")[1].equals("101")
                && responseHead.get("Upgrade").toLowerCase().equals("websocket")
                && responseHead.get("Sec-WebSocket-Extensions") == null
                && responseHead.get("Sec-WebSocket-Accept").equals(sha1Hash(key))
                && checkSubprotocol(conn))) {
            conn.status.remotely = false;
            throw new ProtocolException("WebSocket handshake failed");
        }
    }

    private static boolean checkSubprotocol(WsConnection conn) {
        if (conn.subProtocol == null) {
            return true; // 
        }
        if (conn.wsp.subProtocols != null) {
            for (String subp : conn.wsp.subProtocols) {
                if (conn.subProtocol.equals(subp)) { //
                    return true;
                }
            }
        }
        return false;
    }

    private static String sha1Hash(String key) throws NoSuchAlgorithmException {
        return base64Encode(MessageDigest.getInstance("SHA-1").digest(
                (key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes()));
    }

    private static final byte[] B64_BYTES = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".getBytes();

    static String base64Encode(byte[] b) {
        byte[] s = new byte[((b.length + 2) / 3 * 4)];
        int bi = 0;
        int si = 0;
        while (bi < b.length) {
            int k = Math.min(3, b.length - bi);
            int bits = 0;
            for (int j = 0, shift = 16; j < k; j++, shift -= 8) {
                bits += ((b[bi++] & 0xFF) << shift);
            }
            for (int j = 0, shift = 18; j <= k; j++, shift -= 6) {
                s[si++] = B64_BYTES[(bits >> shift) & 0x3F];
            }
        }
        while (si < s.length) {
            s[si++] = (byte) '=';
        }
        return new String(s);
    }
    
}
