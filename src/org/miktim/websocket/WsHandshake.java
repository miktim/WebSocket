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
import java.util.Map;

class WsHandshake {

    static final String SERVER_AGENT = "WsLite/" + WebSocket.VERSION;

    static boolean waitHandshake(WsConnection conn) {
        synchronized (conn) {
            try {
                if (conn.isClientSide()) {
                    handshakeServer(conn);
                } else {
                    handshakeClient(conn);
                }
                conn.socket.setSoTimeout(conn.wsp.connectionSoTimeout);
                conn.status.code = WsStatus.IS_OPEN;
                conn.status.remotely = !conn.isClientSide();
                conn.notifyAll();
                return true;
            } catch (Exception e) {
//System.err.println(e.toString());
                conn.status.code = WsStatus.PROTOCOL_ERROR;
                conn.status.reason = "Handshake error";
                conn.status.remotely = conn.isClientSide();
                conn.status.error = e;
                conn.closeSocket();
                conn.notifyAll();
                WsConnection.callHandler(conn, conn.getStatus()); // onClose
                return false;
            }
        }
    }

    static void handshakeClient(WsConnection conn)
            throws IOException, URISyntaxException, NoSuchAlgorithmException {
        conn.requestHead = (new HttpHead()).read(conn.inStream);

        String[] parts = conn.requestHead.getStartLine().split(" ");
        conn.requestURI = new URI(parts[1]);
        String key = conn.requestHead.get("Sec-WebSocket-Key");

        conn.responseHead = (new HttpHead()).set("Server", SERVER_AGENT);
        if (parts[0].equals("GET")
                && key != null
                && conn.requestHead.get("Upgrade").toLowerCase().equals("websocket")
                && conn.requestHead.get("Sec-WebSocket-Version").equals("13")
                && setSubprotocol(conn,
                        conn.requestHead.getValues("Sec-WebSocket-Protocol"),
                        conn.responseHead)) {
            conn.responseHead
                    .setStartLine("HTTP/1.1 101 Upgrade")
                    .set("Upgrade", "websocket")
                    .set("Connection", "Upgrade,keep-alive")
                    .set("Sec-WebSocket-Accept", sha1Hash(key));
            if (conn.handler instanceof WsConnection.OnRequest) {
                setCustomHeaders(conn,
                        ((WsConnection.OnRequest) conn.handler)
                                .onRequest(conn, conn.getRequestHead()));
            }
            conn.responseHead.write(conn.outStream);
        } else {
            conn.responseHead
                    .setStartLine("HTTP/1.1 400 Bad Request")
                    .set("Connection", "close")
                    .write(conn.outStream);
            //        conn.status.remotely = false;
            throw new ProtocolException("WebSocket handshake error");
        }
    }

    static void setCustomHeaders(WsConnection conn, Map<String, String> hdrs) {
        if(hdrs == null) return;
        HttpHead head = conn.isClientSide()
                ? conn.requestHead : conn.responseHead;
        for (String hdr : hdrs.keySet().toArray(new String[0])) {
            if (!(conn.requestHead.containsHeader(hdr)
                    || conn.responseHead.containsHeader(hdr))) {
                head.set(hdr, hdrs.get(hdr));
            }
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

    static void handshakeServer(WsConnection conn)
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
        conn.requestHead = (new HttpHead())
                .setStartLine("GET " + requestPath + " HTTP/1.1")
                .set("Host", host)
                .set("Origin", conn.requestURI.getScheme() + "://" + host)
                .set("Upgrade", "websocket")
                .set("Connection", "Upgrade,keep-alive")
                .set("Sec-WebSocket-Key", key)
                .set("Sec-WebSocket-Version", "13")
                .set("User-Agent", SERVER_AGENT);
        if (conn.wsp.subProtocols != null) {
            conn.requestHead.setValues("Sec-WebSocket-Protocol", conn.wsp.subProtocols);
        }
        conn.requestHead.write(conn.outStream);

        conn.responseHead = (new HttpHead()).read(conn.inStream);
        conn.subProtocol = conn.responseHead.get("Sec-WebSocket-Protocol");
        if (!(conn.responseHead.getStartLine().split(" ")[1].equals("101")
                && conn.responseHead.get("Upgrade").toLowerCase().equals("websocket")
                && conn.responseHead.get("Sec-WebSocket-Extensions") == null
                && conn.responseHead.get("Sec-WebSocket-Accept").equals(sha1Hash(key))
                && checkSubprotocol(conn))) {
            //          conn.status.remotely = false;
            throw new ProtocolException("WebSocket handshake error");
        }
        if (conn.handler instanceof WsConnection.OnRequest) {
            setCustomHeaders(conn,
                    ((WsConnection.OnRequest) conn.handler)
                            .onRequest(conn, conn.getRequestHead()));
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
