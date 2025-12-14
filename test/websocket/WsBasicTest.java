/*
 * WsBasicTest. MIT (c) 2025 miktim@mail.ru
 * Testing basic functions of WebSocket, WsServer,
 * client and server connections.
 */

import java.io.File;
import java.io.IOException;
import static java.lang.String.format;
import static java.lang.Thread.sleep;
import java.net.InetAddress;
import java.net.URI;
import org.miktim.websocket.WebSocket;
import org.miktim.websocket.WsConnection;
import org.miktim.websocket.WsError;
import org.miktim.websocket.WsMessage;
import org.miktim.websocket.WsParameters;
import org.miktim.websocket.WsServer;
import org.miktim.websocket.WsStatus;

//package websocket;
public class WsBasicTest {

    static int testId = 1;
    static final int DELAY = 500; //milliseconds
    static final int PORT = 8080;
    static InetAddress intfAddr;
    static URI uri;
    static String uriString;
    static WebSocket webSocket;
    static WsServer server;
    static WsParameters wsp;
    static WsConnection.Handler handler;
    static boolean testOk = true;
    static boolean testCompleted = false;
    static String testBuffer = "";

    static void log(Object obj) {
        System.out.println(String.valueOf(obj));
    }

    static void logTest(int testId, String obj, String msg) {
        log(format("[%d] %s %s", testId, obj, msg));
    }

    static void logSide(WsConnection conn, String msg) {
        logTest(testId, side(conn), msg);
    }

    static void logTestOk(WsConnection conn, boolean testOk) {
        logSide(conn, testOk ? "Ok" : "Failed!");
    }

    static String side(WsConnection conn) {
        return conn.isClientSide() ? "client-side" : "server-side";
    }

    public static void main(String[] args) {
        log("\r\nWsBasicTest. " + WebSocket.VERSION);
        log("");
        handler = new WsServer.Handler() {
            @Override
            public void onOpen(WsConnection conn, String subProtocol) {
                log(format("[0] %s Ok", side(conn)));
                testOk &= PORT == conn.getPort();
                testOk &= conn.getPath().equals(uri.getPath());
                testOk &= conn.getQuery().equals(uri.getQuery());
                testOk &= intfAddr.equals(conn.getSocket().getLocalAddress());
                testOk &= intfAddr.equals(server.getServerSocket().getInetAddress());
                logTestOk(conn, testOk);
            }

            @Override
            public void onMessage(WsConnection conn, WsMessage msg) {
                try {
                    if (!msg.isText()) {
                        throw new IOException("Not is text!");
                    }
                    switch (testId) {
                        case 2: // empty message
                            String s = msg.toString();
                            logTestOk(conn, s.equals(""));
                            return;
                        case 3: // fragmented message
                            String ts = msg.toString();
                            logTestOk(conn, ts.equals(testBuffer));
                            return;
                        default:
                            return;
                    }
                } catch (IOException ex) {
                    testOk = false;
                }
                conn.close();
            }

            @Override
            public void onError(WsConnection conn, Throwable e) {
                e.printStackTrace();
                logSide(conn, e.toString());
            }

            @Override
            public void onClose(WsConnection conn, WsStatus status) {
//                testId = tests.length - 1;
                logTestOk(conn, true);
                logSide(conn, conn.getStatus().toString());
                testCompleted = true;
            }

            @Override
            public void onStart(WsServer server, WsParameters wsp) {
                log("[0] server Ok");
            }

            @Override
            public void onStop(WsServer server, Throwable error) {
                log(format("[%d] server %s", testId,
                        (error == null ? "Ok" : "Failed!")));
            }
        };

        try {
            intfAddr = InetAddress.getByName("127.0.0.1");
            webSocket = new WebSocket(intfAddr);
            webSocket.setKeyFile(new File("./testkeys"), "passphrase");

            wsp = (new WsParameters()).setPayloadBufferLength(123);

            while (testBuffer.length() < wsp.getPayloadBufferLength() * 10) {
                testBuffer += "asfh域名alfqwoлвыыдйзццущ019801[r jsdfjs annsla;d";
            }
            uriString = format(
                    "wss://user:password@localhost:%d/путь?параметр=значение#paragraph",
                    PORT);
            log("uri: " + uriString);
            uri = new URI(uriString);

            runTest();

        } catch (Throwable e) {
            logTest(testId, "?", e.toString());
            e.printStackTrace();
        }
        log("\n\rCompleted.");

    }

    static void runTest() throws WsError, InterruptedException {
//            throws URISyntaxException, IOException, GeneralSecurityException, InterruptedException {
        testId = 1;
        log("\r\n0. Test opening WebSocket, WsServer, connect."
                + "\r\n1. Test connection properties");
        server = webSocket.startSecureServer(PORT, handler, wsp);
        server = server.ready();
        server = webSocket.listServers()[0];
        testOk = true;
        WsConnection conn = webSocket.connect(uriString, handler, server.getParameters());
        conn = webSocket.listConnections()[0].ready();
        WsConnection serverConn = server.listConnections()[0];
        serverConn = serverConn.ready();
        log("[0,1] WebSocket and WsServer Ok");
        sleep(DELAY);

        testId = 2;
        log("\r\n2. Test sending empty message");
        testOk = true;
        conn.send(""); // send to server
        serverConn.send(""); // send to client
        sleep(DELAY);
        testId = 3;
        log("\r\n3. Test sending fragmented message");
        testOk = true;
        conn.send(testBuffer);
        serverConn.send(testBuffer);
        sleep(DELAY);
        testId = 4;
        log("\r\n4. Test closing connections and WsServer");
        testOk = true;
        webSocket.closeAll();
        conn.join();
        serverConn.join();
        server.join();
        int cnt =  webSocket.listServers().length + 
                webSocket.listConnections().length + 
                server.listConnections().length;
//        log(cnt);
        logTest(4, "WebSocket or WsServer", cnt > 0 ? "Failed!" : "Ok");
    }
}
