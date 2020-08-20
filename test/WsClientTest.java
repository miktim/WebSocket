/*
 * WssServer, secure WsConnection test. MIT (c) 2020 miktim@mail.ru
 */
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
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

        String serverHost = "localhost";
        int port = 8000 + WssServer.DEFAULT_SERVER_PORT;
        String serverAddr = serverHost + ":" + port;

        WsHandler serverHandler = new WsHandler() {
            @Override
            public void onOpen(WsConnection con) {
                System.out.println("Server: handle OPEN: " + con.getPath()
                        + " Peer: " + con.getPeerHost());
                try {
                    con.send("Hello Client!");
                } catch (IOException e) {
                    System.out.println("Server: handle OPEN: " + con.getPath()
                            + " send error: " + e.toString());
//                    e.printStackTrace();
                }
            }

            @Override
            public void onClose(WsConnection con) {
                System.out.println("Server: handle CLOSE: " + con.getPath()
                        + " Closure status:" + con.getClosureStatus());
            }

            @Override
            public void onError(WsConnection con, Exception e) {
                System.out.println("Server: handle ERROR: "
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
                        System.out.println("Server: handle TEXT: " + s);
                    } else {
                        con.send(s);
                    }
                } catch (IOException e) {
                    System.out.println("Server: handle TEXT: " + con.getPath()
                            + " send error: " + e.toString());
                }
            }

            @Override
            public void onMessage(WsConnection con, byte[] b) {
                try {
                    con.send(b);
                } catch (IOException e) {
                    System.out.println("Server: handle BINARY: " + con.getPath()
                            + " send error: " + e.toString());
                }
            }
        };
        WsHandler clientHandler = new WsHandler() {
            @Override
            public void onOpen(WsConnection con) {
                System.out.println("Client: handle OPEN: " + con.getPath()
                        + " Peer: " + con.getPeerHost());
                try {
                    con.send("Hello Server!");
                } catch (IOException e) {
                    System.out.println("Client: handle OPEN: " + con.getPath()
                            + " send error: " + e.toString());
//                    e.printStackTrace();
                }
            }

            @Override
            public void onClose(WsConnection con) {
                System.out.println("Client: handle CLOSE: " + con.getPath()
                        + " Closure status:" + con.getClosureStatus());
            }

            @Override
            public void onError(WsConnection con, Exception e) {
                System.out.println("Client: handle ERROR: " + con.getPath()
                        + " " + e.toString()
                        + " Closure status:" + con.getClosureStatus());
//                e.printStackTrace();
            }

            @Override
            public void onMessage(WsConnection con, String s) {
                try {
                    if (s.length() < 128) {
                        con.send(s + s);
                        System.out.println("Client: handle TEXT: " + s);
                    } else {
                        byte[] sBytes = s.getBytes("utf-8");
                        if (sBytes.length < (con.getMaxMessageLength() / 2)) {
                            sBytes = (s + s).getBytes("utf-8");
                        }
//System.out.println("Server send:" + sBytes.length);
                        con.streamText(
                                new ByteArrayInputStream(sBytes));
                    }
                } catch (IOException e) {
                    System.out.println("Client: handle TEXT: " + con.getPath()
                            + " send error: " + e.toString());
                }
            }

            @Override
            public void onMessage(WsConnection con, byte[] b) {
                try {
                    con.send(b);
                } catch (IOException e) {
                    System.out.println("Client: handle BINARY: " + con.getPath()
                            + " send error: " + e.toString());
                }
            }
        };

        int maxMsgLen = 100000;
        final WsServer wsServer = new WssServer(port, serverHandler);
        wsServer.setConnectionSoTimeout(1000, true); // ping
        wsServer.setMaxMessageLength(maxMsgLen);
        /* Android       
        WsConnection.setKeyFile(new File(getCacheDir(),"testkeys"), "passphrase");
         */
// /* Desktop       
//        WsConnection.setKeyFile(new File(path, "localhost.jks"), "password"); // from java 1.8
        WsConnection.setKeyFile(new File(path, "testkeys"), "passphrase");
// */
        int stopTimeout = 10000;
        final Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                wsServer.stop();
                timer.cancel();
            }
        }, stopTimeout);
        System.out.println("\r\nWssServer, secure WsConnection test ("
                + WsConnection.VERSION + ")"
                + "\r\nClient connects to " + serverAddr
                + "\r\nServer maxConnections = 1"
                + "\r\nServer will be stopped after "
                + (stopTimeout / 1000) + " seconds"
                + "\r\n");
        wsServer.setMaxConnections(1);
        wsServer.start();
        try {
            WsConnection wsClient = new WsConnection(
                    "wss://" + serverAddr + "/test", clientHandler);
            wsClient.setMaxMessageLength(maxMsgLen);
            wsClient.open();
            (new WsConnection(
                    "wss://" + serverAddr + "/excess_connection", clientHandler)).open();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
