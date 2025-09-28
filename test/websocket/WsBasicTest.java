/*
 * WsBasicTest. MIT (c) 2025 miktim@mail.ru
 * Testing basic functions of WebSocket, WsServer,
 * client and server connections.
 */

import java.io.IOException;
import static java.lang.String.format;
import static java.lang.Thread.sleep;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import org.miktim.websocket.WebSocket;
import org.miktim.websocket.WsConnection;
import org.miktim.websocket.WsMessage;
import org.miktim.websocket.WsParameters;
import org.miktim.websocket.WsServer;
import org.miktim.websocket.WsStatus;

//package websocket;
public class WsBasicTest {

    static int testId = 1;
    static final int DELAY = 100; //milliseconds
    static final int PORT = 8080;
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

    /*    
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
     */
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
        for (int i = 0; i < tests.length; i++) {
            log(format("%d %s", i, tests[i]));
        }
        log("");
        handler = new WsConnection.Handler() {
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
            public void onMessage(WsConnection conn, WsMessage is) {
                try {
                    if (!is.isText()) {
                        throw new IOException("Not is text");
                    }
                    switch (testId) {
                        case 2: // empty message
                            String s = is.toString();
                            logTestOk(conn, s.equals(""));
                            return;
                        case 3: // fragmented message
                            String ts = is.toString();
                            logTestOk(conn, ts.equals(testBuffer));
                            return;
                        default:
//                            conn.close();
                            return;
                    }
                } catch (IOException ex) {
                    testOk = false;
//                    onError(conn, ex);
                }
                conn.close();
            }
/*
            @Override
            public void onError(WsConnection conn, Throwable e) {
                e.printStackTrace();
                logSide(conn, e.toString());
            }
*/
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
            webSocket.setKeyFile("./testkeys", "passphrase");

            WsParameters wsp = (new WsParameters()).setPayloadBufferLength(123);
            server = webSocket.startSecureServer(PORT, handler, wsp);
            server = server.ready();
            server = webSocket.listServers()[0];

            while (testBuffer.length() < wsp.getPayloadBufferLength() * 10) {
                testBuffer += "asfh域名alfqwoлвыыдйзццущ019801[r jsdfjs annsla;d";
            }
            uriString = format(
                    "wss://user:password@localhost:%d/путь?параметр=значение#paragraph",
                    server.getPort());
            log("uri: " + uriString);
            uri = new URI(uriString);

            runTest();

            while (!testCompleted) {
                sleep(DELAY);
            }
        } catch (Exception e) {
            logTest(testId, "?", e.toString());
            e.printStackTrace();
        }
        log("\n\rCompleted.");
        webSocket.closeAll();

    }

    //   static WsConnection conn;
    static void runTest()
            throws URISyntaxException, IOException, GeneralSecurityException, InterruptedException {
        testId = 1;
        testOk = true;
        WsConnection conn = webSocket.connect(uriString, handler, server.getParameters());
        conn = conn.ready();
        conn = webSocket.listConnections()[0];
        if(!conn.isOpen()) {
            return;
        }
        WsConnection serverConn = server.listConnections()[0];
        conn = serverConn.ready();

        log("[0,1] WebSocket and WsServer Ok");
        sleep(DELAY);
        testId = 2;
        testOk = true;
        conn.send("");
        serverConn.send("");
        sleep(DELAY);
        testId = 3;
        testOk = true;
        conn.send(testBuffer);
        serverConn.send(testBuffer);
        sleep(DELAY);
        testId = 4;
        testOk = true;
        webSocket.closeAll();
        serverConn.join(DELAY);
        conn.join(DELAY);
        server.join(DELAY);
//        sleep(DELAY);
        int cnt =
                webSocket.listServers().length
                + webSocket.listConnections().length
                + server.listConnections().length;
//        log(cnt);
        if (cnt > 0) {
            logTest(4, "WebSocket or WsServer", "Failed!");
        } else {
            log("[4] WebSocket and WsServer Ok");
        }
    }
}
