/*
 * WebSocket client/server test. MIT (c) 2020 miktim@mail.ru
 */
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Timer;
import java.util.TimerTask;
import org.samples.java.websocket.WsConnection;
import org.samples.java.websocket.WsHandler;
import org.samples.java.websocket.WsServer;
import org.samples.java.websocket.WssServer;

public class WsClientTest {

    public static void main(String[] args) throws Exception {
        String path = (new File(".")).getAbsolutePath();
        if (args.length > 0) {
            path = args[0];
        }
        WsHandler serverHandler = new WsHandler() {
            @Override
            public void onOpen(WsConnection con) {
                System.out.println("Server handle OPEN: " + con.getPath()
                        + " Peer: " + con.getPeerHost());
                try {
                    con.send("Hello Client!");
                } catch (IOException e) {
                    System.out.println("Server handle OPEN: " + con.getPath()
                            + " send error: " + e.toString());
//                    e.printStackTrace();
                }
            }

            @Override
            public void onClose(WsConnection con) {
                System.out.println("Server handle CLOSE: " + con.getPath()
                        + " Closure status:" + con.getClosureStatus());
            }

            @Override
            public void onError(WsConnection con, Exception e) {
                System.out.println("Server handle ERROR: "
                        + (con != null ? con.getPath() : null)
                        + " " + e.toString()
                        + " Closure status:"
                        + (con != null ? con.getClosureStatus() : null));
                e.printStackTrace();
            }

            @Override
            public void onMessage(WsConnection con, String s) {
                try {
                    if (s.length() < 128) {
                        con.send(s + s);
                        System.out.println("Server handle TEXT: " + s);
                    } else {
                        con.send(s);
                    }

                } catch (IOException e) {
                    System.out.println("Server handle TEXT: " + con.getPath()
                            + " send error: " + e.toString());
                }
            }

            @Override
            public void onMessage(WsConnection con, byte[] b) {
                try {
                    con.send(b);
                } catch (IOException e) {
                    System.out.println("Server handle BINARY: " + con.getPath()
                            + " send error: " + e.toString());
                }
            }
        };
        WsHandler clientHandler = new WsHandler() {
            @Override
            public void onOpen(WsConnection con) {
                System.out.println("Client handle OPEN: " + con.getPath()
                        + " Peer: " + con.getPeerHost());
                try {
                    con.send("Hello Server!");
                } catch (IOException e) {
                    System.out.println("Client handle OPEN: " + con.getPath()
                            + " send error: " + e.toString());
//                    e.printStackTrace();
                }
            }

            @Override
            public void onClose(WsConnection con) {
                System.out.println("Client handle CLOSE: " + con.getPath()
                        + " Closure status:" + con.getClosureStatus());
            }

            @Override
            public void onError(WsConnection con, Exception e) {
                System.out.println("Client handle ERROR: " + con.getPath()
                        + " " + e.toString()
                        + " Closure status:" + con.getClosureStatus());
//                e.printStackTrace();
            }

            @Override
            public void onMessage(WsConnection con, String s) {
                try {
                    if (s.length() < 128) {
                        con.send(s + s);
                        System.out.println("Client handle TEXT: " + s);
                    } else {
                        con.send(s);
                    }
                } catch (IOException e) {
                    System.out.println("Client handle TEXT: " + con.getPath()
                            + " send error: " + e.toString());
                }
            }

            @Override
            public void onMessage(WsConnection con, byte[] b) {
                try {
                    con.send(b);
                } catch (IOException e) {
                    System.out.println("Client handle BINARY: " + con.getPath()
                            + " send error: " + e.toString());
                }
            }
        };

        int port = 8000 + WssServer.DEFAULT_SERVER_PORT;
        String serverHost = "localhost";
        String serverAddr = serverHost + ":" + port;
        final WsServer wsServer = new WssServer(
                new InetSocketAddress(serverHost, port), serverHandler);
        wsServer.setConnectionSoTimeout(1000, true); // ping
        wsServer.setMaxMessageLength(100000);
        /* Android       
        WsConnection.setKeyFile(new File(getCacheDir(),"testkeys"), "passphrase");
         */
// /* Desktop       
//        WsConnection.setKeyFile(new File(path, "localhost.jks"), "password"); // from java 1.8
//        WsConnection.setKeyFile(new File(path,"samplecacerts"), "changeit"); // need client auth
        WsConnection.setKeyFile(new File(path, "testkeys"), "passphrase");
// */
        int stopTimeout = 30000;
        final Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                wsServer.stop();
                timer.cancel();
            }
        }, stopTimeout);
        System.out.println("\r\nTest WebSocket secure client\r\n"
                + "Server will be stopped after "
                + (stopTimeout / 1000) + " seconds");
        wsServer.start();
        wsServer.setMaxConnections(1);
        try {
            WsConnection wsClient = new WsConnection(
                    "wss://" + serverAddr + "/test", clientHandler);
            wsClient.open();
            (new WsConnection(
                    "wss://" + serverAddr + "/excess_connection", clientHandler)).open();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
