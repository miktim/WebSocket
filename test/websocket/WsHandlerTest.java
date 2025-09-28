/*
 * WsHandlerTest. MIT (c) 2025 miktim@mail.ru
 * The test throws a NullPointerException in event handlers.
 * Created 2025-09-01
 */

//package websocket;
import static java.lang.Thread.sleep;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import org.miktim.websocket.WebSocket;
import org.miktim.websocket.WsConnection;
import org.miktim.websocket.WsMessage;
import org.miktim.websocket.WsParameters;
import org.miktim.websocket.WsServer;
import org.miktim.websocket.WsStatus;

public class WsHandlerTest {

    static int testId = 0;
    static boolean testClient = true; // test client or server side connection
    static final int DELAY = 300; // timeout to close connection
    
    static String clientSideCalls = "";
    static String serverSideCalls = "";
    static String serverHandlerCalls = "";
    static WsConnection serverConn;
    static boolean testCompleted = false;
    static WebSocket webSocket;
    static WsServer server;
    interface Handler extends WsServer.Handler, WsConnection.OnRequest{};

    static void log(Object obj) {
        System.out.println(obj);
    }
    static void logErr(Object obj) {
        System.err.println(obj);
    }
    static void delay() {
        try {
            sleep(DELAY);
        } catch (InterruptedException ex) {

        }
    }

    public static class WsException extends RuntimeException {

        WsException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static void main(String[] args) throws NoSuchAlgorithmException, Exception {
        Handler handler = new Handler() {

            @Override
            public Map<String, String> onRequest(WsConnection conn, Map<String, String> head) {
                String method = "onRequest";
                String calls = String.format("[%d] %s %s",
                        testId,
                        (conn.isClientSide() ? "client-side" : "server-side"),
                        method);
                if (conn.isClientSide()) {
                    clientSideCalls = calls;
                } else {
                    serverConn = conn;
                    serverSideCalls = calls;
                }
                if (testClient == conn.isClientSide()) {
                    if (testId == 3) {
                        throw new NullPointerException(method);
                    }
                }
                return null;
            }
            
            @Override
            public void onOpen(WsConnection conn, String subProtocol) {
                String method = "onOpen";
                if (conn.isClientSide()) {
                    clientSideCalls += ":onOpen";
                } else {
                    serverConn = conn;
                    serverSideCalls += ":onOpen";
                }
                String message = conn.isClientSide() ? "Hi, Server!" : "Hi, Client!";
                    conn.send(message);
                    conn.send(message);
                    conn.send(message);
                if (testId == 1 && testClient == conn.isClientSide()) {
                    throw new NullPointerException(method);
                }
            }

            @Override
            public void onMessage(WsConnection conn, WsMessage is) {
                String method = "onMessage";
                if (conn.isClientSide()) {
                    clientSideCalls += (":" + method);
                } else {
                    serverSideCalls += (":" + method);
                }
                if (testClient == conn.isClientSide()) {
                    if (testId == 2) {
                        throw new NullPointerException(method);
                    }
                    if (testId == 3) {
//                        onError(conn, null); // emulate error
                    }
                    if (testId == 4) {
                        conn.close(1000, "Normal closure");
                    }
                }
            }
/*
            @Override
            public void onError(WsConnection conn, Throwable e) {
                String method = "onError";
                if (conn.isClientSide()) {
                    clientSideCalls += (":" + method);
                } else {
                    serverSideCalls += (":" + method);
                }
 //               logErr(e);
                if (testClient == conn.isClientSide()) {
                    if (testId == 3) {
                        throw new NullPointerException(method);
                    }
                }
            }
*/
            @Override
            public void onClose(WsConnection conn, WsStatus status) {
                String method = "onClose";
                String calls = String.format(":%s(%d)",
                        method,
                        conn.getStatus().code);
                if (conn.isClientSide()) {
                    clientSideCalls += calls;
                } else {
                    serverSideCalls += calls;
                }
                if (testId > 5) { //server handler test
                    log(String.format("[%d] %s %d",
                            testId,
                            conn.isClientSide() ? "client-side" : "server-side",
                            status.code));
                }
                if (testClient == conn.isClientSide()) {
                    if (testId == 4) {
                        throw new NullPointerException(method);
                    }
                }
            }

// Server handler            
            @Override
            public void onStart(WsServer server, WsParameters wsp) {
                serverHandlerCalls = "Server handler onStart";
                if(testId > 5) delay(); // connection opening delay
                if (testId == 6) {
                    throw new NullPointerException("onStart");
                }
            }
            @Override
            public void onStop(WsServer server, WsStatus status) {
                serverHandlerCalls += ":onStop";
                if (testId > 5) { // server handler test
                  log(String.format("[%d] %s Error: %s", testId,
                    serverHandlerCalls, status.error));
                    
                }
                if (testId == 7) {
                    throw new NullPointerException("onStop");
                }
            }
        };

        log("\r\nWsHandlerTest " + WebSocket.VERSION
                + "\r\nThe test throws a NullPointerException in event handlers.");
        webSocket = new WebSocket();
        try {
            runTest(handler);
        } catch (Exception e) {
            sleep(DELAY);
            webSocket.closeAll();
//            e.printStackTrace();
            testCompleted = true;
        }
        while (!testCompleted) {
            sleep(DELAY);
        }
        log("\r\nCompleted");
    }

    static void runTest(Handler handler) throws Exception {
        server = null;
        server = webSocket.startServer(8080, handler);
        testClient = true;
        log("\r\nTesting the client-side connection handler\r\n");
        testConnection(webSocket, handler);
        testClient = false; 
        log("\r\nTesting the server-side connection handler\r\n");
        testConnection(webSocket, handler);
        server.stopServer();
        server.join();
        log(serverHandlerCalls);
        testServer(webSocket, server, handler);
//        sleep(DELAY);
        webSocket.closeAll();
        testCompleted = true;
    } 
    
    static void testServer(WebSocket webSocket,WsServer server, Handler handler)
            throws InterruptedException {    
        log("\r\nTesting the server handler\r\n");
//test5: idle
        for (testId = 6; testId < 8; testId++) {
            WsConnection conn = null;
            try {
                server = webSocket.startServer(8080, handler);
                conn = webSocket.connect("ws://localhost:8080", handler, new WsParameters());
//                conn.ready();
                sleep(DELAY);
            } catch (Exception e) {
                logErr(e.getCause().toString());
            }
            if(testId == 7) server.stopServer(); // test7: check onStop
            server.join(DELAY);
            if (conn != null) {
                conn.join(DELAY);
            }
            String result = server.isActive() ? "Failed!" : "Ok" ;
            log(String.format("[%d] %s", testId, result));
        }
        sleep(DELAY);
    }

    static void testConnection(WebSocket webSocket, Handler handler)
            throws Exception {
        for (testId = 1; testId < 5; testId++) {
            WsConnection conn = webSocket.connect("ws://localhost:8080", handler, new WsParameters());
            sleep(DELAY);
            conn.join(DELAY);
            log(clientSideCalls + " " + conn.getStatus().code);
            serverConn.join(DELAY);
            log(serverSideCalls + " " + serverConn.getStatus().code);
            String result = "Ok";
            if (webSocket.listConnections().length 
                    + webSocket.listServers()[0].listConnections().length > 0) {
                result = "Failed!";
                if(conn.isAlive()) log(String.format("[%d] %s", testId, "client-side Failed"));
                if(serverConn.isAlive()) log(String.format("[%d] %s", testId, "server-side Failed"));
                conn.close();
                serverConn.close();
            }
            log(String.format("[%d] %s", testId, result));
        }
    }
}
