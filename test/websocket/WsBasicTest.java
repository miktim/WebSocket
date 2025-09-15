/*
 * WsBasicTest. MIT (c) 2025 miktim@mail.ru
 * Testing basic functions of WebSocket, WsServer,
 * client and server connections.
 */

import java.io.IOException;
import java.io.InputStream;
import static java.lang.String.format;
import static java.lang.Thread.sleep;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import org.miktim.websocket.WebSocket;
import org.miktim.websocket.WsConnection;
import org.miktim.websocket.WsParameters;
import org.miktim.websocket.WsServer;
import org.miktim.websocket.WsStatus;

//package websocket;
public class WsBasicTest {

    static int testId = 1;
    static int sleepTime = 100; //milliseconds
    static int port = 8080;
    static InetAddress intfAddr;
    static URI uri;
    static String uriString;
    static WebSocket webSocket;
    static WsServer server;
    static WsConnection.Handler handler;
    static boolean testOk = true;
    static boolean testCompleted = false;
    static String[] tests = {
        "Test opening WebSocket, WsServer, connections",
        "Test connection properties",
        "Test sending empty message",
        "Test sending fragmented message",
        "Test closing connections and WsServer"
    };
    static String testBuffer = "";

    static void log(Object obj) {
        System.out.println(String.valueOf(obj));
    }
    static String readString(InputStream is) throws IOException {
        byte[] buf = readBytes(is);
        return new String(buf, 0, buf.length, "UTF-8");
    }
    static byte[] readBytes(InputStream is) throws IOException {
        int increment = 123; 
        byte[] buf = new byte[increment];
        int totalBytes = 0;
        int bytesRead = 0;
        while(bytesRead != -1) {
            totalBytes += bytesRead;
            if(totalBytes == buf.length) {
                buf = Arrays.copyOf(buf, buf.length + increment);
            }
            bytesRead = is.read(buf, totalBytes, buf.length - totalBytes);
        }
        return Arrays.copyOf(buf, totalBytes);
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
        return conn.isClientSide() ? "client" : "server";
    }

    public static void main(String[] args) {
        log("\r\nWsBasicTest. " + WebSocket.VERSION);
        for (int i = 0; i < tests.length; i++) {
            log(format("%d %s", i, tests[i]));
        }
        log("");
        handler = new WsConnection.Handler() {
            @Override
            public void onOpen(WsConnection conn, String subProtocol) {
                log(format("[0] %s Ok", side(conn)));
                if (testId == 1) {
                    testOk &= port == conn.getPort();
                    testOk &= intfAddr.equals(server.getBindAddress());
                    testOk &= conn.getPath().equals(uri.getPath());
                    testOk &= conn.getQuery().equals(uri.getQuery());
                    if (!testOk) {
                        logTestOk(conn, testOk);
                        conn.close();
                    }
                }
                logTestOk(conn, testOk);
            }

            @Override
            public void onMessage(WsConnection conn, InputStream is, boolean isText) {
                try {
                    if(!isText) throw new IOException("Not is text");
                    switch (testId) {
                        case 2: // empty message
                            String s = readString(is);
                            if (!s.equals("")) {
                                break;
                            }
                            logTestOk(conn, testOk);
                            return;
                        case 3: // fragmented message
                            String ts = readString(is);
                            if (!ts.equals(testBuffer)) {
                                break;
                            }
                            logTestOk(conn, testOk);
                            return;
                        default:
                            conn.close();
                            return;
                    } 
                    testOk = false;
                    logTestOk(conn, testOk);
                    conn.close();
                } catch (Exception ex) {
                    testOk = false;
                    onError(conn, ex);
                } 
                conn.close();
            }

            @Override
            public void onError(WsConnection conn, Throwable e) {
                logSide(conn, e.toString());
            }

            @Override
            public void onClose(WsConnection conn, WsStatus status) {
                testId = tests.length - 1;
                logTestOk(conn, true);
                logSide(conn, conn.getStatus().toString());
                testCompleted = true;
            }
        };

        try {
            intfAddr = InetAddress.getByName("127.0.0.1");
            webSocket = new WebSocket(intfAddr);
            WsParameters wsp = (new WsParameters()).setPayloadBufferLength(123);
            webSocket.Server(port, handler, new WsParameters()).launch();
            sleep(sleepTime);
            server = webSocket.listServers()[0];

            while (testBuffer.length() < wsp.getPayloadBufferLength() * 3) {
                testBuffer += "asfh域名alfqwoлвыыдйзццущ019801[r jsdfjs annsla;d";
            }
            uriString = format(
                    "ws://user:password@localhost:%d/путь?параметр=значение#paragraph",
                    server.getPort());
            log("uri: " + uriString);
            uri = new URI(uriString);

            runTest();

            while (!testCompleted) {
                sleep(sleepTime);
            }
        } catch (Exception e) {
            logTest(testId, "?", e.toString());
            e.printStackTrace();
        }
        log("\n\rCompleted.");
        webSocket.closeAll();

    }

    static void runTest()
            throws URISyntaxException, IOException, GeneralSecurityException, InterruptedException {
        testId = 1;
        webSocket.connect(uriString, handler, server.getParameters());
        sleep(sleepTime);
        WsConnection serverConn = server.listConnections()[0];
        WsConnection conn = webSocket.listConnections()[0];
        log("[0,1] WebSocket and WsServer Ok");

        testId = 2;
        conn.send("");
        serverConn.send("");
        sleep(sleepTime);
        testId = 3;
        conn.send(testBuffer);
        serverConn.send(testBuffer);
        sleep(sleepTime);
        testId = 4;
        conn.close();
        serverConn.join(sleepTime);
        webSocket.closeAll();
        sleep(sleepTime);
//        int cnt = webSocket.listServers().length;
//                + webSocket.listConnections().length
//                + server.listConnections().length;
//        log(cnt);
        if ((webSocket.listServers().length
                + webSocket.listConnections().length
                + server.listConnections().length) > 0) {
            logTest(4, "WebSocket or WsServer", "Failed!");
        } else {
            log("[4] WebSocket and WsServer Ok");
        }
    }
}
