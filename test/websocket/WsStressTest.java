/*
 * WsStressTest. MIT (c) 2023 miktim@mail.ru
 */
import java.io.IOException;
import java.io.InputStream;
import static java.lang.Thread.sleep;
import java.util.Timer;
import java.util.TimerTask;
import org.miktim.websocket.WsConnection;
import org.miktim.websocket.WebSocket;
import org.miktim.websocket.WsServer;
import org.miktim.websocket.WsParameters;
import org.miktim.websocket.WsStatus;

public class WsStressTest {

//    static final int MAX_MESSAGE_LENGTH = 1000000; // 1MB
    static final int MAX_CLIENT_CONNECTIONS = 3; // allowed by server
    static final int TEST_SHUTDOWN_TIMEOUT = 5000; // 5 sec 
    static final int PORT = 8080;
    static final String ADDRESS = "ws://localhost:" + PORT;

    static void ws_log(String msg) {
        System.out.println(String.valueOf(msg));
    }

    static String makePath(WsConnection conn) {
        return conn.getPath()
                + (conn.getQuery() == null ? "" : "?" + conn.getQuery());
    }

    static void testResult(WsConnection conn, int code) {
        if (conn.getStatus().code == code)
            ws_log("OK");
        else
            ws_log("Failed!");
    }
    
    static void joinAll(WebSocket webSocket) throws InterruptedException {
        WsServer server = webSocket.listServers()[0];
        for(WsConnection conn : webSocket.listConnections())
            conn.join();
        for(WsConnection conn : server.listConnections())
            conn.join();
    }

    public static void main(String[] args) throws Exception {
        String path = ".";
        if (args.length > 0) {
            path = args[0];
        }

        final WebSocket webSocket = new WebSocket();

        WsConnection.EventHandler handler = new WsConnection.EventHandler() {

            @Override
            public void onOpen(WsConnection conn, String subp) {
                if (subp == null) {
                    conn.close(WsStatus.POLICY_VIOLATION, "Subprotocol required");
                    return;
                }
                try {
                    if (subp.equals("1") && conn.isClientSide()) {
// message too big                        
                        conn.send(new byte[conn.getParameters()
                                .getMaxMessageLength() + 1]);
                    } else if (subp.equals("2") && conn.isClientSide()) {
// message queue overflow
                        for (int i = 0; i < 100; i++) {
                            conn.send(new byte[conn.getParameters().getMaxMessageLength()]);
                            conn.send("Blah Blah Blah Blah Blah Blah Blah Blah Blah Blah ");
                        }
                    } else if (subp.equals("3")) {
// there is nothing to do, wait for a timeout                         
                    } else if (subp.equals("4")) {
// server interrupt
                        conn.send((conn.isClientSide()
                                ? "Hello, server!" : "Hello, client!"));
                    }
                } catch (IOException e) {
                    ws_log(String.format("[%s] %s send Error %s",
                            conn.getSubProtocol(),
                            (conn.isClientSide() ? "Client" : "Server side"),
                            e.toString()
                    ));
                }
            }

            @Override
            public void onClose(WsConnection conn, WsStatus status) {
                ws_log(String.format("[%s] %s onClose %s%s",
                        (conn.getSubProtocol() == null ? "0" : conn.getSubProtocol()),
                        (conn.isClientSide() ? "Client" : "Server side"),
                        status.toString(),
                        (status.error != null ? " Error " + status.error.toString() : "")
                ));
            }

            @Override
            public void onError(WsConnection conn, Throwable e) {
            }

            @Override
            public void onMessage(WsConnection conn, InputStream is, boolean isText) {
                String subp = conn.getSubProtocol();
                try {
                    if (subp.equals("2")) {
// message queue overflow                       
                        if (conn.isOpen()) {
                            conn.send(is, isText);
                        }
                    } else if (subp.equals("4")) {
// terminate server                    
                        if (conn.isOpen()) {
                            conn.send(is, isText);
                        }
                    }

                } catch (IOException e) {
                    ws_log(String.format("[%s] %s onMessage send error %s",
                            subp,
                            (conn.isClientSide() ? "Client" : "Server side"),
                            e.toString()));
                }

            }

        };

        WsServer.EventHandler serverHandler = new WsServer.EventHandler() {
            @Override
            public void onStart(WsServer server) {
                ws_log("Server started");
            }

            @Override
            public boolean onAccept(WsServer server, WsConnection conn) {
                if (server.listConnections().length < MAX_CLIENT_CONNECTIONS) {
                    return true;
                }
                ws_log("Server onAccept: Connection rejected.");
                return false;
            }

            @Override
            public void onStop(WsServer server, Exception e) {
                if (server.isInterrupted()) {
                    ws_log("Server interrupted. Active connections: "
                            + server.listConnections().length
                            + (e != null ? " Error " + e : ""));
                } else {
                    ws_log("Server closed");
                }
            }
        };

        final WsParameters wsp = new WsParameters() // client/server parameters
                .setSubProtocols("0,1,2,3,4,5,6,7,8,9".split(","))
                //               .setMaxMessageLength(2000)
                .setPayloadBufferLength(0);// min payload length

        final Timer timer = new Timer(true);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                ws_log("\r\nTime is over! Test completed.\r\n");
                webSocket.closeAll("Time is over!");
                timer.cancel();
            }
        }, TEST_SHUTDOWN_TIMEOUT);

        ws_log("\r\nWsStressTest "
                + WebSocket.VERSION
                + "\r\nClient try to connect to " + ADDRESS
                + "\r\nConnections allowed by server " + MAX_CLIENT_CONNECTIONS
                + "\r\nTest will be terminated after "
                + (TEST_SHUTDOWN_TIMEOUT / 1000) + " seconds"
                + "\r\n");

        final WsServer wsServer = webSocket.Server(PORT, handler, wsp)
                .setHandler(serverHandler).launch();

        ws_log("0. Connecting via TLS to a cleartext server (1002 expected):");
        WsConnection conn = webSocket.connect("wss://localhost:" + PORT, handler, wsp);
        conn.join();
        testResult(conn,1002);

        ws_log("\r\n0. Unsupported WebSocket subProtocol (1008 expected)");
        wsp.setSubProtocols(new String[]{"10"});
        conn = webSocket.connect(ADDRESS, handler, wsp);
        joinAll(webSocket);
        testResult(conn,1008);
        
        ws_log("\r\n1. Message too big (1009 expected)");
        wsp.setSubProtocols(new String[]{"1"});
        conn = webSocket.connect(ADDRESS, handler, wsp);
        joinAll(webSocket);
        testResult(conn,1009);

        ws_log("\r\n2. Message queue overflow (1008 expected):");
        wsp.setSubProtocols(new String[]{"2"})
                .setPayloadBufferLength(wsp.getMaxMessageLength()); // min
        conn = webSocket.connect(ADDRESS, handler, wsp);
        joinAll(webSocket);
        testResult(conn,1008);
       
        ws_log("\r\n3. Connection timeout (1001 expected):");
        wsp.setSubProtocols(new String[]{"3"})
                .setConnectionSoTimeout(400, false);
        conn = webSocket.connect(ADDRESS, handler, wsp);
        joinAll(webSocket);
        testResult(conn,1001);
        
        ws_log("\r\n4. " + MAX_CLIENT_CONNECTIONS + " connections are allowed:");
        wsp.setSubProtocols(new String[]{"4"});
        for (int i = 0; i < MAX_CLIENT_CONNECTIONS * 2; i++) {
            webSocket.connect(ADDRESS, handler, wsp);
        }
        sleep(200);
        if(webSocket.listConnections().length == MAX_CLIENT_CONNECTIONS)
            ws_log("OK");
        
        ws_log("\r\n5. Interrupt server:");
        wsServer.interrupt();

    }

}
