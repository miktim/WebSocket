/*
 * WsHandlerTest. MIT (c) 2025 miktim@mail.ru
 * Test throws NullPointerException in event handler.
 * Created 2025-09-01
 */

//package websocket;
import java.io.IOException;
import java.io.InputStream;
import static java.lang.Thread.sleep;
import java.security.NoSuchAlgorithmException;
import org.miktim.websocket.WebSocket;
import org.miktim.websocket.WsConnection;
import org.miktim.websocket.WsParameters;
import org.miktim.websocket.WsServer;
import org.miktim.websocket.WsStatus;

public class WsHandlerTest {

    static int testId = 0;
    static boolean testClient = true; // test client or server side connection
    static final int DELAY = 200; // timeout to close connection
    static String clientCalls = "";
    static String serverCalls = "";
    static String serverHandlerCalls = "";
    static WsConnection serverConn;
    static boolean testCompleted = false;
    static WebSocket webSocket;

    static void log(Object obj) {
        System.out.println(String.valueOf(obj));
    }

    static WsConnection ready(WsConnection conn) {
        /*        
        synchronized(conn) {
            while(conn.getStatus().code == WsStatus.IS_INACTIVE) {
                try {
                    conn.wait(conn.getParameters().getHandshakeSoTimeout());
                } catch (InterruptedException ex) {
                }
            }
        }
         */
        while (conn.getStatus().code == WsStatus.IS_INACTIVE) {
            try {
                sleep(200);
            } catch (InterruptedException ex) {
            }
        }

        return conn;
    }

    interface WsHandler extends WsServer.Handler, WsConnection.Handler {
    }

    public static class WsException extends RuntimeException {

        WsException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static WsServer startServer(int port, WsHandler handler) {
        try {
            return webSocket.Server(port, handler, new WsParameters())
                    .setHandler(handler).launch();
        } catch (Exception ex) {
            throw new WsException("Server start failed", ex);
        }
    }

    public static void main(String[] args) throws NoSuchAlgorithmException, Exception {
        WsHandler handler = new WsHandler() {
            @Override
            public void onOpen(WsConnection conn, String subProtocol) {
                String method = "onOpen";
                String calls = String.format("[%d] %s %s",
                        testId,
                        (conn.isClientSide() ? "Client" : "Server"),
                        method);
                if (conn.isClientSide()) {
                    clientCalls = calls;
                } else {
                    serverConn = conn;
                    serverCalls = calls;
                }
                String message = conn.isClientSide() ? "Hi, Server!" : "Hi, Client!";
                try {
                    conn.send(message);
                    conn.send(message);
                    conn.send(message);
                } catch (IOException ex) {
//? uncaught java.net.SocketException: Write failed (connection break)                    
                    ex.printStackTrace();
                    log(String.format("[%d] %s %s",
                            testId,
                            conn.isClientSide() ? "Client" : "Server",
                            ex.toString()));
                }
                if (testId == 1 && testClient == conn.isClientSide()) {
                    throw new NullPointerException(method);
                }
            }

            @Override
            public void onMessage(WsConnection conn, InputStream is, boolean isUTF8Text) {
                String method = "onMessage";
                if (conn.isClientSide()) {
                    clientCalls += (":" + method);
                } else {
                    serverCalls += (":" + method);
                }
                if (testClient == conn.isClientSide()) {
                    if (testId == 2) {
                        throw new NullPointerException(method);
                    }
                    if (testId == 3) try {
                        conn.getSocket().close();
                    } catch (IOException ignore) {
                    }
                    if (testId == 4) {
                        conn.close(1000, "Normal closure");
                    }
                }
            }

            @Override
            public void onError(WsConnection conn, Throwable e) {
                String method = "onError";
                if (conn.isClientSide()) {
                    clientCalls += (":" + method);
                } else {
                    serverCalls += (":" + method);
                }

                if (testClient == conn.isClientSide()) {
                    if (testId == 3) {
                        throw new NullPointerException(method);
                    }
                }
            }

            @Override
            public void onClose(WsConnection conn, WsStatus status) {
                String method = "onClose";
                String calls = String.format(":%s(%d)",
                        method,
                        conn.getStatus().code);
                if (conn.isClientSide()) {
                    clientCalls += calls;
                } else {
                    serverCalls += calls;
                }
                if (testClient == conn.isClientSide()) {
                    if (testId == 4) {
                        throw new NullPointerException(method);
                    }
                }
            }

// Server handler            
            @Override
            public void onStart(WsServer server) {
                serverHandlerCalls = "Server handler onStart";
                if (testId == 5) {
                    throw new NullPointerException("onStart");
                }
            }

            @Override
            public boolean onAccept(WsServer server, WsConnection conn) {
                String method = ":onAccept...";
                if (!serverHandlerCalls.contains(method)) {
                    serverHandlerCalls += method;
                }
                if (testId == 6) {
                    throw new NullPointerException("onAccept");
                }

                return true;
            }

            @Override
            public void onStop(WsServer server, Exception error) {
                serverHandlerCalls += ":onStop";
                if (testId == 7) {
                    throw new NullPointerException("onStop");
                }
            }
        };

        log("\r\nWsHandlerTest " + WebSocket.VERSION
                + "\r\nTest throws NullPointerException in event handlers");
        webSocket = new WebSocket();
        try {
            runTest(handler);
        } catch (Exception e) {
            sleep(DELAY);
            webSocket.closeAll();
            e.printStackTrace();
            testCompleted = true;
        }
        while (!testCompleted) {
            sleep(DELAY);
        }
        log("\r\nCompleted");
    }

    static void runTest(WsHandler handler) throws Exception {
        WsServer server = null;
        server = startServer(8080, handler);
        testClient = true;
        log("\r\nTesting the client side connection handler\r\n");
        testConnection(webSocket, handler);
        testClient = false;
        log("\r\nTesting the server side connection handler\r\n");
        testConnection(webSocket, handler);
        server.close();
        server.join();
        log(serverHandlerCalls);
        testServer(webSocket, server, handler);
        sleep(DELAY);
        webSocket.closeAll();
        testCompleted = true;
    } 
    
    static void testServer(WebSocket webSocket,WsServer server, WsHandler handler)
            throws InterruptedException {    
        log("\r\nTesting the server handler\r\n");
        for (testId = 5; testId < 8; testId++) {
            WsConnection conn = null;
            try {
                server = startServer(8080, handler);
                if (server.isAlive()) {
                    conn = webSocket.connect("ws://localhost:8080", handler, new WsParameters());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            sleep(DELAY);
            if (server.isAlive()) {
                server.close();
                server.join(DELAY);
            }
//            sleep(DELAY);
//            webSocket.closeAll();
            if (conn != null  && conn.isAlive()) {
                conn.close();
                conn.join(DELAY);
            }
            log(String.format("[%d] %s %d", testId,
                    serverHandlerCalls, conn == null ? -1 : conn.getStatus().code));
            String result = !server.isOpen() ? "Ok" : "Failed!";
            log(String.format("[%d] %s", testId, result));
        }
    }

    static void testConnection(WebSocket webSocket, WsHandler handler)
            throws Exception {
        for (testId = 1; testId < 5; testId++) {
            WsConnection conn = webSocket.connect("ws://localhost:8080", handler, new WsParameters());
            ready(conn).join(DELAY);
            log(clientCalls + " " + conn.getStatus().code);
            serverConn.join(DELAY);
            log(serverCalls + " " + serverConn.getStatus().code);
            String result = "Failed!";
            if (webSocket.listConnections().length == 0
                    && webSocket.listServers()[0]
                            .listConnections().length == 0) {
                result = "Ok";
            }
            log(String.format("[%d] %s", testId, result));
        }
    }
}
