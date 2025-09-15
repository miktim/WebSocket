/*
 * WsConnectionTest. MIT (c) 2025 miktim@mail.ru
 * Ws and wss WebSocket handshake tests.
 */

import java.io.File;
import java.io.InputStream;
import static java.lang.String.format;
import static java.lang.Thread.sleep;
import java.security.NoSuchAlgorithmException;
import org.miktim.websocket.WebSocket;
import org.miktim.websocket.WsConnection;
import org.miktim.websocket.WsParameters;
import org.miktim.websocket.WsServer;
import org.miktim.websocket.WsStatus;

//package websocket;
public class WsConnectionTest {

    static int port = 8080;
    static int securePort = 8443;

    static void delay() {
        try {
            sleep(200);
        } catch (InterruptedException ignore) {
        }
    }

    static void log(Object obj) {
        System.out.println(String.valueOf(obj));
    }

    static void logTest(String testId, String obj, boolean result) {
        log(format("[%s] %s %s", testId, obj, result ? "Ok" : "Failed!"));
    }

    static void closeAll(String testId, WebSocket ws) {
        ws.closeAll();
        delay();
        int conns = ws.listConnections().length;
        int servs = ws.listServers().length;
        if (conns + servs > 0) {
            logTest(testId, format("CloseAll (s=%d c=%d)", servs, conns), false);
        } else {
            logTest(testId, "CloseAll", true);
        }
    }

    public static void main(String[] args) throws NoSuchAlgorithmException, InterruptedException {
        log("\r\nWsConnectionTest. " + WebSocket.VERSION);
        
        WsConnection.Handler handler = new WsConnection.Handler() {
            @Override
            public void onOpen(WsConnection conn, String subProtocol) {
            }

            @Override
            public void onMessage(WsConnection conn, InputStream is, boolean isUTF8Text) {
            }

            @Override
            public void onError(WsConnection conn, Throwable e) {
            }

            @Override
            public void onClose(WsConnection conn, WsStatus status) {
            }

        };
        WebSocket webSocket = new WebSocket();
        WsParameters wsp = new WsParameters();
        WsServer server;
        WsConnection conn;
        try {

            log("\r\n0. Server not started");
            log("0.1 wss to ...");
            try{
                conn = webSocket.connect("wss://localhost:" + securePort, handler, wsp);
            } catch (Exception e) {
               System.err.println(e.toString()); 
            }
            delay();
            log("0.2 ws to ...");
            try{
                conn = webSocket.connect("ws://localhost:" + securePort, handler, wsp);
            } catch (Exception e) {
               System.err.println(e.toString()); 
            }
            delay();
            log("\r\n1. Key file not set. Start SecureServer");
            delay();
//            System.err.println("Server take key file from java keystore");
            server = webSocket.SecureServer(securePort, handler, wsp).launch();
            delay();
            logTest("1","SecureServer", webSocket.listServers().length == 1);
            if (server.isOpen()) {
                conn = webSocket.connect("wss://localhost:" + securePort, handler, wsp);
                conn.join();
                logTest("1.1","wss to SecureServer " + conn.getStatus().code, 
                        conn.getStatus().code == WsStatus.IS_OPEN);
                conn = webSocket.connect("ws://localhost:" + securePort, handler, wsp);
                conn.join();
                logTest("1.2","ws to SecureServer " + conn.getStatus().code,
                        conn.getStatus().code == WsStatus.PROTOCOL_ERROR);
            }
            closeAll("1", webSocket);

            log("\r\n2. Key file is set. Start SecureServer");
            webSocket.setKeyFile(new File("./localhost.jks"), "password");
            server = webSocket.SecureServer(securePort, handler, wsp).launch();
            delay();
            logTest("2","SecureServer", webSocket.listServers().length == 1);
            conn = webSocket.connect("wss://localhost:" + securePort, handler, wsp);
            delay();
            logTest("2.1","wss to SecureServer " + conn.getStatus().code,
                    conn.getStatus().code == WsStatus.IS_OPEN);
            conn.close();
            conn = webSocket.connect("ws://localhost:" + securePort, handler, wsp);
            delay();
            logTest("2.2","ws to SecureServer " + conn.getStatus().code,
                    conn.getStatus().code == WsStatus.PROTOCOL_ERROR);
//            log(conn.getStatus().error);
            conn.close();
            server.close();
            closeAll("2", webSocket);

            log("\r\n3. Start plaintext Server");
            server = webSocket.Server(port, handler, wsp).launch();
            delay();
            logTest("3", "Server", webSocket.listServers().length == 1);
            conn = webSocket.connect("wss://localhost:" + port, handler, wsp);
            delay();
            logTest("3.1","wss to plaintext Server " + conn.getStatus().code,
                    conn.getStatus().code == WsStatus.TLS_HANDSHAKE);
            conn.close();
            delay();
            conn = webSocket.connect("ws://localhost:" + port, handler, wsp);
            delay();
            logTest("3.2","ws to plaintext Server " + conn.getStatus().code,
                    conn.getStatus().code == WsStatus.IS_OPEN);
            conn.close();
            delay();
            closeAll("3", webSocket);

        } catch (Exception ex) {
            ex.printStackTrace();
        }
//        webSocket.closeAll();
    }
}
